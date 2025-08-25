package io.ktor.openapi.routing

import io.ktor.openapi.model.JsonSchema
import kotlinx.serialization.Serializable
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
    /**
     * Nothing found for this function call.
     */
    object None : RoutingReferenceResult

    /**
     * Route node found.
     */
    data class Match(val call: RouteNode) : RoutingReferenceResult

    /**
     * `install(ContentNegotiation)`
     */
    data class ContentType(val contentType: io.ktor.openapi.routing.ContentType): RoutingReferenceResult

    /**
     * `authentication {}`
     */
    data class SecurityScheme(
        val name: String,
        val type: String,
        val scheme: String? = null,
        val bearerFormat: String? = null,
        val openIdConnectUrl: String? = null,
        val flows: Map<String, OauthFlow>? = null,
    ) : RoutingReferenceResult
}

@Serializable
data class OauthFlow(
    val authorizationUrl: String,
    val tokenUrl: String,
    val refreshUrl: String? = null,
    val scopes: Map<String, String>? = null,
)