package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.getArgument
import io.ktor.compiler.utils.getArgumentAsString
import io.ktor.compiler.utils.getFunctionName
import io.ktor.compiler.utils.resolveToString
import io.ktor.openapi.routing.OauthFlow
import io.ktor.openapi.routing.RoutingCallInterpreter
import io.ktor.openapi.routing.RoutingFunctionConstants.AUTHENTICATION
import io.ktor.openapi.routing.RoutingFunctionConstants.INSTALL
import io.ktor.openapi.routing.RoutingReferenceResult
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.text

/**
 * Detects install(Authentication) { ... } and authentication { ... } calls and derives a SecurityScheme.
 *
 * This interpreter performs simple heuristics over the configuration block body to infer common schemes:
 * - basic("name") -> type: http, scheme: basic
 * - bearer("name") -> type: http, scheme: bearer
 * - jwt("name") -> type: http, scheme: bearer (heuristic)
 * - oauth("name") or oauth2("name") -> type: oauth2 (flows parsed from configuration)
 */
class AuthenticationInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        val callee = expression.getFunctionName()
        val isInstallAuthentication = callee == INSTALL && expression.arguments.firstOrNull()?.source?.text == AUTHENTICATION
        val isAuthenticationCall = callee.equals("authentication", ignoreCase = true)
        if (!isInstallAuthentication && !isAuthenticationCall) return RoutingReferenceResult.None

        val configLambda = expression.arguments.lastOrNull() as? FirAnonymousFunctionExpression
            ?: return RoutingReferenceResult.None

        val lambdaStatements = configLambda.anonymousFunction.body?.statements.orEmpty()

        val oauthCall = findAuthProviderCall(lambdaStatements, listOf("oauth", "oauth2"))
        if (oauthCall != null) {
            val name = oauthCall.getArgumentAsString("name", 0) ?: "oauth2"
            val providerLambda = oauthCall.arguments.lastOrNull() as? FirAnonymousFunctionExpression

            val flows = if (providerLambda != null) {
                parseOAuthFlows(providerLambda)
            } else {
                emptyMap()
            }

            return RoutingReferenceResult.SecurityScheme(
                name = name,
                type = "oauth2",
                flows = flows,
            )
        }

        val basicCall = findAuthProviderCall(lambdaStatements, listOf("basic"))
        if (basicCall != null) {
            val name = basicCall.getArgumentAsString("name", 0) ?: "basicAuth"
            return RoutingReferenceResult.SecurityScheme(
                name = name,
                type = "http",
                scheme = "basic"
            )
        }

        val bearerOrJwtCall = findAuthProviderCall(lambdaStatements, listOf("bearer", "jwt"))
        if (bearerOrJwtCall != null) {
            val name = bearerOrJwtCall.getArgumentAsString("name", 0) ?: "bearerAuth"
            return RoutingReferenceResult.SecurityScheme(
                name = name,
                type = "http",
                scheme = "bearer"
            )
        }

        return RoutingReferenceResult.None
    }

    /**
     * Find a function call for an authentication provider in the list of statements.
     */
    private fun findAuthProviderCall(statements: List<FirStatement>, providerNames: List<String>): FirFunctionCall? {
        return statements
            .filterIsInstance<FirFunctionCall>()
            .find { call ->
                val calleeName = call.getFunctionName()
                providerNames.any { it.equals(calleeName, ignoreCase = true) }
            }
    }

    /**
     * Find nested function calls within the lambda body by name.
     */
    private fun findNestedFunctionCall(lambda: FirAnonymousFunctionExpression, name: String): FirFunctionCall? {
        val body = lambda.anonymousFunction.body ?: return null
        return body.statements
            .flatMap {
                when (it) {
                    is FirReturnExpression -> listOfNotNull(it.result)
                    else -> listOf(it)
                }
            }
            .filterIsInstance<FirFunctionCall>()
            .find {
                it.getFunctionName().equals(name, ignoreCase = true)
            }
    }

    /**
     * Parse OAuth flow configuration from the OAuth configuration lambda.
     */
    context(context: CheckerContext)
    private fun parseOAuthFlows(configLambda: FirAnonymousFunctionExpression): Map<String, OauthFlow> {
        val body = configLambda.anonymousFunction.body ?: return emptyMap()

        // Find the providerLookup lambda which contains the OAuth2ServerSettings
        val providerLookupLambda = body.statements
            .filterIsInstance<FirVariableAssignment>()
            .firstOrNull { it.lValue.source.text == "providerLookup" }
            ?.rValue as? FirAnonymousFunctionExpression ?: return emptyMap()

        // Find the OAuth2ServerSettings constructor call (or fallback to OAuthServerSettings)
        val settingsCall = findNestedFunctionCall(providerLookupLambda, "OAuth2ServerSettings")
            ?: findNestedFunctionCall(providerLookupLambda, "OAuthServerSettings")
            ?: return emptyMap()

        // Extract OAuth configuration parameters
        val authorizeUrl = settingsCall.getArgumentAsString("authorizeUrl", 1) ?: return emptyMap()
        val tokenUrl = settingsCall.getArgumentAsString("accessTokenUrl", 2) ?: return emptyMap()

        // Extract scopes from defaultScopes
        val defaultScopesArg = settingsCall.getArgument("defaultScopes", 6)
        val scopes = extractScopes(defaultScopesArg)

        // Create OAuth flow - assume authorization code flow as most common in Ktor
        return mapOf(
            "authorizationCode" to OauthFlow(
                authorizationUrl = authorizeUrl,
                tokenUrl = tokenUrl,
                refreshUrl = null,
                scopes = scopes ?: emptyMap()
            )
        )
    }

    /**
     * Extract scopes from a defaultScopes argument.
     */
    context(context: CheckerContext)
    private fun extractScopes(defaultScopesArg: FirExpression?): Map<String, String>? {
        if (defaultScopesArg == null) return null

        // Handle listOf(...) call
        if (defaultScopesArg is FirFunctionCall &&
            defaultScopesArg.getFunctionName() == "listOf") {

            val scopeValues = defaultScopesArg.arguments
                .mapNotNull { it.resolveToString() }

            if (scopeValues.isEmpty()) return null

            // Use the scope string as both key and description
            return scopeValues.associateWith { it }
        }

        return null
    }
}
