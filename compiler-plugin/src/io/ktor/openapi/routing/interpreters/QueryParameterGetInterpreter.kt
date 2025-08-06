package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.routing.RouteField
import io.ktor.openapi.routing.RoutingCallInterpreter
import io.ktor.openapi.routing.RoutingReference
import io.ktor.openapi.routing.SourceRange
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.text

class QueryParameterGetInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): io.ktor.openapi.routing.RoutingReferenceResult {
        if (!(expression.calleeReference.name.asString() == "get" &&
                    expression.explicitReceiver?.source?.text == "call.queryParameters")
        ) return io.ktor.openapi.routing.RoutingReferenceResult.None

        val sourceFile = context.getSourceFile() ?: return io.ktor.openapi.routing.RoutingReferenceResult.None
        val invocation = SourceRange(sourceFile, expression.source?.range ?: return io.ktor.openapi.routing.RoutingReferenceResult.None)
        val functionName = expression.calleeReference.name.asString()

        val key = expression.getArgumentAsString("name") ?: return io.ktor.openapi.routing.RoutingReferenceResult.None

        val routingReference = RoutingReference.CallExpression(
            functionName,
            listOf(RouteField.QueryParam(key)),
            invocation.asCoordinates()
        )

        return io.ktor.openapi.routing.RoutingReferenceResult.Match(routingReference, emptyMap())
    }
}
