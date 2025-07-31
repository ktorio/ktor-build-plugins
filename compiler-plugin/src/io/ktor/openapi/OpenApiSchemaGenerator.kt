package io.ktor.openapi

import io.ktor.openapi.model.JsonSchema
import io.ktor.openapi.model.JsonType
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
import io.ktor.openapi.model.TypeLink
import io.ktor.openapi.model.append
import io.ktor.openapi.model.appendObject
import io.ktor.openapi.model.put
import io.ktor.openapi.model.takeCompatible
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
        routeElements: List<RouteElement>,
        schemas: Map<String, JsonSchema>,
        defaultContentType: String,
        json: Json,
    ): JsonObject {
        val actualCalls = routeElements
            .mergeNested()
            .filterIsInstance<RouteElement.Route>()
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

            val ancestry = sequence {
                var current: RouteElement? = route
                while (current != null) {
                    yield(current)
                    current = childParentMap[current]
                }
            }
            val mergedPath = StringBuilder()
            val mergedParams = mutableListOf<RouteField>()
            for (element in ancestry.toList().reversed()) {
                (element as? RouteElement.Route)?.path?.takeIf { it.isNotEmpty() }?.let {
                    mergedPath.append("$it/")
                }
                mergedParams.takeCompatible(element.parameters)
            }
            parentChildMap[route]?.flatMap { it.parameters }?.let {
                mergedParams.takeCompatible(it)
            }

            route.copy(
                path = mergedPath.toString().trimEnd('/').replace("//", "/"),
                parameters = mergedParams
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
                    put("requestBody", param.jsonSchema(defaultContentType))
                }
                is Parameter -> {
                    append("parameters", buildJsonObject {
                        put("name", param.name)
                        put("in", param.`in`)
                        put("description", param.description)
                        put("required", true)
                        put("schema", Json.encodeToJsonElement(param.typeLink?.jsonSchema() ?: JsonSchema.String))
                    })
                }
                is RouteField.Deprecated -> {
                    put("deprecated", JsonPrimitive(true))
                }
                is Response -> {
                    appendObject("responses", param.code, param.jsonSchema(defaultContentType))
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

    private fun RouteField.Content.jsonSchema(contentType: String) = buildJsonObject {
        put("description", description)
        putJsonObject("content") {
            put(this@jsonSchema.contentType ?: contentType, buildJsonObject {
                val type = typeLink ?: return@buildJsonObject
                put("schema", Json.encodeToJsonElement(type.jsonSchema()))
            })
        }
    }

    private fun TypeLink.jsonSchema(): JsonSchema = when(this) {
        is TypeLink.Simple -> JsonSchema(jsonType)
        is TypeLink.Reference -> JsonSchema(ref = "#/components/schemas/$name")
        is TypeLink.Optional -> delegate.jsonSchema() // TODO
        is TypeLink.Array -> JsonSchema(
            type = JsonType.array,
            items = element.jsonSchema()
        )
    }
}