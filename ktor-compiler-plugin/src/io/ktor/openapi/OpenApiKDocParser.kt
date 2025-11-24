package io.ktor.openapi

import io.ktor.openapi.model.*
import io.ktor.openapi.routing.RouteField
import io.ktor.openapi.routing.RouteField.*
import io.ktor.openapi.routing.RouteField.Deprecated
import io.ktor.openapi.routing.RouteFieldList
import io.ktor.openapi.routing.SchemaReference
import io.ktor.openapi.routing.SourceTextRange
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

fun SourceTextRange.parseKDoc(): RouteFieldList =
    parsePrecedingComment(fileText, range.first)

/**
 * Parses the comment that precedes a given offset in the source text.
 * Handles both single-line (//) and block (/* */) comments.
 *
 * @param text The source text of the file
 * @param beforeOffset The offset before which to look for comments
 * @return The extracted comment text or empty string if no comment is found
 */
fun parsePrecedingComment(text: CharSequence, beforeOffset: Int): RouteFieldList {
    // Ensure offset is within bounds
    val offset = beforeOffset.coerceIn(0, text.length)

    // Find the line start preceding the offset
    val lineStart = text.lastIndexOf('\n', offset - 1).let {
        if (it == -1) 0 else it + 1
    }

    // Get all text before the current line
    val precedingText = text.subSequence(0, lineStart)

    // Skip any whitespace immediately before the current line
    var currentPos = lineStart - 1
    while (currentPos >= 0 && text[currentPos].isWhitespace()) {
        currentPos--
    }

    // Check for single-line comments first
    val singleLineComments = mutableListOf<String>()
    var checkPos = currentPos

    while (checkPos >= 0) {
        // Find the start of the current line
        val currentLineStart = text.lastIndexOf('\n', checkPos).let {
            if (it == -1) 0 else it + 1
        }

        // Extract the current line
        val currentLine = text.substring(currentLineStart, checkPos + 1).trim()

        if (currentLine.startsWith("//")) {
            // Found a single-line comment
            singleLineComments.add(0, currentLine.substring(2).trim())

            // Move to the previous line
            checkPos = currentLineStart - 2
        } else if (currentLine.isEmpty()) {
            // Skip empty lines
            checkPos = currentLineStart - 2
        } else {
            // Found a non-comment, non-empty line
            break
        }
    }

    // If we found single-line comments, return them
    if (singleLineComments.isNotEmpty()) {
        return singleLineComments.parseParameters()
    }

    // Check for block comments
    val commentEnd = precedingText.lastIndexOf("*/")
    if (commentEnd != -1 && text.subSequence(commentEnd + 2, lineStart).isBlank()) {
        val commentStart = precedingText.lastIndexOf("/*", commentEnd)
        if (commentStart != -1) {
            val lines = precedingText.subSequence(commentStart + 2, commentEnd)
                .lines()
                .map { it.trim(' ', '*') }
                .filter { it.isNotEmpty() }

            if (lines.isEmpty()) return emptyList()

            // Extract block comment content
            return lines.parseParameters()
        }
    }

    // No comments found
    return emptyList()
}

private fun List<String>.parseParameters(): List<RouteField> =
    sequence {
        val current = StringBuilder()
        for (line in this@parseParameters) {
            if (line.startsWith("@")) {
                if (current.isNotEmpty()) {
                    yield(parseParameter(current))
                    current.clear()
                }
            }
            current.appendLine(line)
        }
        if (current.isNotEmpty()) {
            yield(parseParameter(current))
        }
    }.filterNotNull().toList()

val contentTypeArg = Regex("^(\\w+/\\S+)$")
val schemaArg = Regex("^(:?)\\[(.*)]([?+]?)$")

fun parseParameter(text: CharSequence): RouteField? {
    if (!text.startsWith('@'))
        return Summary(text.toString().trim())
    val bodyAndAttributes = text.split(Regex("\n(?=\\s*\\$?\\p{Alpha}+)"), limit = 2)
    var i = 0
    val words = bodyAndAttributes.first().trim().removePrefix("@").split(" ")
    val next = { words[i++] }
    val tryMatchNext: Regex.() -> MatchResult? = {
        words.getOrNull(i)?.let { word ->
            matchEntire(word)?.let { match ->
                match.also { i++ }
            }
        }
    }
    val remaining = { words.drop(i).joinToString(" ").trim() }
    val attributes = {
        bodyAndAttributes.getOrNull(1)?.let {
            parseJsonSchemaAttributes(it)
        } ?: emptyMap()
    }

    return when (next()) {
        "body" -> Body(contentTypeArg.tryMatchNext()?.groupValues[1], schemaArg.tryMatchNext()?.getSchemaReference(), remaining(), attributes())
        "cookie" -> Cookie(next(), schemaArg.tryMatchNext()?.getSchemaReference(), remaining(), attributes())
        "deprecated" -> Deprecated(remaining())
        "description" -> Description(remaining())
        "externalDocs" -> ExternalDocs(remaining())
        "header" -> Header(next(), schemaArg.tryMatchNext()?.getSchemaReference(), remaining(), attributes())
        "ignore" -> Ignore
        "path" -> PathParam(next(), schemaArg.tryMatchNext()?.getSchemaReference(), remaining(), attributes())
        "query" -> QueryParam(next(), schemaArg.tryMatchNext()?.getSchemaReference(), remaining(), attributes())
        "response" -> Response(next(), contentTypeArg.tryMatchNext()?.groupValues[1], schemaArg.tryMatchNext()?.getSchemaReference(), remaining(), attributes())
        "security" -> Security(remaining())
        "tag" -> Tag(next())
        "operationId" -> OperationId(next())
        else -> null // ignore unknown tags
    }
}

private fun parseJsonSchemaAttributes(text: CharSequence): Map<String, JsonElement> =
    text.trim().lineSequence().map {
        it.trim().split(Regex("\\s*:\\s*"), limit = 2) }
        .filter { it.size == 2 }.associate { (key, value) ->
            key to parseJsonSchemaAttribute(key, value)
        }

private fun parseJsonSchemaAttribute(key: String, value: String): JsonPrimitive =
    when(key) {
        // Number/Integer Validation
        "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf" ->
            JsonPrimitive(value.toNumberOrNull() ?: 0.0)

        // String Validation
        "minLength", "maxLength" ->
            JsonPrimitive(value.toNumberOrNull() ?: 0)
        "pattern", "format" ->
            JsonPrimitive(value)

        // Array Validation
        "minItems", "maxItems", "minContains", "maxContains" ->
            JsonPrimitive(value.toNumberOrNull() ?: 0)
        "uniqueItems" ->
            JsonPrimitive(value.equals("true", ignoreCase = true))

        // Object Validation
        "minProperties", "maxProperties" ->
            JsonPrimitive(value.toNumberOrNull() ?: 0)
        "additionalProperties" ->
            JsonPrimitive(value.equals("true", ignoreCase = true))

        // General Validation
        "type", "default", "const" ->
            JsonPrimitive(value)

        // Metadata
        "title", "description", $$"$comment" ->
            JsonPrimitive(value)
        "readOnly", "writeOnly", "required" ->
            JsonPrimitive(value.equals("true", ignoreCase = true))

        // Default case for any other attributes
        else -> JsonPrimitive(value)
    }

private fun String.toNumberOrNull(): Number? =
    toLongOrNull() ?: toDoubleOrNull()

private fun MatchResult.getSchemaReference(): SchemaReference.Link? {
    val (prefix, name, postfix) = destructured
    return getSchemaReference(prefix, name, postfix)
}

/**
 * Treats links like `[Type]?` as optional and `[Type]+` as arrays.
 *
 * This design may be revisited before release.
 */
fun getSchemaReference(prefix: String?, name: String, postfix: String?): SchemaReference.Link? {
    var link = when (val jsonType = findJsonPrimitiveType(name)) {
        null -> SchemaReference.Link.Reference(name)
        else -> SchemaReference.Link.Simple(name, jsonType)
    }
    link = when (postfix) {
        "?" -> SchemaReference.Link.Optional(link)
        "+" -> SchemaReference.Link.Array(link)
        else -> link
    }
    link = when (prefix) {
        ":" -> SchemaReference.Link.Map(link)
        else -> link
    }
    return link
}