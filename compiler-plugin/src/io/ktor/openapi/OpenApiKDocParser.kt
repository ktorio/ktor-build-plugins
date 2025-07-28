package io.ktor.openapi

import io.ktor.openapi.model.*
import io.ktor.openapi.model.RouteKDocParam.*
import io.ktor.openapi.model.RouteKDocParam.Deprecated

fun SourceCoordinates.parseKDoc(): List<RouteKDocParam> =
    parsePrecedingComment(file.text, range.first)

/**
 * Parses the comment that precedes a given offset in the source text.
 * Handles both single-line (//) and block (/* */) comments.
 *
 * @param text The source text of the file
 * @param beforeOffset The offset before which to look for comments
 * @return The extracted comment text or empty string if no comment is found
 */
fun parsePrecedingComment(text: CharSequence, beforeOffset: Int): List<RouteKDocParam> {
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
    if (commentEnd != -1) {
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

private fun List<String>.parseParameters(): List<RouteKDocParam> =
    buildList {
        val current = StringBuilder()
        for (line in this@parseParameters) {
            if (line.startsWith("@")) {
                if (current.isNotEmpty()) {
                    add(parseParameter(current.toString()))
                    current.clear()
                }
            }
            current.appendLine(line)
        }
        if (current.isNotEmpty()) {
            add(parseParameter(current.toString()))
        }
    }

val schemaArg = Regex("^\\[(.*)]$")

fun parseParameter(text: String): RouteKDocParam {
    if (!text.startsWith('@'))
        return Summary(text)
    var i = 0
    val words = text.trim().removePrefix("@").split(" ")
    val next = { words[i++] }
//    val maybeNext: ((String) -> Boolean) -> String? = { predicate ->
//        words.getOrNull(i)?.takeIf(predicate)?.also { i++ }
//    }
    val tryMatchNext: Regex.() -> String? = {
        words.getOrNull(i)?.let { word ->
            matchEntire(word)?.let { match ->
                match.groupValues[1].also { i++ }
            }
        }
    }
    val remaining = { words.drop(i).joinToString(" ").trim() }

    return when (next()) {
        "tag" -> Tag(next())
        "param" -> Param(next(), remaining())
        "header" -> Header(next(), remaining())
        "cookie" -> Cookie(next(), remaining())
        "body" -> Body(schemaArg.tryMatchNext(), remaining())
        "response" -> Response(next(), schemaArg.tryMatchNext(), remaining())
        "deprecated" -> Deprecated(remaining())
        else -> throw IllegalArgumentException("Invalid KDoc parameter: $text")
    }
}