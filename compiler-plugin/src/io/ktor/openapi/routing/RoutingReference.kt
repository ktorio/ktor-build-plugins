package io.ktor.openapi.routing

/**
 * Code references to the Ktor routing API.  These recorded, then merged to form the model of the OpenAPI specification.
 */
sealed interface RoutingReference {
    val functionName: String
    val parameters: RouteFieldList
    val coordinates: SourceCoordinates

    operator fun contains(other: RoutingReference): Boolean =
        other.coordinates in coordinates

    /**
     * Route call with method and path.
     *
     * These map to paths in the OpenAPI specification.
     */
    data class RouteCall(
        override val functionName: String,
        override val parameters: RouteFieldList,
        override val coordinates: SourceCoordinates,
        val path: String?,
    ): RoutingReference {
        val method: String? get() =
            functionName.takeIf { it in RoutingFunctionConstants.HTTP_METHODS }

        override fun toString(): String =
            path?.let { "$functionName $it" } ?: functionName

        override fun equals(other: Any?): Boolean =
            other is RouteCall && coordinates == other.coordinates

        override fun hashCode(): Int =
            coordinates.hashCode()
    }

    /**
     * A general call to some Ktor feature that generates automatic OpenAPI properties.
     */
    data class CallExpression(
        override val functionName: String,
        override val parameters: RouteFieldList,
        override val coordinates: SourceCoordinates,
    ) : RoutingReference {
        override fun toString(): String = functionName

        override fun equals(other: Any?): Boolean =
            other is CallExpression && coordinates == other.coordinates

        override fun hashCode(): Int = coordinates.hashCode()
    }

    /**
     * Custom extension function of `Route`.
     *
     * Since it's a reference to a function, it may have non-local children.
     */
    data class ExtensionFunction(
        override val functionName: String,
        override val parameters: RouteFieldList,
        override val coordinates: SourceCoordinates,
    ): RoutingReference {
        override fun toString(): String = functionName

        override fun equals(other: Any?): Boolean =
            other is ExtensionFunction && coordinates == other.coordinates

        override fun hashCode(): Int = coordinates.hashCode()
    }
}

data class SourceCoordinates(
    val invocation: SourceRange,
    val body: SourceRange? = invocation,
) {
    operator fun contains(other: SourceCoordinates) =
        other != this && body != null && other.invocation in body
}

data class SourceRange(
    val file: SourceFile,
    val range: IntRange,
) {
    fun asCoordinates() = SourceCoordinates(this)

    operator fun contains(other: SourceRange) =
        file.path == other.file.path &&
                other.range.first in range
}

data class SourceFile(
    val path: String,
    val text: CharSequence,
)