package io.ktor.openapi.model

import io.ktor.openapi.*

sealed interface RoutingCall {
    val functionName: String
    val parameters: List<RouteKDocParam>
    val invocation: SourceCoordinates
    val body: SourceCoordinates

    operator fun contains(other: RoutingCall): Boolean =
        other != this && other.invocation in body

    data class Ktor(
        override val functionName: String,
        override val parameters: List<RouteKDocParam>,
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
        override val functionName: String,
        override val parameters: List<RouteKDocParam>,
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