package io.ktor.openapi.routing.interpreters

import io.ktor.openapi.model.JsonSchema.Companion.findSchemaDefinitions
import io.ktor.openapi.model.JsonSchema.Companion.schemaFromConeType
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.text

class CallReceiveInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isCallReceive(expression)) return RoutingReferenceResult.None

        val coneType = expression.resolvedType
        val routeNode = RouteNode.CallFeature(
            filePath = context.containingFilePath,
            fir = expression,
            fields = {
                listOf(RouteField.Body(
                    schema = SchemaReference.Resolved(
                        schemaFromConeType(coneType, expand = false)
                    )
                ))
            },
        )

        return RoutingReferenceResult.Match(
            call = routeNode,
            schema = findSchemaDefinitions(coneType).toMap()
        )
    }

    private fun isCallReceive(expression: FirFunctionCall): Boolean =
        expression.calleeReference.name.asString() == "receive" &&
                expression.extensionReceiver?.source?.text == "call" &&
                expression.calleeReference.symbol?.packageFqName()?.asString()?.startsWith("io.ktor.server.request") == true
}
