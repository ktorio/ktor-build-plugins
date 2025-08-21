package io.ktor.openapi.routing

import io.ktor.compiler.utils.getArgument
import io.ktor.compiler.utils.getFunctionName
import io.ktor.compiler.utils.range
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression

/**
 * Code references to the Ktor routing API.  These recorded, then merged to form the model of the OpenAPI specification.
 */
sealed interface RouteNode: SourceCoordinates {
    val fir: FirFunctionCall
    val fields: RouteStack.() -> RouteFieldList

    val id: String get() = filePath.orEmpty() + startOffset.toString()
    val functionName: String get() = fir.getFunctionName()
    override val startOffset: Int get() = fir.source?.range?.first ?: -1
    override val endOffset: Int get() = fir.source?.range?.last ?: -1

    /**
     * Route call with method and path.
     *
     * These map to paths in the OpenAPI specification.
     */
    class Route(
        override val filePath: String?,
        override val fir: FirFunctionCall,
        override val fields: RouteStack.() -> RouteFieldList,
    ): RouteNode {
        val method: String? get() =
            functionName.takeIf { it in RoutingFunctionConstants.HTTP_METHODS }

        override fun toString(): String = buildString {
            append(fir.getFunctionName())
            (fir.getArgument("path") as? FirLiteralExpression)?.let {
                append(" ")
                append(it.value.toString())
            }
        }

        override fun equals(other: Any?): Boolean =
            other is Route && id == other.id

        override fun hashCode(): Int = id.hashCode()
    }

    /**
     * A general call to some Ktor feature that generates automatic OpenAPI properties.
     */
    class CallFeature(
        override val filePath: String?,
        override val fir: FirFunctionCall,
        override val fields: RouteStack.() -> RouteFieldList,
    ) : RouteNode {
        override fun equals(other: Any?): Boolean =
            other is CallFeature && id == other.id

        override fun hashCode(): Int = id.hashCode()
    }

    /**
     * Custom extension function of `Route`.
     *
     * Since it's a reference to a function, it may have non-local children.
     */
    class Function(
        override val filePath: String?,
        override val fir: FirFunctionCall,
        val declaration: SourceCoordinates,
        override val fields: RouteStack.() -> RouteFieldList,
    ): RouteNode {
        override fun contains(other: SourceCoordinates): Boolean =
            other in declaration && other != this

        override fun toString(): String =
            fir.getFunctionName() + '@' + fir.source?.range

        override fun equals(other: Any?): Boolean =
            other is Function && id == other.id

        override fun hashCode(): Int = id.hashCode()
    }
}