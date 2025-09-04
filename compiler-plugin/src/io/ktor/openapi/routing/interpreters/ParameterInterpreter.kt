package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.text

class ParameterInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isParametersGet(expression)) return RoutingReferenceResult.None

        val key = expression.getArgument("name")
            ?: return RoutingReferenceResult.None

        val routeNode = RouteNode.CallFeature(
            filePath = context.containingFilePath,
            fir = expression,
            fields = {
                val receiverText = expression.explicitReceiver?.source?.text ?: return@CallFeature emptyList()
                val key = key.evaluate().asString() ?: return@CallFeature emptyList()
                val keyIsInPath = {
                    stack.flatMap {
                        it.resolvedFields?.filterIsInstance<RouteField.Path>().orEmpty()
                    }.any { "{$key}" in it.path }
                }
                listOf(when {
                    "path" in receiverText -> RouteField.PathParam(key)
                    "query" in receiverText -> RouteField.QueryParam(key)
                    // using ambiguous "parameters" property, so we need to check the stack
                    keyIsInPath() -> RouteField.PathParam(key)
                    else -> RouteField.QueryParam(key)
                })
            }
        )

        return RoutingReferenceResult.Match(routeNode)
    }

    private fun isParametersGet(expression: FirFunctionCall): Boolean =
        expression.getFunctionName().startsWith("get") &&
                expression.explicitReceiver?.resolvedType?.classId?.shortClassName?.asString() == "Parameters"

}
