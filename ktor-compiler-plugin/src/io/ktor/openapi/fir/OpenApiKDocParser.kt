package io.ktor.openapi.fir

import io.ktor.openapi.Logger
import io.ktor.openapi.findJsonPrimitiveType
import io.ktor.openapi.model.ModelAttribute
import io.ktor.openapi.routing.RouteField
import io.ktor.openapi.routing.RouteField.*
import io.ktor.openapi.routing.RouteField.Deprecated
import io.ktor.openapi.routing.RouteFieldList
import io.ktor.openapi.routing.TypeReference
import io.ktor.openapi.routing.LocalReference
import io.ktor.openapi.routing.ParamIn
import org.jetbrains.kotlin.name.FqName

/**
 * Parses the comment that precedes a given offset in the source text.
 * Handles both single-line (//) and block (/* */) comments.
 *
 * @param logger For logging errors
 * @param packageName The current package (for resolving type references)
 * @param text The source text of the file
 * @param beforeOffset The offset before which to look for comments
 * @return The extracted comment text or empty string if no comment is found
 */
fun parsePrecedingComment(
    logger: Logger,
    packageName: FqName,
    text: CharSequence,
    beforeOffset: Int
): RouteFieldList {
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
        return singleLineComments.parseParameters(logger, packageName)
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
            return lines.parseParameters(logger, packageName)
        }
    }

    // No comments found
    return emptyList()
}

private fun List<String>.parseParameters(logger: Logger, packageName: FqName): List<RouteField> =
    sequence {
        val current = StringBuilder()
        for (line in this@parseParameters) {
            if (line.startsWith("@")) {
                if (current.isNotEmpty()) {
                    yield(parseParameter(logger, packageName, current))
                    current.clear()
                }
            }
            current.appendLine(line)
        }
        if (current.isNotEmpty()) {
            yield(parseParameter(logger, packageName, current))
        }
    }.filterNotNull().toList()

val contentTypeArg = Regex("^(\\w+/\\S+)$")
val schemaArg = Regex("^(:?)\\[(.*)]([?+]?)$")

fun parseParameter(logger: Logger, packageName: FqName, text: CharSequence): RouteField? {
    try {
        if (!text.startsWith('@'))
            return Summary(text.toString().trim())
        val bodyAndAttributes = text.split(Regex("\n(?=\\s*\\$?\\p{Alpha}+)"), limit = 2)
        var i = 0
        val words = bodyAndAttributes.first().trim().removePrefix("@").split(Regex("\\s+"))
        val next = { words[i++] }
        val nextReference = { LocalReference.StringValue(next()) }
        val tryMatchNext: Regex.() -> MatchResult? = {
            words.getOrNull(i)?.let { word ->
                matchEntire(word)?.let { match ->
                    match.also { i++ }
                }
            }
        }
        val nextSchemaArg = {
            schemaArg.tryMatchNext()?.let {
                val (prefix, name, postfix) = it.destructured
                getSchemaReference(prefix, name, postfix, packageName)
            }
        }
        val remaining = { words.drop(i).joinToString(" ").trim() }
        val attributes = {
            bodyAndAttributes.getOrNull(1)?.let {
                parseJsonSchemaAttributes(logger, it)
            } ?: emptyMap()
        }

        return when(val key = next()) {
            "body" -> Body(
                contentTypeArg.tryMatchNext()?.groupValues[1]?.let(LocalReference::of),
                nextSchemaArg(),
                remaining(),
                attributes()
            )

            "cookie" -> Parameter(ParamIn.COOKIE, nextReference(), nextSchemaArg(), remaining(), attributes())
            "deprecated" -> Deprecated(remaining())
            "description" -> Description(remaining())
            "externalDocs" -> ExternalDocs(next(), remaining())
            "header" -> Parameter(ParamIn.HEADER, nextReference(), nextSchemaArg(), remaining(), attributes())
            "ignore" -> Ignore
            "path" -> Parameter(ParamIn.PATH, nextReference(), nextSchemaArg(), remaining(), attributes())
            "query" -> Parameter(ParamIn.QUERY, nextReference(), nextSchemaArg(), remaining(), attributes())
            "response" -> Response(
                next().toIntOrNull()?.let(LocalReference::of),
                contentTypeArg.tryMatchNext()?.groupValues[1]?.let(LocalReference::of),
                nextSchemaArg(),
                remaining(),
                attributes()
            )

            "security" -> Security(next(), remaining().trim().split(Regex("\\s*,\\s*")).ifEmpty { null })
            "tag" -> Tag(next())
            else -> {
                logger.log("Unknown KDoc item: @$key")
                null
            }
        }
    } catch (t: Throwable) {
        logger.log("Failed to parse parameter: $text", t)
        return null
    }
}

private fun parseJsonSchemaAttributes(logger: Logger, text: CharSequence): Map<ModelAttribute, String> =
    text.trim().lineSequence()
        .map { it.trim().split(Regex("\\s*:\\s*"), limit = 2) }
        .filter { it.size == 2 }
        .mapNotNull { (key, value) ->
            when(val attr = ModelAttribute.parse(key)) {
                null -> {
                    logger.log("Invalid attribute $key")
                    null
                }
                else -> attr to value
            }
        }
        .toMap()

/**
 * Treats links like `[Type]?` as optional and `[Type]+` as arrays.
 *
 * This design may be revisited before release.
 */
fun getSchemaReference(
    prefix: String,
    name: String,
    postfix: String,
    packageName: FqName,
): TypeReference.Link {
    var link = when (val jsonType = findJsonPrimitiveType(name)) {
        null ->
            TypeReference.Link.Reference(
                if (name.contains('.')) name
                else "${packageName.asString()}.$name"
            )
        else -> TypeReference.Link.Primitive(name, jsonType)
    }
    link = when (postfix) {
        "?" -> TypeReference.Link.Optional(link)
        "+" -> TypeReference.Link.Array(link)
        else -> link
    }
    link = when (prefix) {
        ":" -> TypeReference.Link.Map(link)
        else -> link
    }
    return link
}