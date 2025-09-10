package io.ktor.compiler.utils

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.*

/**
 * Creates a scoped evaluator for evaluating expressions through a call stack
 *
 * @param callStack List of function calls in order of execution (from caller to callee)
 * @return A scoped evaluator that can evaluate expressions with the call stack context
 */
context(session: FirSession)
fun createCallStackEvaluator(callStack: List<FirFunctionCall>): FirScopedEvaluator {

    val evaluator = FirScopedEvaluator(session)

    // Process the call stack to build the parameter value and type substitution chains
    processCallStack(callStack, evaluator, session)

    return evaluator
}

/**
 * Processes the entire call stack to build parameter values and type substitutions at each level
 */
private fun processCallStack(
    callStack: List<FirFunctionCall>,
    evaluator: FirScopedEvaluator,
    session: FirSession
) {
    if (callStack.isEmpty()) return

    // Process each function call in sequence
    for (i in 0 until callStack.size) {
        val call = callStack[i]

        // Get the called function
        val calleeReference = call.calleeReference as? FirResolvedNamedReference
            ?: continue

        val functionSymbol = calleeReference.resolvedSymbol as? FirFunctionSymbol<*>
            ?: continue

        // Process this function call
        processFunction(call, functionSymbol, evaluator, session)
    }
}

/**
 * Processes a single function call to map arguments to parameters and infer type parameters
 */
private fun processFunction(
    call: FirFunctionCall,
    function: FirFunctionSymbol<*>,
    evaluator: FirScopedEvaluator,
    session: FirSession
) {
    // Handle parameter/argument mapping
    mapArgumentsToParameters(call, function, evaluator, session)
    // Handle type parameter inference
    inferTypeParameters(call, function, evaluator, session)
}

/**
 * Maps arguments from a function call to its parameters
 */
private fun mapArgumentsToParameters(
    call: FirFunctionCall,
    function: FirFunctionSymbol<*>,
    evaluator: FirScopedEvaluator,
    session: FirSession
) {
    val arguments = call.argumentList.arguments
    val parameters = function.valueParameterSymbols

    // Handle positional arguments
    var paramIndex = 0
    for (i in arguments.indices) {
        if (paramIndex >= parameters.size) break
        val argument = arguments[i]
        // If it's a named argument, skip it here (will be handled below)
        if (argument is FirNamedArgumentExpression) continue
        val parameter = parameters[paramIndex++]
        // Evaluate the argument
        val evaluatedArg = evaluator.evaluateAsExpression(argument)
        // Store the evaluated value for this parameter
        evaluatedArg?.let {
            evaluator.variables.setVariableValue(parameter, it)
        }
    }

    // Process named arguments
    arguments.forEach { arg ->
        if (arg is FirNamedArgumentExpression) {
            val paramName = arg.name
            val parameter = parameters.find { it.name == paramName } ?: return@forEach

            val evaluatedArg = evaluator.evaluateAsExpression(arg.expression)
            evaluatedArg?.let {
                evaluator.variables.setVariableValue(parameter, it)
            }
        }
    }

    // Process receivers if present
    function.receiverParameterSymbol?.let { receiverParam ->
        call.extensionReceiver?.let { receiver ->
            val evaluatedReceiver = evaluator.evaluateAsExpression(receiver)
            evaluatedReceiver?.let {
                evaluator.variables.setVariableValue(receiverParam, it)
            }
        }
    }
}

/**
 * Infers type parameters from a function call
 */
private fun inferTypeParameters(
    call: FirFunctionCall,
    function: FirFunctionSymbol<*>,
    evaluator: FirScopedEvaluator,
    session: FirSession
) {
    // Get the type parameters from the function
    val typeParameters = function.typeParameterSymbols

    if (typeParameters.isEmpty()) return

    // Process explicit type arguments
    val typeArguments = call.typeArguments

    // Map explicit type arguments to type parameters
    for (i in typeArguments.indices) {
        if (i >= typeParameters.size) break
        val typeArg = typeArguments[i]
        val typeParamSymbol = typeParameters[i]
        if (typeArg is FirTypeProjectionWithVariance) {
            val concreteType = (typeArg.typeRef as? FirResolvedTypeRef)?.coneType
            if (concreteType != null) {
                evaluator.types.setTypeSubstitution(typeParamSymbol, concreteType)
            }
        }
    }

    // Infer type arguments from argument expressions when possible
    val arguments = call.argumentList.arguments
    val valueParameters = function.valueParameterSymbols

    // Positional arguments (skip named; track separate param index)
    var paramIndex = 0
    for (arg in arguments) {
        if (arg is FirNamedArgumentExpression) continue
        val parameter = valueParameters.getOrNull(paramIndex++) ?: break
        inferTypeParametersFromTypes(arg.resolvedType, parameter.resolvedReturnType, evaluator.types)
    }

    // Named arguments
    arguments.filterIsInstance<FirNamedArgumentExpression>().forEach { named ->
        val parameter = valueParameters.find { it.name == named.name } ?: return@forEach
        inferTypeParametersFromTypes(named.expression.resolvedType, parameter.resolvedReturnType, evaluator.types)
    }
}

/**
 * Infers type parameter values by comparing an argument type to a parameter type
 */
private fun inferTypeParametersFromTypes(
    argumentType: ConeKotlinType,
    parameterType: ConeKotlinType,
    typeScope: FirScopedEvaluator.TypeSubstitutionContext
) {
    when {
        // If the parameter type is a type parameter, we can substitute directly
        parameterType is ConeTypeParameterType -> {
            val symbol = parameterType.lookupTag.typeParameterSymbol
            typeScope.setTypeSubstitution(symbol, argumentType)
        }

        // If both are class types, compare structure and infer from type arguments
        argumentType is ConeClassLikeType && parameterType is ConeClassLikeType &&
                argumentType.lookupTag.classId == parameterType.lookupTag.classId -> {
            val argumentTypeArgs = argumentType.typeArguments
            val parameterTypeArgs = parameterType.typeArguments

            val minSize = minOf(argumentTypeArgs.size, parameterTypeArgs.size)
            for (i in 0 until minSize) {
                val argTypeArg = argumentTypeArgs[i]
                val paramTypeArg = parameterTypeArgs[i]

                if (argTypeArg is ConeKotlinTypeProjection &&
                    paramTypeArg is ConeKotlinTypeProjection &&
                    argTypeArg.kind == paramTypeArg.kind) {
                    inferTypeParametersFromTypes(argTypeArg.type, paramTypeArg.type, typeScope)
                }
            }
        }

        // Handle flexible types
        argumentType is ConeFlexibleType && parameterType is ConeFlexibleType -> {
            inferTypeParametersFromTypes(argumentType.lowerBound, parameterType.lowerBound, typeScope)
            inferTypeParametersFromTypes(argumentType.upperBound, parameterType.upperBound, typeScope)
        }
    }
}