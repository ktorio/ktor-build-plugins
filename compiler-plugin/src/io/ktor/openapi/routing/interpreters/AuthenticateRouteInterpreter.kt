package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.getSourceFile
import io.ktor.compiler.utils.resolveToString
import io.ktor.openapi.routing.RouteField
import io.ktor.openapi.routing.RoutingCallInterpreter
import io.ktor.openapi.routing.RoutingReference
import io.ktor.openapi.routing.SourceRange
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import io.ktor.compiler.utils.range
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression

/**
 * Detects authenticate("scheme1", "scheme2", ...) { ... } blocks inside routing
 * and emits RouteField.Security entries so that nested routes inherit security requirements.
 */
class AuthenticateRouteInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): io.ktor.openapi.routing.RoutingReferenceResult {
        val callee = expression.calleeReference.name.asString()
        if (!callee.equals("authenticate", ignoreCase = false))
            return io.ktor.openapi.routing.RoutingReferenceResult.None

        val sourceFile = context.getSourceFile() ?: return io.ktor.openapi.routing.RoutingReferenceResult.None
        val invocation = SourceRange(sourceFile, expression.source?.range ?: return io.ktor.openapi.routing.RoutingReferenceResult.None)

        // Collect all string literal arguments (schemes); ignore trailing lambda/config blocks
        val schemaArgs: List<String> = expression.arguments.firstOrNull()?.let { argument ->
            if (argument is FirVarargArgumentsExpression) {
                argument.arguments.mapNotNull { it.resolveToString() }
            } else emptyList()
        } ?: emptyList()

        if (schemaArgs.isEmpty()) return io.ktor.openapi.routing.RoutingReferenceResult.None

        return io.ktor.openapi.routing.RoutingReferenceResult.Match(
            RoutingReference.ExtensionFunction(
                functionName = callee,
                parameters = schemaArgs.map(RouteField::Security),
                coordinates = invocation.asCoordinates(),
            )
        )
    }
}
