package io.ktor.openapi

import io.ktor.openapi.OpenApiKtorRouting.isCustomRoutingCall
import io.ktor.openapi.OpenApiKtorRouting.isKtorRoutingCall
import io.ktor.openapi.model.RouteKDocParam
import io.ktor.openapi.model.RouteKDocParam.Companion.toSpecObject
import io.ktor.openapi.model.SpecInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
import org.jetbrains.kotlin.fir.types.resolvedType
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
                    OpenApiRouteCallVisitor(session, routingCalls::add),
                )
            }
    }

    fun isEmpty() = routingCalls.isEmpty()

    fun saveSpecification(json: Json) {
        // skip if there are no paths
        if (isEmpty()) return

        val actualCalls = routingCalls
            .mergeNested()
            .filterIsInstance<RoutingCall.Ktor>()
            .groupBy { it.path }

        val paths = buildJsonObject {
            for ((path, calls) in actualCalls) {
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
            if (parent != null && !parent.exists())
                Files.createDirectories(parent)
        }
        val jsonString = json.encodeToString(openApiSpec)

        outputFile.writeText(jsonString)
    }

    /**
     * Merges nested routing calls into a single call by merging their paths and parameters.
     */
    fun List<RoutingCall>.mergeNested(): List<RoutingCall> {
        val childParentMap = mutableMapOf<RoutingCall, RoutingCall>()

        // because the source tree is traversed top-down, we assume calls are ordered
        for (i in 0 ..< lastIndex) {
            when(val current = get(i)) {
                // only subsequent invocations can be children
                is RoutingCall.Ktor -> {
                    for (j in i + 1 ..< size) {
                        if (get(j) in current)
                            childParentMap[get(j)] = current
                        else break
                    }
                }
                // body can occur anywhere, so we need to check the whole array
                is RoutingCall.Custom -> {
                    for (other in this) {
                        if (other in current) {
                            // keep the most immediate parent
                            childParentMap.compute(other) { _, oldParent ->
                                when(oldParent?.bodyOffset) {
                                    null, in 0..current.bodyOffset -> current
                                    else -> oldParent
                                }
                            }
                            childParentMap[other] = current
                        }
                    }
                }
            }
        }
        val parents = childParentMap.values.toSet()
        return mapNotNull { route ->
            when(route) {
                in parents, !is RoutingCall.Ktor -> null
                !in childParentMap -> route
                else -> {
                    val ancestry = sequence {
                        var current: RoutingCall? = route
                        while (current != null) {
                            yield(current)
                            current = childParentMap[current]
                        }
                    }
                    route.copy(
                        path = ancestry.toList().reversed()
                            .filterIsInstance<RoutingCall.Ktor>()
                            .mapNotNull { it.path?.takeIf(String::isNotEmpty) }
                            .joinToString("/")
                            .replace("//", "/"),
                    )
                }
            }
        }
    }

}

sealed interface RoutingCall {
    val call: FirFunctionCall
    val invocation: SourceCoordinates
    val body: SourceCoordinates

    val functionName: String get() =
        call.calleeReference.name.asString()

    val kDocParameters: List<RouteKDocParam> get() {
        val startOffset = call.source?.startOffset ?: return emptyList()
        return parsePrecedingComment(invocation.file.text, startOffset)
    }

    val bodyOffset: Int get() = body.range.first

    operator fun contains(other: RoutingCall): Boolean =
        other != this && other.invocation in body

    data class Ktor(
        override val call: FirFunctionCall,
        override val invocation: SourceCoordinates,
        val path: String?,
    ): RoutingCall {
        override val body: SourceCoordinates get() = invocation

        val method: String? get() =
            functionName.takeIf { it in OpenApiKtorRouting.HTTP_METHODS }

        override fun toString(): String =
            path?.let { "$functionName $it" } ?: functionName

        override fun equals(other: Any?): Boolean =
            other is Ktor && invocation == other.invocation

        override fun hashCode(): Int =
            invocation.hashCode()
    }

    data class Custom(
        override val call: FirFunctionCall,
        override val invocation: SourceCoordinates,
        override val body: SourceCoordinates,
    ): RoutingCall {
        override fun toString(): String = functionName

        override fun equals(other: Any?): Boolean =
            other is Custom && invocation == other.invocation

        override fun hashCode(): Int =
            invocation.hashCode()
    }
}

data class SourceCoordinates(
    val file: SourceFile,
    val range: IntRange,
) {
    operator fun contains(other: SourceCoordinates) =
        file.path == other.file.path &&
                other.range.first in range
}

data class SourceFile(
    val path: String,
    val text: CharSequence,
)

val KtSourceElement.range: IntRange get() =
    startOffset..endOffset

class OpenApiRouteCallVisitor(
    val session: FirSession,
    val operation: (RoutingCall) -> Unit
) : FirFunctionCallChecker(MppCheckerKind.Common) {

    override fun check(
        expression: FirFunctionCall,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        when {
            expression.isKtorRoutingCall() -> {
                val sourceFile = context.getSourceFile() ?: return
                val coordinates = SourceCoordinates(sourceFile, expression.source?.range ?: return)
                val path = expression.arguments.getOrNull(0)?.resolveToString()

                operation(RoutingCall.Ktor(
                    expression,
                    coordinates,
                    path,
                ))
            }
            expression.isCustomRoutingCall(session) -> {
                val sourceFile = context.getSourceFile() ?: return
                val invocation = SourceCoordinates(sourceFile, expression.source?.range ?: return)
                val body = expression.calleeReference.getCoordinates() ?: return

                operation(RoutingCall.Custom(
                    expression,
                    invocation,
                    body
                ))
            }
        }
    }

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
    fun FirFunctionCall.isKtorRoutingCall(): Boolean = with(calleeReference) {
        symbol?.packageFqName()?.asString() == ROUTING_PACKAGE &&
                name.asString() in ROUTING_FUNCTION_NAMES
    }

    /**
     * Checks if the function has `io.ktor.server.routing.Route` as its receiver type.
     */
    fun FirFunctionCall.isCustomRoutingCall(session: FirSession): Boolean {
        val receiverFqName = extensionReceiver?.resolvedType
            ?.fullyExpandedClassId(session)
            ?.asFqNameString()
        return receiverFqName == "$ROUTING_PACKAGE.$ROUTE_CLASS"
    }

}