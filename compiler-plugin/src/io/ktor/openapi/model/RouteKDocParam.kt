package io.ktor.openapi.model

/**
 * Sealed class representing different KDoc parameters for OpenAPI documentation.
 */
sealed interface RouteKDocParam {

    sealed interface Content : RouteKDocParam {
        val type: String?
        val description: String?
    }

    class Summary(val text: String) : RouteKDocParam

    /**
     * Associates the endpoint with a tag for grouping.
     *
     * Format: `@tag [TagName]`
     */
    class Tag(val name: String) : RouteKDocParam

    /**
     * Describes a path or query parameter.
     *
     * Format: `@param name description`
     */
    class Param(val name: String, val description: String) : RouteKDocParam

    /**
     * Describes a header parameter.
     *
     * Format: `@header name description`
     */
    class Header(val name: String, val description: String) : RouteKDocParam

    /**
     * Describes a cookie parameter.
     *
     * Format: `@cookie name description`
     */
    class Cookie(val name: String, val description: String) : RouteKDocParam

    /**
     * Documents the request body type.
     *
     * Format: `@body [Type] description`
     */
    class Body(override val type: String?, override val description: String) : Content

    /**
     * Documents a response code with optional type and description.
     *
     * Format: `@response code [Type] description`
     */
    class Response(val code: String, override val type: String?, override val description: String) : Content

    /**
     * Marks an endpoint as deprecated.
     *
     * Format: `@deprecated reason`
     */
    class Deprecated(val reason: String) : RouteKDocParam

    /**
     * Provides a detailed endpoint description.
     *
     * Format: `@description text`
     */
    class Description(val text: String) : RouteKDocParam

    /**
     * Documents security requirements.
     *
     * Format: `@security scheme`
     */
    class Security(val scheme: String) : RouteKDocParam
}