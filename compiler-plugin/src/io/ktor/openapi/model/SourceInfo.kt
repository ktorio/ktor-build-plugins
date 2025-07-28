package io.ktor.openapi.model

data class SourceCoordinates(
    val file: SourceFile,
    val range: IntRange,
) {
    operator fun contains(other: SourceCoordinates) =
        file.path == other.file.path &&
                other.range.first in range
}

data class SourceFile(
    val path: String,
    val text: CharSequence,
)