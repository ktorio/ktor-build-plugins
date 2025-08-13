package io.ktor.openapi

import io.ktor.openapi.model.*
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.RouteField.*
import io.ktor.openapi.routing.RoutingFunctionConstants.GET
import kotlinx.serialization.json.*

object OpenApiSpecGenerator {

    fun buildSpecification(
        specInfo: SpecInfo,
        routes: RouteGraph,
        schemas: Map<String, JsonSchema>,
        defaultContentType: String,
        securitySchemes: List<RoutingReferenceResult.SecurityScheme>,
        json: Json,
    ): JsonObject {
        val routes = RouteCollector.collectRoutes(routes)
        val callsByPath = routes
            .groupBy { it.path }
        val schemaJson = Json.encodeToJsonElement(schemas)

        val paths = buildJsonObject {
            for ((path, calls) in callsByPath) {
                putJsonObject(path) {
                    for (call in calls) {
                        put(
                            call.method ?: GET,
                            JsonObject(call.fields.toSpecParametersMap(defaultContentType))
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
                if (securitySchemes.isNotEmpty()) {
                    putJsonObject("securitySchemes") {
                        for (scheme in securitySchemes) {
                            putJsonObject(scheme.name) {
                                put("type", scheme.type)
                                if (scheme.scheme != null)
                                    put("scheme", scheme.scheme)
                                if (scheme.bearerFormat != null) put("bearerFormat", scheme.bearerFormat)
                                if (scheme.openIdConnectUrl != null) put("openIdConnectUrl", scheme.openIdConnectUrl)
                                if (scheme.type == "oauth2")
                                    putJsonObject("flows") {
                                        scheme.flows?.forEach { (name, flow) ->
                                            put(name, Json.encodeToJsonElement(flow))
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    fun RouteFieldList.toSpecParametersMap(
        defaultContentType: String
    ): Map<String, JsonElement> = buildMap {
        for (param in this@toSpecParametersMap) {
            when(param) {
                is Summary -> put("summary", param.text)
                is Description -> put("description", param.text)
                is Body -> {
                    put("requestBody", param.asContentJson(defaultContentType))
                }
                is Parameter -> {
                    append("parameters", buildJsonObject {
                        put("name", param.name)
                        put("in", param.`in`)
                        put("description", param.description)
                        put("required", JsonPrimitive(true))
                        put("schema", param.asSchema() ?: JsonSchema.String)
                    })
                }
                is RouteField.Deprecated -> {
                    put("deprecated", JsonPrimitive(true))
                }
                is Response -> {
                    appendObject("responses", param.code, param.asContentJson(defaultContentType))
                }
                is Security -> {
                    append("security", buildJsonObject {
                        putJsonArray(param.scheme) {}
                    })
                }
                is Tag -> {
                    append("tags", param.name)
                }
                is RouteField.Implicit -> {}
            }
        }
    }

    private fun RouteField.Content.asContentJson(defaultContentType: String) = buildJsonObject {
        put("description", description)
        val type = asSchema() ?: return@buildJsonObject
        val contentType = this@asContentJson.contentType
        putJsonObject("content") {
            put(contentType ?: defaultContentType, buildJsonObject {
                put("schema", type)
            })
        }
    }

    private fun RouteField.SchemaHolder.asSchema(): JsonElement? =
        schema?.asSchema()?.let { typeSchema ->
            val typeSchemaObject = Json.encodeToJsonElement(typeSchema).jsonObject
            JsonObject(
                 typeSchemaObject + attributes
            )
        }

}