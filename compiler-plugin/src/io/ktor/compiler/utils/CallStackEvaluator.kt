package io.ktor.compiler.utils

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid

/**
 * Creates a scoped evaluator for evaluating expressions through a call stack
 *
 * @param callStack List of function calls in order of execution (from caller to callee)
 * @param session FIR session for resolving symbols and types
 * @return A scoped evaluator that can evaluate expressions with the call stack context
 */
context(session: FirSession)
fun createCallStackEvaluator(callStack: List<FirFunctionCall>): FirScopedExpressionEvaluator {

    val evaluator = FirScopedExpressionEvaluator(session) {
        processCallStack(callStack)
    }

    return evaluator
}

/**
 * Processes the entire call stack to build parameter values at each level
 */
@OptIn(SymbolInternals::class)
context(session: FirSession, scope: FirEvaluationScope)
private fun processCallStack(callStack: List<FirFunctionCall>) {
    if (callStack.isEmpty()) return

    // Map to store the current value of each symbol
    val valueBySymbol = mutableMapOf<FirCallableSymbol<*>, FirExpression>()

    // Process each call in the stack
    for (i in 0 until callStack.size) {
        val call = callStack[i]

        // Get the called function
        val calleeReference = call.calleeReference as? FirResolvedNamedReference
            ?: continue

        val functionSymbol = calleeReference.resolvedSymbol as? FirFunctionSymbol<*>
            ?: continue

        val function = functionSymbol.fir

        // Process this function call
        processFunction(call, function, valueBySymbol, session)
    }

    // Populate the root scope with all resolved values
    for ((symbol, value) in valueBySymbol) {
        scope.setVariableValue(symbol, value)
    }
}

/**
 * Processes a single function call to map arguments to parameters
 */
private fun processFunction(
    call: FirFunctionCall,
    function: FirFunction,
    valueBySymbol: MutableMap<FirCallableSymbol<*>, FirExpression>,
    session: FirSession
) {
    val arguments = call.argumentList.arguments
    val parameters = function.valueParameters

    // Handle simple positional arguments
    for (i in arguments.indices) {
        if (i >= parameters.size) break

        val argument = arguments[i]
        val parameter = parameters[i]

        // Evaluate the argument using current known values
        val evaluatedArg = evaluateArgumentWithKnownValues(argument, valueBySymbol, session)

        // Store the evaluated value for this parameter
        evaluatedArg?.let {
            valueBySymbol[parameter.symbol] = it
        }
    }

    // Process named arguments if present
    arguments.forEach { arg ->
        if (arg is FirNamedArgumentExpression) {
            val paramName = arg.name
            val parameter = parameters.find { it.name == paramName }

            if (parameter != null) {
                val evaluatedArg = evaluateArgumentWithKnownValues(arg.expression, valueBySymbol, session)
                evaluatedArg?.let {
                    valueBySymbol[parameter.symbol] = it
                }
            }
        }
    }

    // Process body expressions that reference parameters
    processBodyExpressions(function, valueBySymbol, session)
}

/**
 * Processes the function body to find and evaluate expressions that reference parameters
 */
private fun processBodyExpressions(
    function: FirFunction,
    valueBySymbol: MutableMap<FirCallableSymbol<*>, FirExpression>,
    session: FirSession
) {
    val body = function.body ?: return

    // Find assignments to local variables within the function
    body.accept(object : FirVisitorVoid() {
        override fun visitElement(element: FirElement) {
            element.acceptChildren(this)
        }

        override fun visitVariable(variable: FirVariable) {
            val initializer = variable.initializer
            if (initializer != null) {
                val evaluatedInit = evaluateArgumentWithKnownValues(initializer, valueBySymbol, session)
                evaluatedInit?.let {
                    valueBySymbol[variable.symbol] = it
                }
            }
            visitElement(variable)
        }

        // Handle property assignments
        override fun visitPropertyAccessExpression(propertyAccessExpression: FirPropertyAccessExpression) {
            // Process any receiver expressions
            propertyAccessExpression.explicitReceiver?.accept(this)

            // Add the property reference to our tracked symbols
            val calleeReference = propertyAccessExpression.calleeReference
            if (calleeReference is FirResolvedNamedReference) {
                val symbol = calleeReference.resolvedSymbol as? FirCallableSymbol<*>
                if (symbol != null) {
                    // We can't evaluate the property value here, but we can track that it exists
                    // Later, when it's used, we'll know it's a valid reference
                }
            }

            visitElement(propertyAccessExpression)
        }

        // Process function calls within the body that might affect values
        override fun visitFunctionCall(functionCall: FirFunctionCall) {
            // Process arguments
            functionCall.argumentList.arguments.forEach { it.accept(this) }

            // If this is an assignment operation, track the variable being assigned
            if (functionCall.calleeReference is FirResolvedNamedReference) {
                val name = (functionCall.calleeReference as FirResolvedNamedReference).name.asString()

                // Check for assignments like "x = value" or "x += value"
                if (name == "set" || name.endsWith("Assign")) {
                    // Handle property assignments
                    // This would require more complex analysis
                }
            }

            visitElement(functionCall)
        }
    }, null)
}

/**
 * Evaluates an argument expression using currently known variable values
 */
private fun evaluateArgumentWithKnownValues(
    argument: FirExpression,
    valueBySymbol: Map<FirCallableSymbol<*>, FirExpression>,
    session: FirSession
): FirExpression? {
    // For simple literals, just return them
    if (argument is FirLiteralExpression) {
        return argument
    }

    // If the argument is a reference to a parameter we already know
    if (argument is FirPropertyAccessExpression) {
        val calleeReference = argument.calleeReference as? FirResolvedNamedReference
        if (calleeReference != null) {
            val symbol = calleeReference.resolvedSymbol as? FirCallableSymbol<*>
            if (symbol != null && valueBySymbol.containsKey(symbol)) {
                return valueBySymbol[symbol]
            }
        }
    }

    // For more complex expressions, we need to substitute known values and then evaluate
    val substitutedExpression = substituteKnownValues(argument, valueBySymbol)

    // Try to evaluate the substituted expression
    @OptIn(PrivateConstantEvaluatorAPI::class)
    val result = FirExpressionEvaluator.evaluateExpression(substitutedExpression, session)

    return when (result) {
        is FirEvaluatorResult.Evaluated -> result.result as? FirExpression
        else -> null
    }
}

/**
 * Substitutes known variable values in an expression
 */
private fun substituteKnownValues(
    expression: FirExpression,
    valueBySymbol: Map<FirCallableSymbol<*>, FirExpression>
): FirExpression {
    // Create a transformer that will replace references to known variables with their values
    val transformer = object : FirTransformer<Nothing?>() {
        override fun <E : FirElement> transformElement(element: E, data: Nothing?): E {
            @Suppress("UNCHECKED_CAST")
            return element.transformChildren(this, data) as E
        }

        override fun transformPropertyAccessExpression(
            propertyAccessExpression: FirPropertyAccessExpression,
            data: Nothing?
        ): FirStatement {
            val transformed = super.transformPropertyAccessExpression(propertyAccessExpression, data) as FirPropertyAccessExpression

            // Check if this property access refers to a known variable
            val calleeReference = transformed.calleeReference as? FirResolvedNamedReference ?: return transformed
            val symbol = calleeReference.resolvedSymbol as? FirCallableSymbol<*> ?: return transformed

            // If we know the value of this symbol, replace with the value
            return valueBySymbol[symbol] ?: transformed
        }
    }

    return expression.transform<FirElement, Nothing?>(transformer, data = null) as FirExpression
}

/**
 * Extension function for FirScopedExpressionEvaluator to evaluate an expression
 * using a scope built from analysis of a call stack
 */
//fun evaluateExpressionInCallStack(
//    expression: FirExpression,
//    callStack: List<FirFunctionCall>
//): FirEvaluatorResult? {
//    // Create a scope from the call stack
//    val scope = FirEvaluationScope()
//    processCallStack(callStack, scope, session)
//
//    // Evaluate the expression with this scope
//    return evaluateInScope(expression) {
//        // Copy all variable values from the call stack scope
//        for (symbol in scope.getAllDefinedVariables()) {
//            val value = scope.getVariableValue(symbol) ?: continue
//            setVariableValue(symbol, value)
//        }
//    }
//}