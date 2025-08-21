package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.types.isBoolean
import org.jetbrains.kotlin.fir.types.resolvedType

/**
 * Detects authenticate("scheme1", "scheme2", ...) { ... } blocks inside routing
 * and emits RouteField.Security entries so that nested routes inherit security requirements.
 */
class AuthenticateRouteInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isAuthenticateCall(expression))
            return RoutingReferenceResult.None

        // Collect all string literal arguments (schemes); ignore trailing lambda/config blocks
        return RoutingReferenceResult.Match(
            RouteNode.Route(
                filePath = context.containingFilePath,
                fir = expression,
                fields = {
                    val schemeVarargs = expression.arguments.firstOrNull() as? FirVarargArgumentsExpression
                    val schemes = schemeVarargs?.arguments?.mapNotNull {
                        it.evaluate()?.asString()
                    }?.takeIf { it.isNotEmpty() }

                    val optionalArg = expression.arguments.firstOrNull {
                        it.resolvedType.isBoolean
                    }

                    buildList {
                        schemes?.map(RouteField::Security)?.let {
                            addAll(it)
                        } ?: add(RouteField.Security.All)

                        if (optionalArg?.evaluate()?.asBoolean() == true) {
                            add(RouteField.Security.Optional)
                        }
                    }
                },
            )
        )
    }

    private fun isAuthenticateCall(call: FirFunctionCall): Boolean =
        call.getFunctionName().equals("authenticate", ignoreCase = false)
                && call.isInPackage("io.ktor.server.auth")
}
