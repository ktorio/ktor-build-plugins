package io.ktor.openapi

import io.ktor.openapi.OpenApiCodeAnalysis.asPathItem
import io.ktor.openapi.OpenApiCodeAnalysis.isRoute
import io.ktor.openapi.OpenApiCodeAnalysis.pathArgument
import io.ktor.openapi.model.RouteKDocParam.Companion.toSpecObject
import io.ktor.openapi.OpenApiVisitor.Companion.DELETE
import io.ktor.openapi.OpenApiVisitor.Companion.GET
import io.ktor.openapi.OpenApiVisitor.Companion.HEAD
import io.ktor.openapi.OpenApiVisitor.Companion.HTTP_METHODS
import io.ktor.openapi.OpenApiVisitor.Companion.OPTIONS
import io.ktor.openapi.OpenApiVisitor.Companion.PATCH
import io.ktor.openapi.OpenApiVisitor.Companion.POST
import io.ktor.openapi.OpenApiVisitor.Companion.PUT
import io.ktor.openapi.OpenApiVisitor.Companion.ROUTE
import io.ktor.openapi.OpenApiVisitor.Companion.ROUTING_PACKAGE
import io.ktor.openapi.model.SpecInfo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.put
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.getCompilerMessageLocation
import org.jetbrains.kotlin.backend.jvm.ir.getKtFile
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists

@Deprecated("Using FIR now")
class OpenAPIKDocProcessor(val config: OpenApiProcessorConfig): IrGenerationExtension {
    @OptIn(ExperimentalSerializationApi::class)
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        val paths = mutableMapOf<String, JsonObject>()

        moduleFragment.accept(
            visitor = OpenApiVisitor(pluginContext) { trace ->
                val routeCalls = trace.elements.asSequence()
                    .filterIsInstance<IrCall>()
                    .filter { it.isRoute() }
                    .toList()
                val path = routeCalls.mapNotNull { it.pathArgument() }
                    .joinToString("/")
                    .replace("//", "/")

                try {
                    paths[path] = trace.asPathItem()
                } catch (e: Exception) {
                    pluginContext.messageCollector.report(
                        CompilerMessageSeverity.ERROR,
                        e.message ?: e.stackTraceToString(),
                        trace.file?.let { trace.last().getCompilerMessageLocation(it) }
                    )
                }
            },
            data = IrTrace(emptyList())
        )

        if (paths.isEmpty()) return

        pluginContext.messageCollector.report(
            CompilerMessageSeverity.INFO,
            "Discovered ${paths.size} paths; writing to swagger file ${config.outputFile}"
        )
        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "    "
        }
        val model = buildJsonObject {
            put("openapi", "3.1.1")
            put("info", json.encodeToJsonElement(config.info))
            put("paths", JsonObject(paths))
        }
        val outputFile = Paths.get(config.outputFile)
        if (!outputFile.parent.exists()) {
            Files.createDirectories(outputFile.parent)
        }
        Files.newOutputStream(outputFile).use { outputStream ->
            json.encodeToStream(
                model,
                outputStream
            )
        }
    }
}

data class OpenApiProcessorConfig(
    val enabled: Boolean,
    val outputFile: String,
    val mainClass: String? = null,
    val info: SpecInfo = SpecInfo("Open API Document", "1.0.0"),
)

data class IrTrace(
    val elements: List<IrElement>
) {
    val file: IrFile? get() = elements.asSequence()
        .filterIsInstance<IrFile>()
        .firstOrNull()

    operator fun plus(element: IrElement) = IrTrace(elements + element)

    fun last() = elements.last()

    fun size() = elements.size

    override fun toString(): String {
        return super.toString()
    }
}

class OpenApiVisitor(
    val pluginContext: IrPluginContext,
    val onRouteTrace: (IrTrace) -> Unit,
) : IrVisitor<Unit, IrTrace>(), IrPluginContext by pluginContext {
    companion object {
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
    }

    override fun visitElement(element: IrElement, data: IrTrace) {
        element.acceptChildren(this, data + element)
    }

    override fun visitCall(expression: IrCall, data: IrTrace) {
        if (expression.isRoute()) {
            onRouteTrace(data + expression)
        }
        expression.acceptChildren(this, data + expression)
    }

}

object OpenApiCodeAnalysis {

    fun IrCall.isRoute(): Boolean =
        inRoutingPackage() && when(symbol.owner.name.asString()) {
            ROUTE, GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH -> true
            else -> false
        }

    fun IrCall.inRoutingPackage() =
        symbol.owner.parent.kotlinFqName.asString() == ROUTING_PACKAGE

    fun IrCall.pathArgument(): String? =
        arguments.getOrNull(1)?.resolveToString()

    fun IrExpression.resolveToString(): String? =
        when(this) {
            is IrConst -> value as? String
            else -> null
        }

    fun IrTrace.asPathItem(): JsonObject {
        val lastCall = elements.last() as? IrCall
            ?: error("Missing route call for trace")
        val method = lastCall.symbol.owner.name.asString().takeIf { it in HTTP_METHODS }
            ?: error("Bad function for route: ${lastCall.symbol.owner.name}")
        val sourceFileContents = file?.getKtFile()?.text
            ?: error("Missing source file for route")
        val parameters = parsePrecedingComment(sourceFileContents, lastCall.startOffset)

        return buildJsonObject {
            put(method, JsonObject(parameters.toSpecObject()))
        }
    }


}