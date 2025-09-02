package io.ktor.openapi

import io.ktor.openapi.model.*
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.RouteField.*
import kotlinx.serialization.json.*

object OpenApiSpecGenerator {

    fun buildSpecification(
        specInfo: SpecInfo,
        routes: RouteCallGraph,
        defaultContentType: String,
        securitySchemes: List<RoutingReferenceResult.SecurityScheme>,
        json: Json,
    ): JsonObject {
        val routes = RouteCollector.collectRoutes(routes)
        val routesByPath = routes.groupBy { it.path }
        val schemaJson = JsonObject(routes.flatMap {
            it.fields.filterIsInstance<Schema>()
        }.associate { (name, schema) ->
            name to Json.encodeToJsonElement(schema)
        })

        val paths = buildJsonObject {
            for ((path, calls) in routesByPath) {
                putJsonObject(path) {
                    for (call in calls) {
                        put(
                            call.method,
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
                                            put(name, json.encodeToJsonElement(flow))
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
        defaultContentType: String,
        securitySchemes: List<RoutingReferenceResult.SecurityScheme> = emptyList(),
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
                        param.description?.let {
                            put("description", it)
                        }
                        put("required", JsonPrimitive(param is PathParam))
                        put("schema", param.asSchema() ?: JsonSchema.StringObject)
                    })
                }
                is RouteField.Deprecated -> {
                    put("deprecated", JsonPrimitive(true))
                }
                is Response -> {
                    appendObject("responses", param.code, param.asContentJson(defaultContentType))
                }
                is Security -> {
                    when (param.scheme) {
                        // optional
                        null -> append("security", JsonObject(emptyMap()))
                        // everything
                        "*" -> securitySchemes.forEach {
                            append("security", JsonObject(mapOf(it.name to JsonArray(emptyList()))))
                        }
                        // specific
                        else -> append("security", JsonObject(mapOf(param.scheme to JsonArray(emptyList()))))
                    }
                }
                is Tag -> {
                    append("tags", param.name)
                }
                is Transient -> {}
            }
        }
    }

    private fun Content.asContentJson(defaultContentType: String) = buildJsonObject {
        put("description", description ?: "")
        val type = asSchema() ?: return@buildJsonObject
        val contentType = this@asContentJson.contentType
        putJsonObject("content") {
            put(contentType ?: defaultContentType, buildJsonObject {
                put("schema", type)
            })
        }
    }

    private fun SchemaHolder.asSchema(): JsonElement? =
        schema?.asSchema()?.let { typeSchema ->
            val typeSchemaObject = Json.encodeToJsonElement(typeSchema).jsonObject
            JsonObject(
                 typeSchemaObject + attributes
            )
        }

}