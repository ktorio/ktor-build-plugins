package io.ktor.openapi.model

import io.ktor.openapi.*

sealed interface RouteElement {
    val functionName: String
    val parameters: RouteFieldList
    val invocation: SourceCoordinates
    val body: SourceCoordinates? get() = null

    operator fun contains(other: RouteElement): Boolean =
        body != null && other != this && other.invocation in body!!

    /**
     * Route call with method and path.
     *
     * These map to paths in the OpenAPI specification.
     */
    data class Route(
        override val functionName: String,
        override val parameters: RouteFieldList,
        override val invocation: SourceCoordinates,
        val path: String?,
    ): RouteElement {
        override val body: SourceCoordinates get() = invocation

        val method: String? get() =
            functionName.takeIf { it in OpenApiKtorRouting.HTTP_METHODS }

        override fun toString(): String =
            path?.let { "$functionName $it" } ?: functionName

        override fun equals(other: Any?): Boolean =
            other is Route && invocation == other.invocation

        override fun hashCode(): Int =
            invocation.hashCode()
    }

    /**
     * A general call to some Ktor feature that generates automatic OpenAPI properties.
     */
    data class CallFeature(
        override val functionName: String,
        override val parameters: RouteFieldList,
        override val invocation: SourceCoordinates,
    ) : RouteElement {
        override fun toString(): String = functionName

        override fun equals(other: Any?): Boolean =
            other is Extension && invocation == other.invocation

        override fun hashCode(): Int = invocation.hashCode()
    }

    /**
     * Custom extension function of `Route`.
     *
     * Since it's a reference to a function, it may have non-local children.
     */
    data class Extension(
        override val functionName: String,
        override val parameters: RouteFieldList,
        override val invocation: SourceCoordinates,
        override val body: SourceCoordinates,
    ): RouteElement {
        override fun toString(): String = functionName

        override fun equals(other: Any?): Boolean =
            other is Extension && invocation == other.invocation

        override fun hashCode(): Int = invocation.hashCode()
    }
}