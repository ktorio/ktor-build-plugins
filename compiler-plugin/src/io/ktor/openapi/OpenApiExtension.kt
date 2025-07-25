package io.ktor.openapi

import io.ktor.openapi.OpenApiKtorRouting.isRoute
import io.ktor.openapi.model.RouteKDocParam
import io.ktor.openapi.model.RouteKDocParam.Companion.toSpecObject
import io.ktor.openapi.model.SpecInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.text
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.writeText

class OpenApiExtension(
    private val config: OpenApiProcessorConfig,
) : FirExtensionRegistrar() {

    private val routingCalls = mutableListOf<RoutingCall>()

    override fun ExtensionRegistrarContext.configurePlugin() {
        +::OpenApiFirAdditionalChecksExtension
    }

    private inner class OpenApiFirAdditionalChecksExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
        override val expressionCheckers: ExpressionCheckers
            get() = object : ExpressionCheckers() {
                override val functionCallCheckers = setOf(
                    OpenApiFirCallPathInfoAnalyzer(routingCalls::add)
                )
            }
    }

    fun isEmpty() = routingCalls.isEmpty()

    fun saveSpecification(json: Json) {
        // skip if there are no paths
        if (routingCalls.isEmpty()) return

        val paths = buildJsonObject {
            for ((path, calls) in routingCalls.groupBy { it.path }) {
                putJsonObject(path ?: continue) {
                    for (call in calls) {
                        put(
                            call.method ?: continue,
                            JsonObject(call.kDocParameters.toSpecObject())
                        )
                    }
                }
            }
        }

        val openApiSpec = buildJsonObject {
            put("openapi", "3.1.1")
            put("info", json.encodeToJsonElement<SpecInfo>(config.info))
            put("paths", JsonObject(paths))
        }
        val outputFile = Paths.get(config.outputFile).apply {
            if (parent != null && !parent.exists()) Files.createDirectories(parent)
        }
        val jsonString = json.encodeToString(openApiSpec)

        outputFile.writeText(jsonString)
    }

}

class RoutingCall(
    val call: FirFunctionCall,
    val sourceText: CharSequence,
) {
    val path get(): String? =
        call.arguments.getOrNull(0)?.resolveToString()

    val method get(): String? =
        call.calleeReference.name.asString()
            .takeIf { it in OpenApiKtorRouting.HTTP_METHODS }

    val kDocParameters get(): List<RouteKDocParam> {
        val startOffset = call.source?.startOffset ?: return emptyList()
        return parsePrecedingComment(sourceText, startOffset)
    }

    private fun FirExpression.resolveToString(): String? =
        when(this) {
            is FirLiteralExpression -> this.value?.toString()
            else -> null
        }
}

class OpenApiFirCallPathInfoAnalyzer(
    val operation: (RoutingCall) -> Unit
) : FirFunctionCallChecker(MppCheckerKind.Common) {

    override fun check(
        expression: FirFunctionCall,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        if (expression.isRoute()) {
            val sourceText = context.containingFile?.source?.text ?: return
            operation(RoutingCall(expression, sourceText))
        }
    }
}

object OpenApiKtorRouting {
    const val ROUTING_PACKAGE = "io.ktor.server.routing"
    const val ROUTE = "route"
    const val GET = "get"
    const val POST = "post"
    const val PUT = "put"
    const val DELETE = "delete"
    const val HEAD = "head"
    const val OPTIONS = "options"
    const val PATCH = "patch"

    val HTTP_METHODS = setOf(GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH)

    fun FirFunctionCall.isRoute(): Boolean =
        inRoutingPackage() && when(calleeReference.name.asString()) {
            ROUTE, GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH -> true
            else -> false
        }

    fun FirFunctionCall.inRoutingPackage() =
        calleeReference.symbol?.packageFqName()?.asString() == ROUTING_PACKAGE

}