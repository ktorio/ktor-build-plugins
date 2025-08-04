package io.ktor.openapi.routing

import io.ktor.compiler.utils.*
import io.ktor.openapi.*
import io.ktor.openapi.model.*
import io.ktor.openapi.model.JsonSchema.Companion.findSchemaDefinitions
import io.ktor.openapi.model.JsonSchema.Companion.schemaFromConeType
import io.ktor.openapi.routing.RoutingFunctionConstants.CONTENT_NEGOTIATION
import io.ktor.openapi.routing.RoutingFunctionConstants.INSTALL
import io.ktor.openapi.routing.RoutingFunctionConstants.JSON_LIKE_CALLS
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTE_CLASS
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_FUNCTION_NAMES
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_PACKAGE
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.fullyExpandedClassId
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.references.toResolvedFunctionSymbol
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.text

class EndpointInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!with(expression.calleeReference) {
                symbol?.packageFqName()?.asString() == ROUTING_PACKAGE &&
                        name.asString() in ROUTING_FUNCTION_NAMES
            }) return RoutingReferenceResult.None

        val sourceFile = context.getSourceFile() ?: return RoutingReferenceResult.None
        val invocation = SourceRange(sourceFile, expression.source?.range ?: return RoutingReferenceResult.None)
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

        return RoutingReferenceResult.Match(routingReference, schemaMap)
    }
}

class CallRespondInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!(expression.calleeReference.name.asString() == "respond" &&
                    expression.calleeReference.symbol?.packageFqName()?.asString()
                        ?.startsWith("io.ktor.server.response") == true &&
                    expression.extensionReceiver?.source?.text == "call")
        ) return RoutingReferenceResult.None

        val sourceFile = context.getSourceFile() ?: return RoutingReferenceResult.None
        val invocation = SourceRange(sourceFile, expression.source?.range ?: return RoutingReferenceResult.None)
        val functionName = expression.calleeReference.name.asString()

        val coneType = expression.arguments.firstOrNull {
            it.resolvedType.classId?.shortClassName?.asString() != "HttpStatusCode"
        }?.resolvedType ?: return RoutingReferenceResult.None

        val className = coneType.classId?.shortClassName?.asString() ?: return RoutingReferenceResult.None
        val schemaMap = mutableMapOf<String, JsonSchema>()

        for (schema in context.findSchemaDefinitions(coneType)) {
            schemaMap[className] = schema
        }

        val schema = SchemaReference.Resolved(context.schemaFromConeType(coneType, expand = false))
        val routingReference = RoutingReference.CallExpression(
            functionName,
            listOf(RouteField.Response(code = "200", schema = schema)),
            invocation.asCoordinates()
        )

        return RoutingReferenceResult.Match(routingReference, schemaMap)
    }
}

class CallReceiveInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!(expression.calleeReference.name.asString() == "receive" &&
                    expression.calleeReference.symbol?.packageFqName()?.asString()
                        ?.startsWith("io.ktor.server.request") == true &&
                    expression.extensionReceiver?.source?.text == "call")
        ) return RoutingReferenceResult.None

        val sourceFile = context.getSourceFile() ?: return RoutingReferenceResult.None
        val invocation = SourceRange(sourceFile, expression.source?.range ?: return RoutingReferenceResult.None)
        val functionName = expression.calleeReference.name.asString()

        val coneType = expression.resolvedType
        val className = coneType.classId?.shortClassName?.asString() ?: return RoutingReferenceResult.None
        val schemaMap = mutableMapOf<String, JsonSchema>()

        for (schema in context.findSchemaDefinitions(coneType)) {
            schemaMap[className] = schema
        }

        val schema = SchemaReference.Resolved(context.schemaFromConeType(coneType, expand = false))
        val routingReference = RoutingReference.CallExpression(
            functionName,
            listOf(RouteField.Body(schema = schema)),
            invocation.asCoordinates()
        )

        return RoutingReferenceResult.Match(routingReference, schemaMap)
    }
}

class ParameterGetInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!(expression.calleeReference.name.asString() == "get" &&
                    expression.explicitReceiver?.source?.text == "call.parameters")
        ) return RoutingReferenceResult.None

        val sourceFile = context.getSourceFile() ?: return RoutingReferenceResult.None
        val invocation = SourceRange(sourceFile, expression.source?.range ?: return RoutingReferenceResult.None)
        val functionName = expression.calleeReference.name.asString()

        val key = expression.getArgumentAsString("name") ?: return RoutingReferenceResult.None

        val routingReference = RoutingReference.CallExpression(
            functionName,
            listOf(RouteField.PathParam(key)),
            invocation.asCoordinates()
        )

        return RoutingReferenceResult.Match(routingReference, emptyMap())
    }
}

class QueryParameterGetInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!(expression.calleeReference.name.asString() == "get" &&
                    expression.explicitReceiver?.source?.text == "call.queryParameters")
        ) return RoutingReferenceResult.None

        val sourceFile = context.getSourceFile() ?: return RoutingReferenceResult.None
        val invocation = SourceRange(sourceFile, expression.source?.range ?: return RoutingReferenceResult.None)
        val functionName = expression.calleeReference.name.asString()

        val key = expression.getArgumentAsString("name") ?: return RoutingReferenceResult.None

        val routingReference = RoutingReference.CallExpression(
            functionName,
            listOf(RouteField.QueryParam(key)),
            invocation.asCoordinates()
        )

        return RoutingReferenceResult.Match(routingReference, emptyMap())
    }
}

class CustomExtensionInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        val receiverFqName = expression.extensionReceiver?.resolvedType
            ?.fullyExpandedClassId(context.session)
            ?.asFqNameString()
        if (receiverFqName != "$ROUTING_PACKAGE.$ROUTE_CLASS") return RoutingReferenceResult.None

        val sourceFile = context.getSourceFile() ?: return RoutingReferenceResult.None
        val invocation = SourceRange(sourceFile, expression.source?.range ?: return RoutingReferenceResult.None)
        val functionName = expression.calleeReference.name.asString()

        val body = expression.calleeReference.getCoordinates() ?: return RoutingReferenceResult.None
        val routeFields = invocation.parseKDoc() + body.parseKDoc()
        val coordinates = SourceCoordinates(invocation, body)

        val routingReference = RoutingReference.ExtensionFunction(
            functionName,
            routeFields,
            coordinates
        )

        return RoutingReferenceResult.Match(routingReference, emptyMap())
    }

    /**
     * Resolved the source file and text range of a reference.
     */
    context(context: CheckerContext)
    private fun FirNamedReference.getCoordinates(): SourceRange? {
        val resolvedFunctionSymbol = resolved?.toResolvedFunctionSymbol() ?: return null
        val containingFile = context.session.firProvider.getFirCallableContainerFile(resolvedFunctionSymbol) ?: return null
        val filePath = containingFile.sourceFile?.path ?: return null
        val fileText = containingFile.source?.text ?: return null
        val range = resolvedFunctionSymbol.source?.range ?: return null

        return SourceRange(
            file = SourceFile(
                path = filePath,
                text = fileText,
            ),
            range = range,
        )
    }
}

class ContentNegotiationInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!(expression.calleeReference.name.asString() == INSTALL &&
                    expression.arguments.firstOrNull()?.source.text == CONTENT_NEGOTIATION)
        ) return RoutingReferenceResult.None

        val body = expression.arguments.lastOrNull()?.source?.text ?: return RoutingReferenceResult.None
        val contentType = when {
            JSON_LIKE_CALLS.any { it in body } -> ContentType.JSON
            else -> ContentType.OTHER
        }

        return RoutingReferenceResult.ContentType(contentType)
    }
}

val KtSourceElement.range: IntRange get() =
    startOffset..endOffset

fun CheckerContext.getSourceFile(): SourceFile? {
    return SourceFile(
        containingFilePath ?: return null,
        containingFile?.source?.text ?: return null,
    )
}