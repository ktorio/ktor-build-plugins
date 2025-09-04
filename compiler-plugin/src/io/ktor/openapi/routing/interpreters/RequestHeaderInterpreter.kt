package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.asString
import io.ktor.compiler.utils.getArgument
import io.ktor.compiler.utils.getFunctionName
import io.ktor.openapi.routing.RouteField
import io.ktor.openapi.routing.RouteNode
import io.ktor.openapi.routing.RoutingCallInterpreter
import io.ktor.openapi.routing.RoutingReferenceResult
import io.ktor.openapi.routing.evaluate
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.resolvedType

class RequestHeaderInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isHeadersGet(expression) && !isHeaderCallFromRequest(expression)) return RoutingReferenceResult.None

        val key = expression.getArgument("name")
            ?: return RoutingReferenceResult.None

        val routeNode = RouteNode.CallFeature(
            filePath = context.containingFilePath,
            fir = expression,
            fields = {
                listOf(RouteField.Header(key.evaluateHeader() ?: return@CallFeature emptyList()))
            }
        )

        return RoutingReferenceResult.Match(routeNode)
    }

    private fun isHeadersGet(expression: FirFunctionCall): Boolean =
        expression.getFunctionName().startsWith("get") &&
                expression.explicitReceiver?.resolvedType?.classId?.shortClassName?.asString() == "Headers"

    private fun isHeaderCallFromRequest(expression: FirFunctionCall): Boolean =
        expression.getFunctionName() == "header" &&
                expression.explicitReceiver?.resolvedType?.classId?.shortClassName?.asString() == "RoutingRequest"
}