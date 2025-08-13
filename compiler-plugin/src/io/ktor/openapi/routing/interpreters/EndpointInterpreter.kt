package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.*
import io.ktor.openapi.model.*
import io.ktor.openapi.model.JsonSchema.Companion.findSchemaDefinitions
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.RoutingFunctionConstants.HTTP_METHODS
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_FUNCTION_NAMES
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_PACKAGE
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.symbol

class EndpointInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!with(expression.calleeReference) {
                symbol?.packageFqName()?.asString() == ROUTING_PACKAGE &&
                        name.asString() in ROUTING_FUNCTION_NAMES
            }) return RoutingReferenceResult.None

        val invocation = expression.getLocation() ?: return RoutingReferenceResult.None
        val routeFields = invocation.parseKDoc().toMutableList()
        expression.getArgumentAsString("path")?.let {
            routeFields += RouteField.Path(it)
        }
        val schemaMap = mutableMapOf<String, JsonSchema>()

        for (content in routeFields.filterIsInstance<RouteField.Content>()) {
            if (content.schema == null) continue
            val reference = content.schema?.getReference() ?: continue
            val coneType = resolveTypeLink(context, reference) ?: continue

            schemaMap += findSchemaDefinitions(coneType)
        }

        val routeNode = RouteNode.Route(
            filePath = context.containingFilePath,
            fir = expression,
            fields = {
                invocation.parseKDoc() + buildList {
                    expression.getArgument("path")?.evaluate()?.asString()?.let {
                        add(RouteField.Path(it))
                    }
                    // TODO can also be supplied as an argument
                    expression.calleeReference.name.asString().takeIf { it in HTTP_METHODS }?.let {
                        add(RouteField.Method(it))
                    }
                }
            },
        )

        return RoutingReferenceResult.Match(routeNode, schemaMap)
    }
}
