package io.ktor.openapi

import io.ktor.compiler.utils.*
import io.ktor.openapi.OpenApiKtorRouting.isCustomExtension
import io.ktor.openapi.OpenApiKtorRouting.isRoute
import io.ktor.openapi.OpenApiKtorRouting.isCallRespond
import io.ktor.openapi.OpenApiKtorRouting.isCallReceive
import io.ktor.openapi.OpenApiKtorRouting.isParameterGet
import io.ktor.openapi.OpenApiKtorRouting.isQueryParameterGet
import io.ktor.openapi.OpenApiKtorSchema.classifyContentNegotiationBody
import io.ktor.openapi.OpenApiKtorSchema.isInstallContentNegotiation
import io.ktor.openapi.model.*
import io.ktor.openapi.model.JsonSchema.Companion.schemaFromConeType
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.checkers.fullyExpandedClassId
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.references.toResolvedFunctionSymbol
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.text
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.writeText

class OpenApiExtension(
    private val config: OpenApiProcessorConfig,
) : FirExtensionRegistrar() {

    private val routeElements = mutableListOf<RouteElement>()
    private val schemas = mutableMapOf<String, JsonSchema>()
    private var defaultContentType: String = ContentType.OTHER.value

    override fun ExtensionRegistrarContext.configurePlugin() {
        +::OpenApiFirAdditionalChecksExtension
    }

    fun isEmpty() = routeElements.isEmpty()

    fun saveSpecification(json: Json) {
        // skip if there are no paths
        if (isEmpty()) return

        val outputFile = Paths.get(config.outputFile).apply {
            if (parent != null && !parent.exists())
                Files.createDirectories(parent)
        }
        val openApiSpec = OpenApiSchemaGenerator.buildSchema(
            config.info,
            routeElements,
            schemas,
            defaultContentType,
            json
        )
        val jsonString = json.encodeToString(openApiSpec)

        outputFile.writeText(jsonString)
    }


    private inner class OpenApiFirAdditionalChecksExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
        override val expressionCheckers: ExpressionCheckers
            get() = object : ExpressionCheckers() {
                override val functionCallCheckers = setOf(
                    OpenApiRouteCallReader(
                        session = session,
                        onRoutingCall = routeElements::add,
                        onSchemaReference = schemas::put, // TODO
                        onContentNegotiation = { defaultContentType = it.value }
                    ),
                )
            }
    }

}

val KtSourceElement.range: IntRange get() =
    startOffset..endOffset

class OpenApiRouteCallReader(
    val session: FirSession,
    val onRoutingCall: (RouteElement) -> Unit,
    val onContentNegotiation: (ContentType) -> Unit,
    val onSchemaReference: (String, JsonSchema) -> Unit,
) : FirFunctionCallChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val sourceFile = context.getSourceFile() ?: return
        val invocation = SourceCoordinates(sourceFile, expression.source?.range ?: return)
        val functionName = expression.calleeReference.name.asString()

        when {
            expression.isRoute() -> {
                val routeFields = invocation.parseKDoc()
                val path = expression.arguments.getOrNull(0)?.resolveToString()
                for (content in routeFields.filterIsInstance<RouteField.Content>()) {
                    if (content.typeLink == null) continue
                    val typeLink = content.typeLink ?: continue
                    val coneType = resolveTypeLink(context, typeLink) ?: continue
                    if (!typeLink.hasReference()) continue

                    onSchemaReference(typeLink.name, context.schemaFromConeType(coneType))
                }

                onRoutingCall(RouteElement.Route(
                    functionName,
                    routeFields,
                    invocation,
                    path,
                ))
            }
            expression.isCallRespond() -> {
                val coneType = expression.arguments.getOrNull(0)?.resolvedType ?: return
                val className = coneType.classId?.shortClassName?.asString() ?: return

                // TODO handle this better
                if (className == "HttpStatusCode") return

                onSchemaReference(className, context.schemaFromConeType(coneType))

                // TODO optionals, arrays
                onRoutingCall(RouteElement.CallFeature(
                    functionName,
                    listOf(RouteField.Response(code = "200", typeLink = getTypeLink(className))),
                    invocation
                ))
            }
            expression.isCallReceive() -> {
                val coneType = expression.resolvedType
                val className = coneType.classId?.shortClassName?.asString() ?: return

                onSchemaReference(className, context.schemaFromConeType(coneType))

                // TODO optionals, arrays
                onRoutingCall(RouteElement.CallFeature(
                    functionName,
                    listOf(RouteField.Body(typeLink = getTypeLink(className))),
                    invocation
                ))
            }
            expression.isParameterGet() -> {
                val key = expression.arguments.getOrNull(0)?.resolveToString() ?: return

                onRoutingCall(RouteElement.CallFeature(
                    functionName,
                    listOf(RouteField.PathParam(key)),
                    invocation,
                ))

            }
            expression.isQueryParameterGet() -> {
                val key = expression.arguments.getOrNull(0)?.resolveToString() ?: return

                onRoutingCall(RouteElement.CallFeature(
                    functionName,
                    listOf(RouteField.QueryParam(key)),
                    invocation,
                ))
            }
            expression.isCustomExtension(session) -> {
                val body = expression.calleeReference.getCoordinates() ?: return
                val routeFields = invocation.parseKDoc() + body.parseKDoc()

                onRoutingCall(RouteElement.Extension(
                    functionName,
                    routeFields,
                    invocation,
                    body,
                ))
            }
            expression.isInstallContentNegotiation() -> {
                val body = expression.arguments.lastOrNull()?.source?.text ?: return

                onContentNegotiation(classifyContentNegotiationBody(body))
            }
        }
    }

    // TODO only supports string literals atm, should handle constants and possibly trace up the stack for variables
    private fun FirExpression.resolveToString(): String? =
        when(this) {
            is FirLiteralExpression -> this.value?.toString()
            else -> null
        }

    private fun CheckerContext.getSourceFile(): SourceFile? {
        return SourceFile(
            containingFilePath ?: return null,
            containingFile?.source?.text ?: return null,
        )
    }

    /**
     * Resolved the source file and text range of a reference.
     */
    private fun FirNamedReference.getCoordinates(): SourceCoordinates? {
        val resolvedFunctionSymbol = resolved?.toResolvedFunctionSymbol() ?: return null
        val containingFile = session.firProvider.getFirCallableContainerFile(resolvedFunctionSymbol) ?: return null
        val filePath = containingFile.sourceFile?.path ?: return null
        val fileText = containingFile.source?.text ?: return null
        val range = resolvedFunctionSymbol.source?.range ?: return null

        return SourceCoordinates(
            file = SourceFile(
                path = filePath,
                text = fileText,
            ),
            range = range,
        )
    }
}

object OpenApiKtorRouting {
    const val ROUTING_PACKAGE = "io.ktor.server.routing"
    const val ROUTE_CLASS = "Route"
    const val ROUTE = "route"
    const val GET = "get"
    const val POST = "post"
    const val PUT = "put"
    const val DELETE = "delete"
    const val HEAD = "head"
    const val OPTIONS = "options"
    const val PATCH = "patch"

    val HTTP_METHODS = setOf(GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH)
    val ROUTING_FUNCTION_NAMES = HTTP_METHODS + ROUTE

    /**
     * Checks if the function is one of the standard Ktor routing functions.
     */
    fun FirFunctionCall.isRoute(): Boolean = with(calleeReference) {
        symbol?.packageFqName()?.asString() == ROUTING_PACKAGE &&
                name.asString() in ROUTING_FUNCTION_NAMES
    }

    /**
     * Checks if this is a reference to `call.respond()`
     */
    fun FirFunctionCall.isCallRespond(): Boolean =
        calleeReference.name.asString() == "respond" &&
            calleeReference.symbol?.packageFqName()?.asString()?.startsWith("io.ktor.server.response") == true &&
            extensionReceiver?.source?.text == "call"

    /**
     * Checks if this is a reference to `call.receive()`
     */
    fun FirFunctionCall.isCallReceive(): Boolean =
        calleeReference.name.asString() == "receive" &&
                calleeReference.symbol?.packageFqName()?.asString()?.startsWith("io.ktor.server.request") == true &&
                extensionReceiver?.source?.text == "call"


    /**
     * Checks if this is a reference to `call.parameters["key"]`
     */
    fun FirFunctionCall.isParameterGet(): Boolean =
        calleeReference.name.asString() == "get" &&
                explicitReceiver?.source?.text == "call.parameters"


    /**
     * Checks if this is a reference to `call.queryParameters["key"]`
     */
    fun FirFunctionCall.isQueryParameterGet(): Boolean =
        calleeReference.name.asString() == "get" &&
                explicitReceiver?.source?.text == "call.queryParameters"

    /**
     * Checks if the function has `io.ktor.server.routing.Route` as its receiver type.
     */
    fun FirFunctionCall.isCustomExtension(session: FirSession): Boolean {
        val receiverFqName = extensionReceiver?.resolvedType
            ?.fullyExpandedClassId(session)
            ?.asFqNameString()
        return receiverFqName == "$ROUTING_PACKAGE.$ROUTE_CLASS"
    }

}

object OpenApiKtorSchema {
    const val INSTALL = "install"
    const val CONTENT_NEGOTIATION = "ContentNegotiation"
    val JSON_LIKE_CALLS = listOf("json", "jackson", "gson")

    fun FirFunctionCall.isInstallContentNegotiation() =
        calleeReference.name.asString() == INSTALL &&
                arguments.firstOrNull()?.source.text == CONTENT_NEGOTIATION

    fun classifyContentNegotiationBody(body: CharSequence): ContentType =
        when {
            JSON_LIKE_CALLS.any { it in body } -> ContentType.JSON
            else -> ContentType.OTHER
        }
}

enum class ContentType(val value: String) {
    JSON("application/json"),
    XML("application/xml"),
    YAML("application/yaml"),
    PROTOBUF("application/x-protobuf"),
    OTHER("application/octet-stream")
}