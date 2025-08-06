package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.model.*
import io.ktor.openapi.model.JsonSchema.Companion.findSchemaDefinitions
import io.ktor.openapi.model.JsonSchema.Companion.schemaFromConeType
import io.ktor.openapi.routing.RouteField
import io.ktor.openapi.routing.RoutingCallInterpreter
import io.ktor.openapi.routing.RoutingReference
import io.ktor.openapi.routing.SourceRange
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.text
import org.jetbrains.kotlin.fir.types.classId

class CallRespondInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): io.ktor.openapi.routing.RoutingReferenceResult {
        if (!(expression.calleeReference.name.asString() == "respond" &&
                    expression.calleeReference.symbol?.packageFqName()?.asString()
                        ?.startsWith("io.ktor.server.response") == true &&
                    expression.extensionReceiver?.source?.text == "call")
        ) return io.ktor.openapi.routing.RoutingReferenceResult.None

        val sourceFile = context.getSourceFile() ?: return io.ktor.openapi.routing.RoutingReferenceResult.None
        val invocation = SourceRange(sourceFile, expression.source?.range ?: return io.ktor.openapi.routing.RoutingReferenceResult.None)
        val functionName = expression.calleeReference.name.asString()

        val coneType = expression.arguments.firstOrNull {
            it.resolvedType.classId?.shortClassName?.asString() != "HttpStatusCode"
        }?.resolvedType ?: return io.ktor.openapi.routing.RoutingReferenceResult.None

        val className = coneType.classId?.shortClassName?.asString() ?: return io.ktor.openapi.routing.RoutingReferenceResult.None
        val schemaMap = mutableMapOf<String, JsonSchema>()

        for (schema in context.findSchemaDefinitions(coneType)) {
            schemaMap[className] = schema
        }

        val schema = io.ktor.openapi.routing.SchemaReference.Resolved(context.schemaFromConeType(coneType, expand = false))
        val routingReference = RoutingReference.CallExpression(
            functionName,
            listOf(RouteField.Response(code = "200", schema = schema)),
            invocation.asCoordinates()
        )

        return io.ktor.openapi.routing.RoutingReferenceResult.Match(routingReference, schemaMap)
    }
}
