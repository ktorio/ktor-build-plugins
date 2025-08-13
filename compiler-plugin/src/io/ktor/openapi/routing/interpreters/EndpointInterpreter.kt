package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.compiler.utils.resolveTypeLink
import io.ktor.openapi.*
import io.ktor.openapi.model.JsonSchema.Companion.findSchemaDefinitions
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.RouteField
import io.ktor.openapi.routing.RouteField.Schema
import io.ktor.openapi.routing.RoutingFunctionConstants.HTTP_METHODS
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_FUNCTION_NAMES
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_PACKAGE
import io.ktor.openapi.routing.getReference
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.symbol

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
                    addAll(invocation.parseKDoc().also { fields ->
                        addAll(sequence<Schema> {
                            for (field in fields.asSequence().filterIsInstance<RouteField.Content>()) {
                                val reference = field.schema?.getReference() ?: continue
                                val coneType = resolveTypeLink(reference) ?: continue

                                yieldAll(findSchemaDefinitions(coneType).map { (name, schema) ->
                                    Schema(name, schema)
                                })
                            }
                        })
                    })

                    // path argument
                    expression.getArgument("path")?.evaluate()?.asString()?.let {
                        add(RouteField.Path(it))
                    }

                    // method from function
                    // TODO can also be supplied as an argument
                    expression.calleeReference.name.asString().takeIf { it in HTTP_METHODS }?.let {
                        add(RouteField.Method(it))
                    }
                }
            },
        )

        return RoutingReferenceResult.Match(routeNode)
    }

    private fun isRouteFunction(expression: FirFunctionCall): Boolean =
        with(expression.calleeReference) {
            symbol?.packageFqName()?.asString() == ROUTING_PACKAGE &&
                    name.asString() in ROUTING_FUNCTION_NAMES
        }
}
