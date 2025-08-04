package io.ktor.openapi.routing

import io.ktor.openapi.model.JsonSchema
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall

/**
 * Infers aspects for OpenAPI generation from a general function call in the source code.
 */
fun interface RoutingCallInterpreter {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    fun check(expression: FirFunctionCall): RoutingReferenceResult
}

sealed interface RoutingReferenceResult {
    object None : RoutingReferenceResult

    data class Match(
        val call: RoutingReference,
        val schema: Map<String, JsonSchema>,
    ) : RoutingReferenceResult

    data class ContentType(
        val contentType: io.ktor.openapi.routing.ContentType,
    ): RoutingReferenceResult
}