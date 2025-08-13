package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.text

class QueryParameterGetInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isCallQueryParameters(expression))
            return RoutingReferenceResult.None

        val key = expression.getArgumentAsString("name") ?: return RoutingReferenceResult.None

        val routeNode = RouteNode.CallFeature(
            filePath = context.containingFilePath,
            fir = expression,
            fields = { listOf(RouteField.QueryParam(key)) },
        )

        return RoutingReferenceResult.Match(routeNode)
    }

    private fun isCallQueryParameters(expression: FirFunctionCall): Boolean =
        expression.calleeReference.name.asString() == "get" &&
            expression.explicitReceiver?.source?.text == "call.queryParameters"
}
