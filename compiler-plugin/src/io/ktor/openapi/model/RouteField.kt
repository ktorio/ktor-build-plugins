package io.ktor.openapi.model

/**
 * Sealed class representing different KDoc parameters for OpenAPI documentation.
 */
sealed interface RouteField {

    /**
     * Returns true if the two [RouteField] instances are mutually exclusive for a given endpoint.
     */
    fun conflictsWith(other: RouteField): Boolean =
        other::class == this::class

    sealed interface SchemaHolder : RouteField {
        val typeLink: TypeLink?
    }

    sealed interface Content : SchemaHolder {
        val contentType: String?
        val description: String?
    }

    sealed interface Parameter : SchemaHolder {
        val name: String
        val description: String?
        val `in`: String

        override fun conflictsWith(other: RouteField): Boolean =
            other::class == this::class && other is Parameter && name == other.name
    }

    /**
     * Associates the endpoint with a tag for grouping.
     *
     * Format: `@tag [TagName]`
     */
    class Tag(val name: String) : RouteField {
        override fun conflictsWith(other: RouteField): Boolean =
            other is Tag && name == other.name
    }

    /**
     * Describes a path parameter.
     *
     * Format: `@path name description`
     */
    class PathParam(
        override val name: String,
        override val typeLink: TypeLink? = null,
        override val description: String? = null,
    ) : Parameter {
        override val `in`: String = "path"
    }

    /**
     * Describes a query parameter.
     *
     * Format: `@query name description`
     */
    class QueryParam(
        override val name: String,
        override val typeLink: TypeLink? = null,
        override val description: String? = null,
    ) : Parameter {
        override val `in`: String = "query"
    }

    /**
     * Describes a header parameter.
     *
     * Format: `@header name description`
     */
    class Header(
        override val name: String,
        override val typeLink: TypeLink? = null,
        override val description: String? = null,
    ) : Parameter {
        override val `in`: String = "header"
    }

    /**
     * Describes a cookie parameter.
     *
     * Format: `@cookie name description`
     */
    class Cookie(
        override val name: String,
        override val typeLink: TypeLink? = null,
        override val description: String? = null,
    ) : Parameter {
        override val `in`: String = "cookie"
    }

    /**
     * Documents the request body type.
     *
     * Format: `@body [Type] description`
     */
    class Body(
        override val contentType: String? = null,
        override val typeLink: TypeLink? = null,
        override val description: String? = null,
    ) : Content

    /**
     * Documents a response code with optional type and description.
     *
     * Format: `@response code [Type] description`
     */
    class Response(
        val code: String,
        override val contentType: String? = null,
        override val typeLink: TypeLink? = null,
        override val description: String? = null,
    ) : Content

    /**
     * Marks an endpoint as deprecated.
     *
     * Format: `@deprecated reason`
     */
    class Deprecated(val reason: String) : RouteField

    /**
     * Provides a detailed endpoint description.
     *
     * Format: `@description text`
     */
    class Description(val text: String) : RouteField


    /**
     * Provides a summary of the endpoint.
     * Format: `@summary text`
     */
    class Summary(val text: String) : RouteField

    /**
     * Documents security requirements.
     *
     * Format: `@security scheme`
     */
    class Security(val scheme: String) : RouteField {
        override fun conflictsWith(other: RouteField): Boolean =
            other is Security && scheme == other.scheme
    }
}

typealias RouteFieldList = List<RouteField>

fun MutableList<RouteField>.takeCompatible(other: RouteFieldList): Boolean =
    addAll(other.filterNot { field ->
        any(field::conflictsWith)
    })

sealed interface TypeLink {
    val name: String

    data class Simple(override val name: String, val jsonType: JsonType) : TypeLink
    data class Reference(override val name: String) : TypeLink
    data class Array(val element: TypeLink) : TypeLink by element
    data class Optional(val delegate: TypeLink) : TypeLink by delegate
    // TODO map?
}

fun TypeLink.hasReference(): Boolean = when(this) {
    is TypeLink.Array -> element.hasReference()
    is TypeLink.Optional -> delegate.hasReference()
    is TypeLink.Reference -> true
    is TypeLink.Simple -> false
}