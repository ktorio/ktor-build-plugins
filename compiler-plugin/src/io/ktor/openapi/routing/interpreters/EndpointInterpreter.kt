package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.*
import io.ktor.openapi.model.*
import io.ktor.openapi.model.JsonSchema.Companion.findSchemaDefinitions
import io.ktor.openapi.model.JsonSchema.Companion.schemaFromConeType
import io.ktor.openapi.routing.RouteField
import io.ktor.openapi.routing.RoutingCallInterpreter
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_FUNCTION_NAMES
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_PACKAGE
import io.ktor.openapi.routing.RoutingReference
import io.ktor.openapi.routing.SourceRange
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.references.symbol
import io.ktor.openapi.routing.getReference

class EndpointInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): io.ktor.openapi.routing.RoutingReferenceResult {
        if (!with(expression.calleeReference) {
                symbol?.packageFqName()?.asString() == ROUTING_PACKAGE &&
                        name.asString() in ROUTING_FUNCTION_NAMES
            }) return io.ktor.openapi.routing.RoutingReferenceResult.None

        val sourceFile = context.getSourceFile() ?: return io.ktor.openapi.routing.RoutingReferenceResult.None
        val invocation = SourceRange(sourceFile, expression.source?.range ?: return io.ktor.openapi.routing.RoutingReferenceResult.None)
        val functionName = expression.calleeReference.name.asString()

        val routeFields = invocation.parseKDoc()
        val path = expression.getArgumentAsString("path")
        val schemaMap = mutableMapOf<String, JsonSchema>()

        for (content in routeFields.filterIsInstance<RouteField.Content>()) {
            if (content.schema == null) continue
            val reference = content.schema?.getReference() ?: continue
            val coneType = resolveTypeLink(context, reference) ?: continue

            context.findSchemaDefinitions(coneType).forEach { resolvedSchema ->
                schemaMap[reference] = resolvedSchema
            }
        }

        val routingReference = RoutingReference.RouteCall(
            functionName,
            routeFields,
            invocation.asCoordinates(),
            path
        )

        return io.ktor.openapi.routing.RoutingReferenceResult.Match(routingReference, schemaMap)
    }
}
