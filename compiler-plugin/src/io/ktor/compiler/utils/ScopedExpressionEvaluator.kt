package io.ktor.compiler.utils

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.result
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.PrivateForInline

/**
 * A scope for FIR expression evaluation that can hold custom variable values.
 */
class FirEvaluationScope(
    val parent: FirEvaluationScope? = null
) {
    // Maps variable symbols to their constant values (as FirLiteralExpression)
    internal val variableValues = mutableMapOf<FirBasedSymbol<*>, FirExpression>()

    /**
     * Sets a value for a variable in this scope
     */
    fun setVariableValue(symbol: FirBasedSymbol<*>, value: FirExpression) {
        variableValues[symbol] = value
    }

    /**
     * Gets a value for a variable from this scope or any parent scope
     */
    fun getVariableValue(symbol: FirBasedSymbol<*>): FirExpression? {
        return variableValues[symbol] ?: parent?.getVariableValue(symbol)
    }

    /**
     * Checks if a variable is defined in this scope or any parent scope
     */
    fun containsVariable(symbol: FirCallableSymbol<*>): Boolean {
        return variableValues.containsKey(symbol) || (parent?.containsVariable(symbol) ?: false)
    }
}

/**
 * An evaluator that can evaluate FIR expressions with custom variable values
 */
class FirScopedEvaluator(val session: FirSession) {
    // Map variable symbols to their values
    var variables: FirEvaluationScope = FirEvaluationScope()

    // Map type parameter symbols to their concrete types
    var types: TypeSubstitutionContext = TypeSubstitutionContext()

    // ScopeSession for creating type scopes
    val scopeSession = ScopeSession()

    /**
     * Context for tracking type parameter substitutions
     */
    inner class TypeSubstitutionContext(val parent: TypeSubstitutionContext? = null) {
        // Maps type parameter symbols to their substituted concrete types
        private val typeSubstitutions = mutableMapOf<Name, ConeKotlinType>()

        /**
         * Sets a substitution for a type parameter
         */
        fun setTypeSubstitution(typeParam: FirTypeParameterSymbol, type: ConeKotlinType) {
            typeSubstitutions[typeParam.name] = when(type) {
                is ConeTypeParameterType -> getTypeSubstitution(type.lookupTag.typeParameterSymbol) ?: type
                else -> type
            }
        }

        /**
         * Gets the substituted type for a type parameter
         */
        fun getTypeSubstitution(typeParam: FirTypeParameterSymbol): ConeKotlinType? {
            return typeSubstitutions[typeParam.name] ?: parent?.getTypeSubstitution(typeParam)
        }

        /**
         * Gets all type parameter symbols defined in this scope
         */
        fun getDefinedTypeParameters(): Set<Name> {
            return typeSubstitutions.keys
        }

        /**
         * Recursively collects all type parameters defined in this scope and parent scopes
         */
        private fun collectTypeParametersTo(symbols: MutableSet<Name>) {
            getDefinedTypeParameters().forEach { symbols.add(it) }
            parent?.collectTypeParametersTo(symbols)
        }
    }

    /**
     * Evaluates an expression within a new scope where custom values can be provided
     */
    fun evaluate(
        expression: FirExpression,
    ): FirEvaluatorResult? {
        // Create new scopes with the current scopes as parents
        val newScope = FirEvaluationScope(variables)
        val newTypeScope = TypeSubstitutionContext(types)

        return inScope(newScope, newTypeScope) {
            // Create a modified expression with variables replaced by their values
            val modifiedExpression = expression.transform<FirExpression, Unit>(VariableSubstitutionTransformer(), Unit)

            // Use the standard evaluator to evaluate the modified expression
            @OptIn(PrivateConstantEvaluatorAPI::class)
            FirExpressionEvaluator.evaluateExpression(modifiedExpression, session)
        }
    }

    @OptIn(PrivateForInline::class)
    fun evaluateAsExpression(
        expression: FirExpression
    ): FirExpression? =
        evaluate(expression)?.result as? FirExpression


    /**
     * Execute a block in specific scopes
     */
    private inline fun <R> inScope(
        scope: FirEvaluationScope,
        typeScope: TypeSubstitutionContext,
        block: () -> R
    ): R {
        val previousScope = this@FirScopedEvaluator.variables
        val previousTypeScope = types

        this@FirScopedEvaluator.variables = scope
        types = typeScope

        try {
            return block()
        } finally {
            this@FirScopedEvaluator.variables = previousScope
            types = previousTypeScope
        }
    }

    /**
     * A transformer that replaces variable references with their values from the current scope
     */
    private inner class VariableSubstitutionTransformer : FirTransformer<Unit>() {
        override fun <E : FirElement> transformElement(element: E, data: Unit): E {
            @Suppress("UNCHECKED_CAST")
            return element.transformChildren(this, data) as E
        }

        override fun transformPropertyAccessExpression(
            propertyAccessExpression: FirPropertyAccessExpression,
            data: Unit
        ): FirStatement {
            // Transform children first (like receivers)
            val transformed = super.transformPropertyAccessExpression(propertyAccessExpression, data) as FirPropertyAccessExpression

            // Get the symbol for this property access
            val calleeReference = transformed.calleeReference as? FirResolvedNamedReference ?: return transformed
            val symbol = calleeReference.resolvedSymbol as? FirCallableSymbol<*> ?: return transformed

            // If the symbol is in our scope, replace with its value
            if (variables.containsVariable(symbol)) {
                val value = variables.getVariableValue(symbol) ?: return transformed

                // Create a copy of the value that preserves type information from the original expression
                return when (value) {
                    is FirLiteralExpression -> {
                        buildLiteralExpression(
                            source = transformed.source,
                            kind = value.kind,
                            value = value.value,
                            annotations = transformed.annotations.toMutableList(),
                            setType = false
                        ).apply {
                            replaceConeTypeOrNull(transformed.resolvedType)
                        }
                    }
                    else -> {
                        // For non-literal expressions, we might need to clone them
                        // while preserving type information from the original
                        value.transformChildren(this, data)
                    }
                } as FirStatement
            }

            return transformed
        }
    }

    /**
     * Resolves type variables within a ConeTypeProjection based on the current type scope
     */
    fun resolveTypeProjection(projection: ConeTypeProjection): ConeKotlinType? {
        // If it's already a type, just make sure any type parameters in it are resolved
        if (projection is ConeKotlinType) {
            return resolveType(projection)
        }

        // Handle different kinds of projections
        return when (projection) {
            is ConeKotlinTypeProjection -> {
                val resolvedType = resolveType(projection.type)

                // Apply projection kind if needed
                when (projection.kind) {
                    ProjectionKind.INVARIANT -> resolvedType
                    ProjectionKind.IN -> resolvedType?.let { ConeKotlinTypeProjectionIn(it).type }
                    ProjectionKind.OUT -> resolvedType?.let { ConeKotlinTypeProjectionOut(it).type }
                    ProjectionKind.STAR -> null // Star projection can't be resolved to a concrete type
                }
            }
            is ConeStarProjection -> null // Star projection can't be resolved to a concrete type
            else -> null // Unknown projection type
        }
    }

    /**
     * Resolves type parameters within a type using the current type scope
     */
    fun resolveType(type: ConeKotlinType): ConeKotlinType? {
        // Handle different kinds of types
        return when (type) {
            // Handle type parameters by substituting with their resolved types
            is ConeTypeParameterType -> {
                val symbol = type.lookupTag.typeParameterSymbol
                types.getTypeSubstitution(symbol)
            }

            // For class types, resolve each type argument
            is ConeClassLikeType -> {
                val resolvedTypeArgs = type.typeArguments.map { arg ->
                    when (arg) {
                        is ConeKotlinTypeProjection -> {
                            val resolvedArgType = resolveType(arg.type)
                            resolvedArgType?.let {
                                when (arg.kind) {
                                    ProjectionKind.INVARIANT -> it
                                    ProjectionKind.IN -> ConeKotlinTypeProjectionIn(it)
                                    ProjectionKind.OUT -> ConeKotlinTypeProjectionOut(it)
                                    ProjectionKind.STAR -> ConeStarProjection
                                }
                            } ?: arg
                        }
                        is ConeStarProjection -> arg
                        else -> arg
                    }
                }.toTypedArray()

                // Create a new type with resolved arguments
                type.withArguments(resolvedTypeArgs)
            }

            // For flexible types, resolve both bounds
            is ConeFlexibleType -> {
                val resolvedLower = resolveType(type.lowerBound) as? ConeRigidType
                val resolvedUpper = resolveType(type.upperBound) as? ConeRigidType

                if (resolvedLower != null && resolvedUpper != null) {
                    ConeFlexibleType(resolvedLower, resolvedUpper, isTrivial = type.isTrivial)
                } else {
                    type
                }
            }

            // Handle intersection types
            is ConeIntersectionType -> {
                val resolvedTypes = type.intersectedTypes.mapNotNull {
                    resolveType(it)
                }
                if (resolvedTypes.isNotEmpty()) {
                    ConeIntersectionType(resolvedTypes)
                } else {
                    type
                }
            }

            is ConeDefinitelyNotNullType -> resolveType(type.original)

            // Other types can be returned as is
            else -> type
        }
    }
}