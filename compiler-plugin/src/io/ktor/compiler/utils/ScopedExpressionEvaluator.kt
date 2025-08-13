package io.ktor.compiler.utils

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirTransformer

/**
 * A scope for FIR expression evaluation that can hold custom variable values
 */
class FirEvaluationScope(
    val parent: FirEvaluationScope? = null
) {
    // Maps variable symbols to their constant values (as FirLiteralExpression)
    internal val variableValues = mutableMapOf<FirCallableSymbol<*>, FirExpression>()

    /**
     * Sets a value for a variable in this scope
     */
    fun setVariableValue(symbol: FirCallableSymbol<*>, value: FirExpression) {
        variableValues[symbol] = value
    }

    /**
     * Gets a value for a variable from this scope or any parent scope
     */
    fun getVariableValue(symbol: FirCallableSymbol<*>): FirExpression? {
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
class FirScopedExpressionEvaluator(
    private val session: FirSession,
    private var currentScope: FirEvaluationScope = FirEvaluationScope(null)
) {
    constructor(session: FirSession, scopeSetup: FirEvaluationScope.() -> Unit) : this(session, FirEvaluationScope().apply(scopeSetup))

    /**
     * Evaluates an expression within a new scope where custom values can be provided
     */
    fun evaluate(
        expression: FirExpression,
    ): FirEvaluatorResult? {
        // Create a new scope with the current scope as parent
        val newScope = FirEvaluationScope(currentScope)

        return inScope(newScope) {
            // Create a modified expression with variables replaced by their values
            val modifiedExpression = expression.transform<FirExpression, Unit>(VariableSubstitutionTransformer(), Unit)

            // Use the standard evaluator to evaluate the modified expression
            @OptIn(PrivateConstantEvaluatorAPI::class)
            FirExpressionEvaluator.evaluateExpression(modifiedExpression, session)
        }
    }

    /**
     * Execute a block in a specific scope
     */
    private inline fun <R> inScope(scope: FirEvaluationScope, block: () -> R): R {
        val previousScope = currentScope
        currentScope = scope
        try {
            return block()
        } finally {
            currentScope = previousScope
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
            val symbol = transformed.calleeReference.toResolvedCallableSymbol() ?: return transformed

            // If the symbol is in our scope, replace with its value
            if (currentScope.containsVariable(symbol)) {
                val value = currentScope.getVariableValue(symbol) ?: return transformed

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
}