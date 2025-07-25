package io.ktor.openapi

import io.ktor.openapi.model.RouteKDocParam
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal fun MutableMap<String, JsonElement>.put(key: String, value: String) =
    put(key, JsonPrimitive(value))

internal fun MutableMap<String, JsonElement>.put(key: String, buildObject: JsonObjectBuilder.() -> Unit) =
    put(key, buildJsonObject(buildObject))

internal fun MutableMap<String, JsonElement>.append(key: String, value: String) =
    append(key, JsonPrimitive(value))

internal fun MutableMap<String, JsonElement>.append(key: String, value: JsonElement) =
    compute(key) { _, oldValue ->
        oldValue?.jsonArray?.let { JsonArray(it + value) } ?: JsonArray(listOf(value))
    }

internal fun MutableMap<String, JsonElement>.appendObject(key: String, subKey: String, buildObject: JsonObjectBuilder.() -> Unit) =
    appendObject(key, subKey, buildJsonObject(buildObject))

internal fun MutableMap<String, JsonElement>.appendObject(key: String, subKey: String, element: JsonElement) =
    compute(key) { _, oldValue ->
        val newValue = mapOf(subKey to element)
        oldValue?.jsonObject?.let { JsonObject(it + newValue) }
            ?: JsonObject(newValue)
    }

internal fun RouteKDocParam.Data.jsonObject() = buildJsonObject {
    put("description", description)
    type?.let { type ->
        putJsonObject("content") {
            // TODO supply from content negotiation or call response
            putJsonObject("application/json") {
                putJsonObject("schema") {
                    put("type", type)
                }
            }
        }
    }
}

