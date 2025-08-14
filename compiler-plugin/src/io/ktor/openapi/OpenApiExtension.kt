package io.ktor.openapi

import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.interpreters.*
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

    private lateinit var routeGraph: RouteGraph
    private var defaultContentType: String = ContentType.JSON.value
    private val securitySchemes = mutableListOf<RoutingReferenceResult.SecurityScheme>()
    private var session: FirSession? = null

    private val routeCallReader = object: OpenApiRouteCallReader() {
        override fun onRoutingReference(reference: RouteNode) {
            routeGraph.add(reference)
        }
        override fun onContentNegotiation(contentType: ContentType) {
            defaultContentType = contentType.value
        }
        override fun onSecurityScheme(security: RoutingReferenceResult.SecurityScheme) {
            securitySchemes.add(security)
        }
    }

    override fun ExtensionRegistrarContext.configurePlugin() {
        +::registerChecker
    }

    fun isEmpty() = routeGraph.isEmpty()

    fun registerChecker(session: FirSession): FirAdditionalCheckersExtension {
        this.session = session // TODO use a better mechanism here
        return OpenApiFirAdditionalChecksExtension(session)
    }

    fun saveSpecification(json: Json) {
        // skip if there are no paths
        if (isEmpty()) return

        val outputFile = Paths.get(config.outputFile).apply {
            if (parent != null && !parent.exists())
                Files.createDirectories(parent)
        }
        val openApiSpec = OpenApiSpecGenerator.buildSpecification(
            config.info,
            routeGraph.build(),
            defaultContentType,
            securitySchemes,
            json
        )
        val jsonString = json.encodeToString(openApiSpec)

        outputFile.writeText(jsonString)
    }

    private inner class OpenApiFirAdditionalChecksExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
        override val expressionCheckers: ExpressionCheckers
            get() = object : ExpressionCheckers() {
                init {
                    routeGraph = RouteGraph(session)
                }
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
        CustomFunctionInterpreter(),
        ContentNegotiationInterpreter(),
        AuthenticationInterpreter(),
        AuthenticateRouteInterpreter(),
    )
) : FirFunctionCallChecker(MppCheckerKind.Common) {
    abstract fun onRoutingReference(reference: RouteNode)
    abstract fun onContentNegotiation(contentType: ContentType)
    abstract fun onSecurityScheme(security: RoutingReferenceResult.SecurityScheme)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        for (adapter in adapters) {
            when(val result = adapter.check(expression)) {
                is RoutingReferenceResult.None -> continue
                is RoutingReferenceResult.Match ->
                    onRoutingReference(result.call)
                is RoutingReferenceResult.ContentType ->
                    onContentNegotiation(result.contentType)
                is RoutingReferenceResult.SecurityScheme ->
                    onSecurityScheme(result)
            }
            // when a match is found, skip other adapters
            return
        }
    }
}