package io.ktor.openapi.model

import io.ktor.openapi.append
import io.ktor.openapi.appendObject
import io.ktor.openapi.jsonObject
import io.ktor.openapi.put
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Sealed class representing different KDoc parameters for OpenAPI documentation.
 */
sealed interface RouteKDocParam {
    companion object {
        val schemaArg = Regex("^\\[.*]$")

        fun parse(text: String): RouteKDocParam {
            if (!text.startsWith('@'))
                return Summary(text)
            var i = 0
            val words = text.trim().removePrefix("@").split(" ")
            val next = { words[i++] }
            val maybeNext: ((String) -> Boolean) -> String? = { predicate ->
                words.getOrNull(i)?.takeIf(predicate)?.also { i++ }
            }
            val remaining = { words.drop(i).joinToString(" ").trim() }
            return when (next()) {
                "tag" -> Tag(next())
                "param" -> Param(next(), remaining())
                "header" -> Header(next(), remaining())
                "cookie" -> Cookie(next(), remaining())
                "body" -> Body(maybeNext { it.matches(schemaArg) }, remaining())
                "response" -> Response(next(), maybeNext { it.matches(schemaArg) }, remaining())
                "deprecated" -> Deprecated(remaining())
                else -> throw IllegalArgumentException("Invalid KDoc parameter: $text")
            }
        }

        fun List<RouteKDocParam>.toSpecObject(): Map<String, JsonElement> = buildMap {
            for (param in this@toSpecObject) {
                when(param) {
                    is Summary -> put("summary", param.text)
                    is Description -> put("description", param.text)
                    is Body -> {
                        put("requestBody", param.jsonObject())
                    }
                    is Cookie -> {
                        append("parameters", buildJsonObject {
                            put("name", param.name)
                            put("in", "cookie")
                            put("description", param.description)
                            put("required", true)
                        })
                    }
                    is Deprecated -> {
                        put("deprecated", JsonPrimitive(true))
                    }
                    is Header -> {
                        append("parameters", buildJsonObject {
                            put("name", param.name)
                            put("in", "header")
                            put("description", param.description)
                            put("required", false)
                        })
                    }
                    is Param -> {
                        append("parameters", buildJsonObject {
                            put("name", param.name)
                            put("in", "path") // Default to path, could be refined based on route analysis
                            put("description", param.description)
                            put("required", true)
                        })
                    }
                    is Response -> {
                        appendObject("responses", param.code, param.jsonObject())
                    }
                    is Security -> {
                        append("security", buildJsonObject {
                            putJsonArray(param.scheme) {}
                        })
                    }
                    is Tag -> {
                        append("tags", param.name)
                    }
                }
            }
        }
    }

    sealed interface Data : RouteKDocParam {
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
    class Body(override val type: String?, override val description: String) : Data

    /**
     * Documents a response code with optional type and description.
     *
     * Format: `@response code [Type] description`
     */
    class Response(val code: String, override val type: String?, override val description: String) : Data

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