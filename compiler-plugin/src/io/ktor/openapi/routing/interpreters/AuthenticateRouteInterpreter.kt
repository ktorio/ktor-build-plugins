package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.arguments

/**
 * Detects authenticate("scheme1", "scheme2", ...) { ... } blocks inside routing
 * and emits RouteField.Security entries so that nested routes inherit security requirements.
 */
class AuthenticateRouteInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        val callee = expression.calleeReference.name.asString()
        if (!callee.equals("authenticate", ignoreCase = false))
            return RoutingReferenceResult.None

        // Collect all string literal arguments (schemes); ignore trailing lambda/config blocks
        val schemaArgs: List<String> = expression.arguments.firstOrNull()?.let { argument ->
            if (argument is FirVarargArgumentsExpression) {
                argument.arguments.mapNotNull { it.resolveToString() }
            } else emptyList()
        } ?: emptyList()

        if (schemaArgs.isEmpty()) return RoutingReferenceResult.None

        return RoutingReferenceResult.Match(
            RouteNode.CallFeature(
                filePath = context.containingFilePath,
                fir = expression,
                fields = {
                    schemaArgs.map(RouteField::Security)
                },
            )
        )
    }
}
