package io.ktor.openapi.routing

interface SourceCoordinates {
    val filePath: String?
    val startOffset: Int
    val endOffset: Int

    operator fun contains(other: SourceCoordinates): Boolean =
        filePath == other.filePath && other.startOffset in startOffset ..< endOffset && other != this
}

data class SourceTextRange(
    override val filePath: String,
    val fileText: CharSequence,
    val range: IntRange,
): SourceCoordinates {
    override val startOffset: Int get() = range.first
    override val endOffset: Int get() = range.last + 1
}