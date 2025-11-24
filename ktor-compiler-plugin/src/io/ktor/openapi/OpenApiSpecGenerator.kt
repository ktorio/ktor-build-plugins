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
        val resolvedRoutes = RouteCollector.collectRoutes(routes)
        val routesByPath = resolvedRoutes.groupBy { it.path }
        val schemaJson = JsonObject(resolvedRoutes.flatMap {
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
                            JsonObject(call.fields.toOperationInfo(defaultContentType))
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

    fun RouteFieldList.toOperationInfo(
        defaultContentType: String,
        securitySchemes: List<RoutingReferenceResult.SecurityScheme> = emptyList(),
    ): Map<String, JsonElement> = buildMap {
        val responseHeaders = mutableListOf<ResponseHeader>()

        for (param in this@toOperationInfo) {
            when(param) {
                is Summary -> put("summary", param.text)
                is Description -> put("description", param.text)
                is ExternalDocs -> put("externalDocs", buildJsonObject { put("url", param.url) })
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
                is ResponseHeader -> {
                    responseHeaders += param
                }
                is Response -> {
                    val headersJson = responseHeaders.asJsonMap()
                    val contentJson = param.asContentJson(defaultContentType, headersJson)
                    appendObject("responses", param.code, contentJson)
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
                is Tag -> append("tags", param.name)
                is OperationId -> put("operationId", param.value)
                is Transient -> {}
            }
        }
    }

    private fun Content.asContentJson(defaultContentType: String, appendEntries: Map<String, JsonElement>? = null) = buildJsonObject {
        put("description", description ?: "")

        appendEntries?.forEach { (name, element) ->
            put(name, element)
        }

        val schema = asSchema()
        val contentType = this@asContentJson.contentType
        // when both content type and schema are null, we assume this is an empty response
        if (contentType == null && schema == null) {
            return@buildJsonObject
        }
        putJsonObject("content") {
            put(contentType ?: defaultContentType, buildJsonObject {
                schema?.let {
                    put("schema", schema)
                }
            })
        }
    }

    private fun List<ResponseHeader>.asJsonMap(): Map<String, JsonElement>? {
        if (isEmpty())
            return null
        return associate { header ->
            header.name to buildJsonObject {
                put("description", header.description ?: "")
                header.asSchema()?.let {
                    put("schema", it)
                }
            }
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