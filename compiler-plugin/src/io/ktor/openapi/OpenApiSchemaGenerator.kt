package io.ktor.openapi

import io.ktor.openapi.model.JsonSchema
import io.ktor.openapi.model.RouteKDocParam
import io.ktor.openapi.model.RouteKDocParam.Body
import io.ktor.openapi.model.RouteKDocParam.Cookie
import io.ktor.openapi.model.RouteKDocParam.Description
import io.ktor.openapi.model.RouteKDocParam.Header
import io.ktor.openapi.model.RouteKDocParam.Param
import io.ktor.openapi.model.RouteKDocParam.Response
import io.ktor.openapi.model.RouteKDocParam.Security
import io.ktor.openapi.model.RouteKDocParam.Summary
import io.ktor.openapi.model.RouteKDocParam.Tag
import io.ktor.openapi.model.RoutingCall
import io.ktor.openapi.model.SpecInfo
import io.ktor.openapi.model.append
import io.ktor.openapi.model.appendObject
import io.ktor.openapi.model.put
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

object OpenApiSchemaGenerator {

    fun buildSchema(
        specInfo: SpecInfo,
        routingCalls: List<RoutingCall>,
        schemas: Map<String, JsonSchema>,
        defaultContentType: String,
        json: Json,
    ): JsonObject {
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
                            call.parameters.toSpecObject(defaultContentType)
                        )
                    }
                }
            }
        }

        return buildJsonObject {
            put("openapi", "3.1.1")
            put("info", json.encodeToJsonElement(specInfo))
            put("paths", JsonObject(paths))
            putJsonObject("components") {
                put("schemas", Json.encodeToJsonElement(schemas))
            }
        }
    }

    fun collectSchema(calls: List<RouteKDocParam>): Map<String, JsonObject> =
        calls.filterIsInstance<RouteKDocParam.Content>()
            .mapNotNull { it.type }
            .associate { type ->
                type to buildJsonObject {}
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
                        if (other in current && other !in childParentMap)
                            childParentMap[other] = current
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

    fun List<RouteKDocParam>.toSpecObject(defaultContentType: String) =
        JsonObject(toSpecParametersMap(defaultContentType))

    fun List<RouteKDocParam>.toSpecParametersMap(
        defaultContentType: String
    ): Map<String, JsonElement> = buildMap {
        for (param in this@toSpecParametersMap) {
            when(param) {
                is Summary -> put("summary", param.text)
                is Description -> put("description", param.text)
                is Body -> {
                    put("requestBody", param.jsonObject(defaultContentType))
                }
                is Cookie -> {
                    append("parameters", buildJsonObject {
                        put("name", param.name)
                        put("in", "cookie")
                        put("description", param.description)
                        put("required", true)
                        putJsonObject("schema") {
                            put("type", "string")
                        }
                    })
                }
                is RouteKDocParam.Deprecated -> {
                    put("deprecated", JsonPrimitive(true))
                }
                is Header -> {
                    append("parameters", buildJsonObject {
                        put("name", param.name)
                        put("in", "header")
                        put("description", param.description)
                        put("required", false)
                        putJsonObject("schema") {
                            put("type", "string")
                        }
                    })
                }
                is Param -> {
                    append("parameters", buildJsonObject {
                        put("name", param.name)
                        put("in", "path") // Default to path, could be refined based on route analysis
                        put("description", param.description)
                        put("required", true)
                        putJsonObject("schema") {
                            put("type", "string")
                        }
                    })
                }
                is Response -> {
                    appendObject("responses", param.code, param.jsonObject(defaultContentType))
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

    private fun RouteKDocParam.Content.jsonObject(contentType: String) = buildJsonObject {
        put("description", description)
        putJsonObject("content") {
            put(contentType, buildJsonObject {
                type?.let {
                    // TODO handle list / arrays
                    putJsonObject("schema") {
                        put("\$ref", "#/components/schemas/$it")
                    }
                }
            })
        }
    }
}