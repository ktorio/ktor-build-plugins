package io.ktor.compiler.utils

import org.jetbrains.kotlin.contracts.description.LogicOperationKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.*
import org.jetbrains.kotlin.fir.expressions.impl.FirResolvedArgumentList
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.unwrapOr
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.resolve.constants.evaluate.CompileTimeType
import org.jetbrains.kotlin.resolve.constants.evaluate.evalBinaryOp
import org.jetbrains.kotlin.resolve.constants.evaluate.evalUnaryOp
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.util.OperatorNameConventions

/**
 * A scope for FIR expression evaluation that can hold custom variable values.
 *
 * This acts a little as a miniature Kotlin runtime and inherits many of the complexities. This will be removed in
 * future releases in lieu of simply transforming the route functions to include the appropriate information so that
 * it may be combined with the route paths at runtime.
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

        try {
            return inScope(newScope, newTypeScope) {
                // Create a modified expression with variables replaced by their values
                val visitor = ScopedExpressionEvaluationVisitor(session, newScope)

                // substitute variables
                expression.accept(visitor, null)
            }
        } catch (e: Throwable) {
            return null
        }
    }

    fun evaluateAsExpression(
        expression: FirExpression
    ): FirExpression? =
        (evaluate(expression) as? FirEvaluatorResult.Evaluated)?.result as? FirExpression


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
     * Resolves type variables within a ConeTypeProjection based on the current type scope
     */
    fun resolveTypeProjection(projection: ConeTypeProjection): ConeKotlinType? {
        // If it's already a type, make sure any type parameters in it are resolved
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

/**
 * This is mostly copied from the Kotlin repository.
 *
 * The intent of this class is to evaluate any expression while substituting variables provided in the scope.
 */
internal class ScopedExpressionEvaluationVisitor(
    val session: FirSession,
    val scope: FirEvaluationScope,
) : FirVisitor<FirEvaluatorResult, Nothing?>() {
    fun evaluate(expression: FirExpression?): FirEvaluatorResult {
        return expression?.accept(this, null) ?: FirEvaluatorResult.NotEvaluated
    }

    override fun visitElement(element: FirElement, data: Nothing?): FirEvaluatorResult {
        error("FIR element \"${element::class}\" is not supported in constant evaluation")
    }

    override fun visitLiteralExpression(literalExpression: FirLiteralExpression, data: Nothing?): FirEvaluatorResult {
        return literalExpression.wrap()
    }

    override fun visitThisReceiverExpression(thisReceiverExpression: FirThisReceiverExpression, data: Nothing?): FirEvaluatorResult {
        return thisReceiverExpression.wrap()
    }

    override fun visitCallableReferenceAccess(callableReferenceAccess: FirCallableReferenceAccess, data: Nothing?): FirEvaluatorResult {
        return callableReferenceAccess.calleeReference.resolved.wrap()
    }

    override fun visitResolvedNamedReference(resolvedNamedReference: FirResolvedNamedReference, data: Nothing?): FirEvaluatorResult {
        return resolvedNamedReference.wrap()
    }

    override fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier, data: Nothing?): FirEvaluatorResult {
        return resolvedQualifier.wrap()
    }

    override fun visitErrorResolvedQualifier(errorResolvedQualifier: FirErrorResolvedQualifier, data: Nothing?): FirEvaluatorResult {
        return errorResolvedQualifier.wrap()
    }

    override fun visitGetClassCall(getClassCall: FirGetClassCall, data: Nothing?): FirEvaluatorResult {
        return getClassCall.wrap()
    }

    override fun visitArgumentList(argumentList: FirArgumentList, data: Nothing?): FirEvaluatorResult {
        return when (argumentList) {
            is FirResolvedArgumentList -> buildResolvedArgumentList(
                argumentList.originalArgumentList,
                argumentList.mapping.mapKeysTo(LinkedHashMap()) {
                    evaluate(it.key).unwrapOr { return it } ?: return FirEvaluatorResult.NotEvaluated
                },
            )
            else -> buildArgumentList {
                source = argumentList.source
                arguments.addAll(argumentList.arguments.map {
                    evaluate(it).unwrapOr { return it } ?: return FirEvaluatorResult.NotEvaluated
                })
            }
        }.wrap()
    }

    override fun visitNamedArgumentExpression(namedArgumentExpression: FirNamedArgumentExpression, data: Nothing?): FirEvaluatorResult {
        return buildNamedArgumentExpression {
            source = namedArgumentExpression.source
            annotations.addAll(namedArgumentExpression.annotations)
            expression = evaluate(namedArgumentExpression.expression).unwrapOr { return it }
                ?: return FirEvaluatorResult.NotEvaluated
            isSpread = namedArgumentExpression.isSpread
            name = namedArgumentExpression.name
        }.wrap()
    }

    @OptIn(UnresolvedExpressionTypeAccess::class)
    override fun visitArrayLiteral(arrayLiteral: FirArrayLiteral, data: Nothing?): FirEvaluatorResult {
        return buildArrayLiteral {
            source = arrayLiteral.source
            coneTypeOrNull = arrayLiteral.coneTypeOrNull
            annotations.addAll(arrayLiteral.annotations)
            argumentList = visitArgumentList(arrayLiteral.argumentList, data).unwrapOr { return it }
                ?: return FirEvaluatorResult.NotEvaluated
        }.wrap()
    }

    @OptIn(UnresolvedExpressionTypeAccess::class)
    override fun visitVarargArgumentsExpression(varargArgumentsExpression: FirVarargArgumentsExpression, data: Nothing?): FirEvaluatorResult {
        return buildVarargArgumentsExpression {
            source = varargArgumentsExpression.source
            coneTypeOrNull = varargArgumentsExpression.coneTypeOrNull
            annotations.addAll(varargArgumentsExpression.annotations)
            arguments.addAll(varargArgumentsExpression.arguments.map {
                evaluate(it).unwrapOr { return it } ?: return FirEvaluatorResult.NotEvaluated
            })
            coneElementTypeOrNull = varargArgumentsExpression.coneElementTypeOrNull
        }.wrap()
    }

    override fun visitSpreadArgumentExpression(spreadArgumentExpression: FirSpreadArgumentExpression, data: Nothing?): FirEvaluatorResult {
        return buildSpreadArgumentExpression {
            source = spreadArgumentExpression.source
            annotations.addAll(spreadArgumentExpression.annotations)
            expression = evaluate(spreadArgumentExpression.expression).unwrapOr { return it }
                ?: return FirEvaluatorResult.NotEvaluated
        }.wrap()
    }

    @OptIn(SymbolInternals::class)
    override fun visitPropertyAccessExpression(propertyAccessExpression: FirPropertyAccessExpression, data: Nothing?): FirEvaluatorResult {
        val propertySymbol = propertyAccessExpression.calleeReference.toResolvedCallableSymbol()
            ?: return FirEvaluatorResult.NotEvaluated

        if (propertySymbol.wasVisited()) {
            return FirEvaluatorResult.RecursionInInitializer
        }

        fun evaluateWithSourceCopy(initializer: FirExpression?): FirEvaluatorResult = propertySymbol.visit {
            // We need a copy here to copy a source of the original expression
            if (initializer is FirLiteralExpression) {
                initializer.copy(propertyAccessExpression).wrap()
            } else {
                val evaluatedResult = evaluate(initializer)
                if (evaluatedResult !is FirEvaluatorResult.Evaluated || evaluatedResult.result !is FirLiteralExpression) {
                    return evaluatedResult
                }
                val unwrappedLiteralResult = evaluatedResult.result as FirLiteralExpression
                unwrappedLiteralResult.copy(propertyAccessExpression).wrap()
            }
        }

        if (scope.containsVariable(propertySymbol)) {
            val value = scope.getVariableValue(propertySymbol)
            if (value is FirLiteralExpression) {
                return buildLiteralExpression(
                    source = propertyAccessExpression.source,
                    kind = value.kind,
                    value = value.value,
                    annotations = propertyAccessExpression.annotations.toMutableList(),
                    setType = false
                ).apply {
                    replaceConeTypeOrNull(propertyAccessExpression.resolvedType)
                }.wrap()
            }
        }

        return when (propertySymbol) {
            is FirPropertySymbol -> {
                when {
                    propertySymbol.callableId.isStringLength || propertySymbol.callableId.isCharCode -> {
                        evaluate(propertyAccessExpression.explicitReceiver).let { receiver ->
                            val unaryArg = receiver.unwrapOr<FirExpression> { return it } ?: return FirEvaluatorResult.NotEvaluated
                            evaluateUnary(unaryArg, propertySymbol.callableId)
                                .adjustTypeAndConvertToLiteral(propertyAccessExpression)
                        }
                    }
                    else -> evaluateWithSourceCopy(propertySymbol.fir.initializer)
                }
            }
            is FirFieldSymbol -> evaluateWithSourceCopy(propertySymbol.fir.initializer)
            is FirEnumEntrySymbol -> propertyAccessExpression.wrap()
            else -> error("FIR symbol \"${propertySymbol::class}\" is not supported in constant evaluation")
        }
    }

    override fun visitFunctionCall(functionCall: FirFunctionCall, data: Nothing?): FirEvaluatorResult {
        val calleeReference = functionCall.calleeReference
        if (calleeReference !is FirResolvedNamedReference) return FirEvaluatorResult.NotEvaluated

        return when (val symbol = calleeReference.resolvedSymbol) {
            is FirNamedFunctionSymbol -> visitNamedFunction(functionCall, symbol)
            is FirConstructorSymbol -> visitConstructorCall(functionCall)
            else -> FirEvaluatorResult.NotEvaluated
        }
    }

    private fun visitNamedFunction(functionCall: FirFunctionCall, symbol: FirNamedFunctionSymbol): FirEvaluatorResult {
        val receivers = listOfNotNull(functionCall.dispatchReceiver, functionCall.extensionReceiver)
        val evaluatedArgs = receivers.plus(functionCall.arguments).map {
            evaluate(it).unwrapOr<FirLiteralExpression> { return it } ?: return FirEvaluatorResult.NotEvaluated
        }

        val opr1 = evaluatedArgs.getOrNull(0) ?: return FirEvaluatorResult.NotEvaluated
        evaluateUnary(opr1, symbol.callableId)
            ?.adjustTypeAndConvertToLiteral(functionCall)
            ?.let { return it }

        val opr2 = evaluatedArgs.getOrNull(1) ?: return FirEvaluatorResult.NotEvaluated
        evaluateBinary(opr1, symbol.callableId, opr2)
            ?.adjustTypeAndConvertToLiteral(functionCall)
            ?.let { return it }

        return FirEvaluatorResult.NotEvaluated
    }

    @OptIn(UnresolvedExpressionTypeAccess::class)
    private fun visitConstructorCall(constructorCall: FirFunctionCall): FirEvaluatorResult {
        val type = constructorCall.resolvedType.fullyExpandedType(session).lowerBoundIfFlexible()
        when {
            type.toRegularClassSymbol(session)?.classKind == ClassKind.ANNOTATION_CLASS -> {
                val evaluatedArgs = constructorCall.argumentList.accept(this, null)
                    .unwrapOr<FirResolvedArgumentList> { return it } ?: return FirEvaluatorResult.NotEvaluated
                return buildFunctionCall {
                    coneTypeOrNull = constructorCall.coneTypeOrNull
                    annotations.addAll(constructorCall.annotations)
                    typeArguments.addAll(constructorCall.typeArguments)
                    source = constructorCall.source
                    nonFatalDiagnostics.addAll(constructorCall.nonFatalDiagnostics)
                    argumentList = evaluatedArgs
                    calleeReference = constructorCall.calleeReference
                    origin = constructorCall.origin
                }.wrap()
            }
            type.isUnsignedType -> {
                val argument = evaluate(constructorCall.argument)
                    .unwrapOr<FirLiteralExpression> { return it }?.value ?: return FirEvaluatorResult.NotEvaluated
                return argument.adjustTypeAndConvertToLiteral(constructorCall)
            }
            else -> return FirEvaluatorResult.NotEvaluated
        }
    }

    override fun visitIntegerLiteralOperatorCall(
        integerLiteralOperatorCall: FirIntegerLiteralOperatorCall,
        data: Nothing?
    ): FirEvaluatorResult {
        return visitFunctionCall(integerLiteralOperatorCall, data)
    }

    override fun visitComparisonExpression(comparisonExpression: FirComparisonExpression, data: Nothing?): FirEvaluatorResult {
        return visitFunctionCall(comparisonExpression.compareToCall, data).let {
            val intResult = it.unwrapOr<FirLiteralExpression> { return it }?.value as? Int ?: return FirEvaluatorResult.NotEvaluated
            val compareToResult = when (comparisonExpression.operation) {
                FirOperation.LT -> intResult < 0
                FirOperation.LT_EQ -> intResult <= 0
                FirOperation.GT -> intResult > 0
                FirOperation.GT_EQ -> intResult >= 0
                else -> error("Unsupported comparison operation type \"${comparisonExpression.operation.name}\"")
            }
            compareToResult.adjustTypeAndConvertToLiteral(comparisonExpression)
        }
    }

    override fun visitEqualityOperatorCall(equalityOperatorCall: FirEqualityOperatorCall, data: Nothing?): FirEvaluatorResult {
        val evaluatedArgs = equalityOperatorCall.arguments.map {
            evaluate(it).unwrapOr<FirLiteralExpression> { return it } ?: return FirEvaluatorResult.NotEvaluated
        }
        if (evaluatedArgs.size != 2) return FirEvaluatorResult.NotEvaluated

        val result = when (equalityOperatorCall.operation) {
            FirOperation.EQ -> evaluatedArgs[0].value == evaluatedArgs[1].value
            FirOperation.NOT_EQ -> evaluatedArgs[0].value != evaluatedArgs[1].value
            else -> error("Operation \"${equalityOperatorCall.operation}\" is not supported in compile time evaluation")
        }

        return result.toConstExpression(ConstantValueKind.Boolean, equalityOperatorCall).wrap()
    }

    override fun visitBooleanOperatorExpression(booleanOperatorExpression: FirBooleanOperatorExpression, data: Nothing?): FirEvaluatorResult {
        val left = evaluate(booleanOperatorExpression.leftOperand)
        val right = evaluate(booleanOperatorExpression.rightOperand)

        val leftBoolean = left.unwrapOr<FirLiteralExpression> { return it }?.value as? Boolean ?: return FirEvaluatorResult.NotEvaluated
        val rightBoolean = right.unwrapOr<FirLiteralExpression> { return it }?.value as? Boolean ?: return FirEvaluatorResult.NotEvaluated
        val result = when (booleanOperatorExpression.kind) {
            LogicOperationKind.AND -> leftBoolean && rightBoolean
            LogicOperationKind.OR -> leftBoolean || rightBoolean
        }

        return result.toConstExpression(ConstantValueKind.Boolean, booleanOperatorExpression).wrap()
    }

    override fun visitStringConcatenationCall(stringConcatenationCall: FirStringConcatenationCall, data: Nothing?): FirEvaluatorResult {
        val strings = stringConcatenationCall.argumentList.arguments.map {
            evaluate(it).unwrapOr<FirLiteralExpression> { return it } ?: return FirEvaluatorResult.NotEvaluated
        }
        val result = strings.joinToString(separator = "") { it.value.toString() }
        return result.toConstExpression(ConstantValueKind.String, stringConcatenationCall).wrap()
    }

    override fun visitTypeOperatorCall(typeOperatorCall: FirTypeOperatorCall, data: Nothing?): FirEvaluatorResult {
        if (typeOperatorCall.operation != FirOperation.AS) return FirEvaluatorResult.NotEvaluated
        val result = evaluate(typeOperatorCall.argument).unwrapOr<FirLiteralExpression> { return it } ?: return FirEvaluatorResult.NotEvaluated
        if (result.resolvedType.isSubtypeOf(typeOperatorCall.resolvedType, session)) {
            return result.wrap()
        }
        return typeOperatorCall.wrap()
    }

    override fun visitEnumEntryDeserializedAccessExpression(
        enumEntryDeserializedAccessExpression: FirEnumEntryDeserializedAccessExpression,
        data: Nothing?,
    ): FirEvaluatorResult {
        return enumEntryDeserializedAccessExpression.wrap()
    }

    override fun visitClassReferenceExpression(
        classReferenceExpression: FirClassReferenceExpression,
        data: Nothing?,
    ): FirEvaluatorResult {
        return classReferenceExpression.wrap()
    }

    override fun visitAnnotationCall(annotationCall: FirAnnotationCall, data: Nothing?): FirEvaluatorResult {
        return visitAnnotation(annotationCall, data)
    }

    override fun visitAnnotation(annotation: FirAnnotation, data: Nothing?): FirEvaluatorResult {
        val mapping = annotation.argumentMapping.mapping
        if (mapping.isEmpty()) return annotation.wrap()
        val evaluatedMapping = mutableMapOf<Name, FirExpression>()
        for ((name, expression) in mapping) {
            when (val evaluatedExpression = evaluate(expression)) {
                is FirEvaluatorResult.Evaluated -> evaluatedMapping[name] = evaluatedExpression.result as FirExpression
                else -> return evaluatedExpression
            }
        }
        return buildAnnotationCopy(annotation) {
            argumentMapping = buildAnnotationArgumentMapping {
                this.mapping.putAll(evaluatedMapping)
            }
        }.wrap()
    }
}

private val visitedCallables: ThreadLocal<HashSet<FirCallableSymbol<*>>> = ThreadLocal.withInitial(::hashSetOf)

private fun FirCallableSymbol<*>.wasVisited(): Boolean = this in visitedCallables.get()

private inline fun <T> FirCallableSymbol<*>.visit(block: () -> T): T {
    val visited = visitedCallables.get()
    visited.add(this)
    return try {
        block()
    } finally {
        visited.remove(this)
        if (visited.isEmpty()) {
            // to avoid keeping large empty collections in memory
            visitedCallables.remove()
        }
    }
}

private fun Any?.toConstExpression(
    kind: ConstantValueKind,
    originalExpression: FirExpression
): FirLiteralExpression {
    return buildLiteralExpression(
        originalExpression.source,
        kind,
        this,
        originalExpression.annotations.takeIf { it.isNotEmpty() }?.toMutableList(),
        setType = false,
    ).apply { replaceConeTypeOrNull(originalExpression.resolvedType) }
}

private fun FirElement?.wrap(): FirEvaluatorResult {
    return if (this != null) FirEvaluatorResult.Evaluated(this) else FirEvaluatorResult.NotEvaluated
}

private fun FirLiteralExpression.copy(originalExpression: FirExpression): FirLiteralExpression {
    return this.value.toConstExpression(this.kind, originalExpression)
}

private fun ConstantValueKind.toCompileTimeType(): CompileTimeType {
    return when (this) {
        ConstantValueKind.Byte -> CompileTimeType.BYTE
        ConstantValueKind.Short -> CompileTimeType.SHORT
        ConstantValueKind.Int -> CompileTimeType.INT
        ConstantValueKind.Long -> CompileTimeType.LONG
        ConstantValueKind.Double -> CompileTimeType.DOUBLE
        ConstantValueKind.Float -> CompileTimeType.FLOAT
        ConstantValueKind.Char -> CompileTimeType.CHAR
        ConstantValueKind.Boolean -> CompileTimeType.BOOLEAN
        ConstantValueKind.String -> CompileTimeType.STRING

        else -> CompileTimeType.ANY
    }
}

// Unary operators
private fun evaluateUnary(arg: FirExpression, callableId: CallableId): Any? {
    if (arg !is FirLiteralExpression || arg.value == null) return null

    val opr = arg.kind.convertToGivenKind(arg.value) ?: return null
    return evalUnaryOp(
        callableId.callableName.asString(),
        arg.kind.toCompileTimeType(),
        opr
    )
}

// Binary operators
private fun evaluateBinary(
    arg1: FirExpression,
    callableId: CallableId,
    arg2: FirExpression
): Any? {
    if (arg1 !is FirLiteralExpression || arg1.value == null) return null
    if (arg2 !is FirLiteralExpression || arg2.value == null) return null
    // NB: some utils accept very general types, and due to the way operation map works, we should up-cast rhs type.
    val rightType = when {
        callableId.isStringEquals -> CompileTimeType.ANY
        callableId.isStringPlus -> CompileTimeType.ANY
        else -> arg2.kind.toCompileTimeType()
    }

    val opr1 = arg1.kind.convertToGivenKind(arg1.value) ?: return null
    val opr2 = arg2.kind.convertToGivenKind(arg2.value) ?: return null

    val functionName = callableId.callableName.asString()

    // Check for division by zero
    if (functionName == "div" || functionName == "rem") {
        if (rightType != CompileTimeType.FLOAT && rightType != CompileTimeType.DOUBLE && (opr2 as? Number)?.toInt() == 0) {
            // If expression is division by zero, then return the original expression as a result. We will handle on later steps.
            return FirEvaluatorResult.DivisionByZero
        }
    }

    return evalBinaryOp(
        functionName,
        arg1.kind.toCompileTimeType(),
        opr1,
        rightType,
        opr2
    )
}

private fun Any?.adjustTypeAndConvertToLiteral(original: FirExpression): FirEvaluatorResult {
    if (this == null) return FirEvaluatorResult.NotEvaluated
    if (this is FirEvaluatorResult) return this
    val expectedType = original.resolvedType
    val expectedKind = expectedType.toConstantValueKind() ?: return FirEvaluatorResult.NotEvaluated
    val typeAdjustedValue = expectedKind.convertToGivenKind(this) ?: return FirEvaluatorResult.NotEvaluated
    return typeAdjustedValue.toConstExpression(expectedKind, original).wrap()
}

private val CallableId.isStringLength: Boolean
    get() = classId == StandardClassIds.String && callableName.identifierOrNullIfSpecial == "length"

private val CallableId.isStringEquals: Boolean
    get() = classId == StandardClassIds.String && callableName == OperatorNameConventions.EQUALS

private val CallableId.isStringPlus: Boolean
    get() = classId == StandardClassIds.String && callableName == OperatorNameConventions.PLUS

private val CallableId.isCharCode: Boolean
    get() = packageName == StandardClassIds.BASE_KOTLIN_PACKAGE && classId == null && callableName.identifierOrNullIfSpecial == "code"

private fun ConeKotlinType.toConstantValueKind(): ConstantValueKind? =
    when (this) {
        is ConeErrorType -> null
        is ConeLookupTagBasedType -> (lookupTag as? ConeClassLikeLookupTag)?.classId?.toConstantValueKind()
        is ConeFlexibleType -> upperBound.toConstantValueKind()
        is ConeCapturedType -> constructor.supertypes!!.first().toConstantValueKind()
        is ConeDefinitelyNotNullType -> original.toConstantValueKind()
        is ConeIntersectionType -> intersectedTypes.first().toConstantValueKind()
        is ConeStubType, is ConeIntegerLiteralType, is ConeTypeVariableType -> null
    }

private fun ClassId.toConstantValueKind(): ConstantValueKind? =
    when (this) {
        StandardClassIds.Byte -> ConstantValueKind.Byte
        StandardClassIds.Double -> ConstantValueKind.Double
        StandardClassIds.Float -> ConstantValueKind.Float
        StandardClassIds.Int -> ConstantValueKind.Int
        StandardClassIds.Long -> ConstantValueKind.Long
        StandardClassIds.Short -> ConstantValueKind.Short

        StandardClassIds.Char -> ConstantValueKind.Char
        StandardClassIds.String -> ConstantValueKind.String
        StandardClassIds.Boolean -> ConstantValueKind.Boolean

        StandardClassIds.UByte -> ConstantValueKind.UnsignedByte
        StandardClassIds.UShort -> ConstantValueKind.UnsignedShort
        StandardClassIds.UInt -> ConstantValueKind.UnsignedInt
        StandardClassIds.ULong -> ConstantValueKind.UnsignedLong

        else -> null
    }

private fun ConstantValueKind.convertToGivenKind(value: Any?): Any? {
    if (value == null) {
        return null
    }
    return when (this) {
        ConstantValueKind.Boolean -> value as Boolean
        ConstantValueKind.Char -> value as Char
        ConstantValueKind.String -> value as String
        ConstantValueKind.Byte -> (value as Number).toByte()
        ConstantValueKind.Double -> (value as Number).toDouble()
        ConstantValueKind.Float -> (value as Number).toFloat()
        ConstantValueKind.Int -> (value as Number).toInt()
        ConstantValueKind.Long -> (value as Number).toLong()
        ConstantValueKind.Short -> (value as Number).toShort()
        ConstantValueKind.UnsignedByte -> {
            if (value is UByte) value
            else (value as Number).toLong().toUByte()
        }
        ConstantValueKind.UnsignedShort -> {
            if (value is UShort) value
            else (value as Number).toLong().toUShort()
        }
        ConstantValueKind.UnsignedInt -> {
            if (value is UInt) value
            else (value as Number).toLong().toUInt()
        }
        ConstantValueKind.UnsignedLong -> {
            if (value is ULong) value
            else (value as Number).toLong().toULong()
        }
        ConstantValueKind.UnsignedIntegerLiteral -> {
            when (value) {
                is UInt -> value.toULong()
                is ULong -> value
                else -> (value as Number).toLong().toULong()
            }
        }
        else -> null
    }
}