package io.ktor.openapi.model

import kotlinx.serialization.json.*

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

