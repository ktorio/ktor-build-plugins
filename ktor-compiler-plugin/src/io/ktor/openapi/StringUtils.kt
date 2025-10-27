package io.ktor.openapi

fun String.toCamelCase() =
    splitToSequence(Regex("\\W+"))
        .joinToString("") { it.capitalizeFirst() }

fun String.capitalizeFirst() =
    replaceFirstChar { it.titlecase() }