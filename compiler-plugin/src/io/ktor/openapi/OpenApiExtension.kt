package io.ktor.openapi

import io.ktor.openapi.model.*
import io.ktor.openapi.routing.CallReceiveInterpreter
import io.ktor.openapi.routing.CallRespondInterpreter
import io.ktor.openapi.routing.ContentNegotiationInterpreter
import io.ktor.openapi.routing.ContentType
import io.ktor.openapi.routing.CustomExtensionInterpreter
import io.ktor.openapi.routing.ParameterGetInterpreter
import io.ktor.openapi.routing.QueryParameterGetInterpreter
import io.ktor.openapi.routing.EndpointInterpreter
import io.ktor.openapi.routing.RoutingReference
import io.ktor.openapi.routing.RoutingCallInterpreter
import io.ktor.openapi.routing.RoutingReferenceResult
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.writeText

class OpenApiExtension(
    private val config: OpenApiProcessorConfig,
) : FirExtensionRegistrar() {

    private val routingReferences = mutableListOf<RoutingReference>()
    private val schemas = mutableMapOf<String, JsonSchema>()
    private var defaultContentType: String = ContentType.OTHER.value

    private val routeCallReader = object: OpenApiRouteCallReader() {
        override fun onRoutingReference(reference: RoutingReference) {
            routingReferences.add(reference)
        }
        override fun onSchemaReference(name: String, schema: JsonSchema) {
            schemas[name] = schema
        }
        override fun onContentNegotiation(contentType: ContentType) {
            defaultContentType = contentType.value
        }
    }

    override fun ExtensionRegistrarContext.configurePlugin() {
        +::OpenApiFirAdditionalChecksExtension
    }

    fun isEmpty() = routingReferences.isEmpty()

    fun saveSpecification(json: Json) {
        // skip if there are no paths
        if (isEmpty()) return

        val outputFile = Paths.get(config.outputFile).apply {
            if (parent != null && !parent.exists())
                Files.createDirectories(parent)
        }
        val openApiSpec = OpenApiSpecGenerator.buildSpecification(
            config.info,
            routingReferences,
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
                override val functionCallCheckers = setOf(routeCallReader)
            }
    }

}

abstract class OpenApiRouteCallReader(
    private val adapters: List<RoutingCallInterpreter> = listOf(
        EndpointInterpreter(),
        CallRespondInterpreter(),
        CallReceiveInterpreter(),
        ParameterGetInterpreter(),
        QueryParameterGetInterpreter(),
        CustomExtensionInterpreter(),
        ContentNegotiationInterpreter()
    )
) : FirFunctionCallChecker(MppCheckerKind.Common) {
    abstract fun onRoutingReference(reference: RoutingReference)
    abstract fun onSchemaReference(name: String, schema: JsonSchema)
    abstract fun onContentNegotiation(contentType: ContentType)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        for (adapter in adapters) {
            when(val result = adapter.check(expression)) {
                is RoutingReferenceResult.None -> continue
                is RoutingReferenceResult.Match -> {
                    onRoutingReference(result.call)
                    result.schema.forEach { (name, schema) ->
                        onSchemaReference(name, schema)
                    }
                }
                is RoutingReferenceResult.ContentType ->
                    onContentNegotiation(result.contentType)
            }
            // when match is found, skip other adapters
            return
        }
    }
}