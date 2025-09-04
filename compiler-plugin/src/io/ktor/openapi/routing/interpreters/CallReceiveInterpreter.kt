package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.model.JsonSchema.Companion.asJsonSchema
import io.ktor.openapi.model.JsonSchema.Companion.findSchemaDefinitions
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.text

class CallReceiveInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isCallReceive(expression)) return RoutingReferenceResult.None

        val routeNode = RouteNode.CallFeature(
            filePath = context.containingFilePath,
            fir = expression,
            fields = {
                buildList {
                    val coneType = expression.resolvedType
                    add(RouteField.Body(
                        schema = SchemaReference.Resolved(coneType.asJsonSchema(fullSchema = false))
                    ))
                    coneType.findSchemaDefinitions().forEach { (name, schema) ->
                        add(RouteField.Schema(name, schema))
                    }
                }
            },
        )

        return RoutingReferenceResult.Match(routeNode)
    }

    private fun isCallReceive(call: FirFunctionCall): Boolean =
        call.getFunctionName() == "receive" &&
                call.extensionReceiver?.source?.text == "call" &&
                call.isInPackage("io.ktor.server.request")
}
