package io.ktor.openapi.model

/**
 * Sealed class representing different KDoc parameters for OpenAPI documentation.
 */
sealed interface KDocField {

    sealed interface SchemaHolder : KDocField {
        val typeRef: String?
    }

    sealed interface Content : SchemaHolder {
        val contentType: String?
        val description: String?
    }

    sealed interface Parameter : SchemaHolder {
        val name: String
        val description: String?
        val `in`: String
    }

    class Summary(val text: String) : KDocField

    /**
     * Associates the endpoint with a tag for grouping.
     *
     * Format: `@tag [TagName]`
     */
    class Tag(val name: String) : KDocField

    /**
     * Describes a path parameter.
     *
     * Format: `@path name description`
     */
    class PathParam(override val name: String, override val typeRef: String?, override val description: String?) : Parameter {
        override val `in`: String = "path"
    }

    /**
     * Describes a query parameter.
     *
     * Format: `@query name description`
     */
    class QueryParam(override val name: String, override val typeRef: String?, override val description: String?) : Parameter {
        override val `in`: String = "query"
    }

    /**
     * Describes a header parameter.
     *
     * Format: `@header name description`
     */
    class Header(override val name: String, override val typeRef: String?, override val description: String?) : Parameter {
        override val `in`: String = "header"
    }

    /**
     * Describes a cookie parameter.
     *
     * Format: `@cookie name description`
     */
    class Cookie(override val name: String, override val typeRef: String?, override val description: String?) : Parameter {
        override val `in`: String = "cookie"
    }

    /**
     * Documents the request body type.
     *
     * Format: `@body [Type] description`
     */
    class Body(override val contentType: String?, override val typeRef: String?, override val description: String) : Content

    /**
     * Documents a response code with optional type and description.
     *
     * Format: `@response code [Type] description`
     */
    class Response(val code: String, override val contentType: String?, override val typeRef: String?, override val description: String) : Content

    /**
     * Marks an endpoint as deprecated.
     *
     * Format: `@deprecated reason`
     */
    class Deprecated(val reason: String) : KDocField

    /**
     * Provides a detailed endpoint description.
     *
     * Format: `@description text`
     */
    class Description(val text: String) : KDocField

    /**
     * Documents security requirements.
     *
     * Format: `@security scheme`
     */
    class Security(val scheme: String) : KDocField
}