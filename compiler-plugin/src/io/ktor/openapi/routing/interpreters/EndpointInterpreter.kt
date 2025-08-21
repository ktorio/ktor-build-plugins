package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.*
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.RouteField
import io.ktor.openapi.routing.RoutingFunctionConstants.HTTP_METHODS
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_FUNCTION_NAMES
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_PACKAGE
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall

class EndpointInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isRouteFunction(expression)) return RoutingReferenceResult.None
        val invocation = expression.getLocation() ?: return RoutingReferenceResult.None

        val routeNode = RouteNode.Route(
            filePath = context.containingFilePath,
            fir = expression,
            fields = {
                buildList {
                    // kDoc fields
                    addAll(invocation.parseKDoc().resolveSchemaReferences())

                    // path argument
                    expression.getArgument("path")?.evaluate()?.asString()?.let {
                        add(RouteField.Path(it))
                    }

                    // method from function name or argument
                    getMethod(expression)?.let {
                        add(RouteField.Method(it))
                    }
                }
            },
        )

        return RoutingReferenceResult.Match(routeNode)
    }

    private fun isRouteFunction(call: FirFunctionCall): Boolean =
        call.isInPackage(ROUTING_PACKAGE) &&
            call.getFunctionName() in ROUTING_FUNCTION_NAMES


    /**
     * If the function name is an HTTP method, use that.  Otherwise, check if a method argument is supplied.
     */
    context(stack: RouteStack)
    private fun getMethod(expression: FirFunctionCall): String? =
        expression.getFunctionName().takeIf { it in HTTP_METHODS }
            ?: expression.getArgument("method", 1)?.evaluate()
                ?.sourceText()?.substringAfterLast('.')?.lowercase().takeIf { it in HTTP_METHODS }

}
