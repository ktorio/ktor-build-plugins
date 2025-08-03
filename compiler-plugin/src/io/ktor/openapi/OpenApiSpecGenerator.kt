package io.ktor.openapi

import io.ktor.openapi.model.JsonSchema
import io.ktor.openapi.model.RouteField
import io.ktor.openapi.model.RouteField.Body
import io.ktor.openapi.model.RouteField.Description
import io.ktor.openapi.model.RouteField.Parameter
import io.ktor.openapi.model.RouteField.Response
import io.ktor.openapi.model.RouteField.Security
import io.ktor.openapi.model.RouteField.Summary
import io.ktor.openapi.model.RouteField.Tag
import io.ktor.openapi.model.RouteElement
import io.ktor.openapi.model.RouteFieldList
import io.ktor.openapi.model.SpecInfo
import io.ktor.openapi.model.append
import io.ktor.openapi.model.appendObject
import io.ktor.openapi.model.getReference
import io.ktor.openapi.model.mergeAll
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

object OpenApiSpecGenerator {

    fun buildSpecification(
        specInfo: SpecInfo,
        routeElements: List<RouteElement>,
        schemas: Map<String, JsonSchema>,
        defaultContentType: String,
        json: Json,
    ): JsonObject {
        val callsByPath = routeElements
            .mergeNested()
            .filterIsInstance<RouteElement.Route>()
            .groupBy { it.path }
        val schemaReferences = callsByPath.values.flatten()
            .flatMap { it.parameters }
            .filterIsInstance<RouteField.Content>()
            .mapNotNull { it.schema?.getReference() }
        val schemaJson = Json.encodeToJsonElement(schemas.filterKeys { it in schemaReferences })

        val paths = buildJsonObject {
            for ((path, calls) in callsByPath) {
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
                put("schemas", schemaJson)
            }
        }
    }


    /**
     * Merges nested routing calls into a single call by merging their paths and parameters.
     */
    fun List<RouteElement>.mergeNested(): List<RouteElement> {
        val parentChildMap = mutableMapOf<RouteElement, List<RouteElement>>()
        val childParentMap = mutableMapOf<RouteElement, RouteElement>()

        fun RouteElement.addChild(child: RouteElement) {
            parentChildMap[this] = parentChildMap.getOrDefault(this, emptyList()) + child
            childParentMap[child] = this
        }

        // 1. Build the call tree. We assume calls are ordered by location.
        for (i in 0 ..< lastIndex) {
            when(val current = get(i)) {
                // only subsequent invocations can be children
                is RouteElement.Route -> {
                    for (j in i + 1 ..< size) {
                        if (get(j) in current)
                            current.addChild(get(j))
                        else break
                    }
                }
                // body can occur anywhere, so we need to check the whole array
                is RouteElement.Extension -> {
                    for (other in this) {
                        if (other in current && other !in childParentMap)
                            current.addChild(other)
                    }
                }
                // call features are leaf nodes
                is RouteElement.CallFeature -> {}
            }
        }

        // 2. Merge routes from paths in the tree.
        return mapNotNull { route ->
            if (route !is RouteElement.Route || route.method == null) return@mapNotNull null
            if (route !in childParentMap && route !in parentChildMap) return@mapNotNull route

            val ancestors = sequence {
                var current: RouteElement? = route
                while (current != null) {
                    yield(current)
                    current = childParentMap[current]
                }
            }.toList()

            val descendents = parentChildMap[route] ?: emptyList()

            route.copy(
                path = ancestors.reversed().asSequence()
                    .filterIsInstance<RouteElement.Route>()
                    .mapNotNull { it.path }
                    .joinToString("/")
                    .replace("//", "/"),
                parameters = mergeAll(
                    ancestors.map { it.parameters } + descendents.map { it.parameters }
                )
            )
        }
    }

    fun RouteFieldList.toSpecObject(defaultContentType: String) =
        JsonObject(toSpecParametersMap(defaultContentType))

    fun RouteFieldList.toSpecParametersMap(
        defaultContentType: String
    ): Map<String, JsonElement> = buildMap {
        for (param in this@toSpecParametersMap) {
            when(param) {
                is Summary -> put("summary", param.text)
                is Description -> put("description", param.text)
                is Body -> {
                    put("requestBody", param.asSchema(defaultContentType))
                }
                is Parameter -> {
                    append("parameters", buildJsonObject {
                        put("name", param.name)
                        put("in", param.`in`)
                        put("description", param.description)
                        put("required", true)
                        put("schema", Json.encodeToJsonElement(param.schema?.asSchema() ?: JsonSchema.String))
                    })
                }
                is RouteField.Deprecated -> {
                    put("deprecated", JsonPrimitive(true))
                }
                is Response -> {
                    appendObject("responses", param.code, param.asSchema(defaultContentType))
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

    private fun RouteField.Content.asSchema(contentType: String) = buildJsonObject {
        put("description", description)
        val type = schema ?: return@buildJsonObject
        putJsonObject("content") {
            put(this@asSchema.contentType ?: contentType, buildJsonObject {
                put("schema", Json.encodeToJsonElement(type.asSchema()))
            })
        }
    }
}