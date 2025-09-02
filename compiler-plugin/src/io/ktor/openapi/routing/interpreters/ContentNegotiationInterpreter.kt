package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.getFunctionName
import io.ktor.openapi.routing.ContentType
import io.ktor.openapi.routing.RoutingCallInterpreter
import io.ktor.openapi.routing.RoutingFunctionConstants.CONTENT_NEGOTIATION
import io.ktor.openapi.routing.RoutingFunctionConstants.INSTALL
import io.ktor.openapi.routing.RoutingFunctionConstants.JSON_LIKE_CALLS
import io.ktor.openapi.routing.RoutingReferenceResult
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.text

class ContentNegotiationInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isInstallContentNegotiation(expression)) return RoutingReferenceResult.None

        val body = expression.arguments.lastOrNull()?.source?.text ?: return RoutingReferenceResult.None
        val contentType = when {
            JSON_LIKE_CALLS.any { it in body } -> ContentType.JSON
            else -> ContentType.OTHER
        }

        return RoutingReferenceResult.ContentType(contentType)
    }

    private fun isInstallContentNegotiation(expression: FirFunctionCall): Boolean =
        expression.getFunctionName() == INSTALL &&
            expression.arguments.firstOrNull()?.source?.text == CONTENT_NEGOTIATION
}
