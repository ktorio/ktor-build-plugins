package io.ktor.openapi.ir

import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitor

/**
 * Searches through route lambda bodies for common Ktor call references, so that we may infer some annotations
 * from the code.
 */
class CallHandlerAnalyzer(
    val callInference: IrCallHandlerInference,
    val context: CodeGenContext,
    val visited: Set<IrFunction> = emptySet(),
    val variables: MutableMap<IrValueSymbol, IrExpression> = mutableMapOf(),
): IrVisitor<Unit, MutableList<RouteField>>(), CodeGenContext by context {
    companion object {
        const val KTOR_PACKAGE = "io.ktor"
    }

    fun analyze(element: IrElement): List<RouteField> =
        buildList { element.accept(this@CallHandlerAnalyzer, this) }

    override fun visitElement(element: IrElement, data: MutableList<RouteField>) =
        element.acceptChildren(this, data)

    override fun visitVariable(declaration: IrVariable, data: MutableList<RouteField>) {
        declaration.initializer?.let { initializer ->
            val resolved = copyAndResolve(initializer)
            if (resolved != null) {
                variables[declaration.symbol] = resolved
            }
        }
        super.visitVariable(declaration, data)
    }

    override fun visitCall(expression: IrCall, data: MutableList<RouteField>) {
        try {
            val function = expression.symbol.owner
            if (function.kotlinFqName.asString().startsWith(KTOR_PACKAGE)) {
                val routeDetails = callInference.findRouteDetails(expression)
                if (routeDetails != null) {
                    data += routeDetails
                }
            } else if (function !in visited && function.body != null) {
                // when calling custom functions with Ktor types in their signature,
                // we inspect the body of these for route details
                if (function.parameters.any { it.type.containsKtorTypeReference() }) {
                    val arguments = createVariableScope(function, expression)
                    val functionAnalyzer = CallHandlerAnalyzer(
                        callInference,
                        context,
                        visited + function,
                        arguments
                    )
                    function.body!!.accept(functionAnalyzer, data)
                } else {
                    for (arg in expression.arguments) {
                        if (arg == null) continue

                        val resolvedArg = copyAndResolve(arg) ?: arg

                        val fnExpr = resolvedArg as? IrFunctionExpression ?: continue
                        val lambdaFn = fnExpr.function
                        val lambdaBody = lambdaFn.body ?: continue
                        if (lambdaFn in visited) continue

                        val lambdaAnalyzer = CallHandlerAnalyzer(
                            callInference = callInference,
                            context = context,
                            visited = visited + lambdaFn,
                            variables = variables
                        )
                        lambdaBody.accept(lambdaAnalyzer, data)
                    }
                }
            }

            // Continue normal traversal
            super.visitCall(expression, data)
        } catch (e: Throwable) {
            context.log("Failed to analyze $expression", e)
        }
    }

    private fun IrType.containsKtorTypeReference(): Boolean {
        val fqName = classFqName?.asString()
        if (fqName != null && fqName.startsWith(KTOR_PACKAGE)) return true

        val simple = this as? IrSimpleType ?: return false
        return simple.arguments.any { proj ->
            val argType = proj.typeOrNull
            argType != null && argType.containsKtorTypeReference()
        }
    }

    /**
     * We need to substitute parameters for functions.
     */
    override fun copyAndResolve(expression: IrExpression): IrExpression? {
        val copied = expression.deepCopyWithSymbols()
        var hasUnresolvedVariables = false

        // Substitute variable references with their values from the scope
        val expanded = copied.transform(object : IrElementTransformerVoid() {
            override fun visitGetValue(expression: IrGetValue): IrExpression {
                return when(val argumentValue = variables[expression.symbol]?.deepCopyWithSymbols()) {
                    null -> {
                        hasUnresolvedVariables = true
                        super.visitGetValue(expression)
                    }
                    else -> {
                        argumentValue
                    }
                }
            }
        }, null)

        // return the substituted expression only if there were no unresolved variables
        return expanded.takeIf { !hasUnresolvedVariables }
    }

}

/**
 * Initialize variables from the function call parameters.
 */
internal fun createVariableScope(
    function: IrFunction,
    call: IrCall
): MutableMap<IrValueSymbol, IrExpression> {
    val variables = mutableMapOf<IrValueSymbol, IrExpression>()

    for (param in function.parameters) {
        val argument = call.arguments[param.indexInParameters] ?: param.defaultValue?.expression
            ?: continue
        variables[param.symbol] = argument
    }

    return variables
}