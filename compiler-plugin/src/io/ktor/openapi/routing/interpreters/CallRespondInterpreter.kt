package io.ktor.openapi.routing.interpreters

import io.ktor.openapi.model.JsonSchema.Companion.findSchemaDefinitions
import io.ktor.openapi.model.JsonSchema.Companion.schemaFromConeType
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.text

class CallRespondInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isCallRespond(expression)) return RoutingReferenceResult.None

        val coneType = firstNonStatusCodeArgType(expression) ?: return RoutingReferenceResult.None

        val routeNode = RouteNode.CallFeature(
            filePath = context.containingFilePath,
            fir = expression,
            fields = {
                buildList {
                    add(RouteField.Response(
                        code = "200", // TODO infer the actual code from arguments
                        schema = SchemaReference.Resolved(
                            schemaFromConeType(coneType, expand = false)
                        )
                    ))
                    findSchemaDefinitions(coneType).forEach { (name, schema) ->
                        add(RouteField.Schema(name, schema))
                    }
                }
            },
        )

        return RoutingReferenceResult.Match(routeNode)
    }

    private fun firstNonStatusCodeArgType(expression: FirFunctionCall): ConeKotlinType? = expression.arguments.firstOrNull {
        it.resolvedType.classId?.shortClassName?.asString() != "HttpStatusCode"
    }?.resolvedType

    private fun isCallRespond(expression: FirFunctionCall): Boolean =
        expression.calleeReference.name.asString() == "respond" &&
            expression.extensionReceiver?.source?.text == "call" &&
            expression.calleeReference.symbol?.packageFqName()?.asString()?.startsWith("io.ktor.server.response") == true
}
