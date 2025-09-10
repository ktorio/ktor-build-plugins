package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.resolvedType

class ResponseHeaderInterpreter : RoutingCallInterpreter {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isHeaderAppend(expression) && !isHeaderCall(expression)) {
            return RoutingReferenceResult.None
        }

        val nameArg = expression.getArgument("name") ?: return RoutingReferenceResult.None
        
        val routeNode = RouteNode.CallFeature(
            filePath = context.containingFilePath,
            fir = expression,
            fields = {
                listOf(RouteField.ResponseHeader(nameArg.evaluateHeader() ?: return@CallFeature emptyList()))
            }
        )

        return RoutingReferenceResult.Match(routeNode)
    }

    private fun isHeaderAppend(expression: FirFunctionCall): Boolean =
        expression.getFunctionName() == "append"
                && expression.explicitReceiver?.resolvedType?.classId?.shortClassName?.asString() == "ResponseHeaders"

    private fun isHeaderCall(expression: FirFunctionCall): Boolean =
        expression.getFunctionName() == "header"
                && expression.explicitReceiver?.resolvedType?.classId?.shortClassName?.asString() == "RoutingResponse"
}