package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.text

class ParameterGetInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isGetParameter(expression)) return RoutingReferenceResult.None

        val key = expression.getArgument("name")
            ?: return RoutingReferenceResult.None

        val routeNode = RouteNode.CallFeature(
            filePath = context.containingFilePath,
            fir = expression,
            fields = {
                key.evaluate().asString()?.let {
                    listOf(RouteField.PathParam(it))
                } ?: emptyList()
            }
        )

        return RoutingReferenceResult.Match(routeNode)
    }

    private fun isGetParameter(expression: FirFunctionCall): Boolean = expression.getFunctionName() == "get" &&
            expression.explicitReceiver?.source?.text == "call.parameters"
}
