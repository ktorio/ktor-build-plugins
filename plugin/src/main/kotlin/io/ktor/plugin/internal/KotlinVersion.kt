package io.ktor.plugin.internal

@JvmInline
internal value class KotlinVersion(private val value: Int) : Comparable<KotlinVersion> {
    private val major: Int get() = value / 1_0_00
    private val minor: Int get() = (value / 1_00) % 10
    private val patch: Int get() = value % 100

    override fun toString(): String = "$major.$minor.$patch"
    override fun compareTo(other: KotlinVersion): Int = value.compareTo(other.value)

    companion object {
        fun parse(version: String): KotlinVersion {
            val versionParts = version
                .substringBefore("-")
                .split('.').mapNotNull { it.toIntOrNull() }
            require(versionParts.size == 3) { "Unable to parse Kotlin version '$version'." }

            val (major, minor, patch) = versionParts
            return KotlinVersion(major * 1_0_00 + minor * 1_00 + patch)
        }

        val V2_1_20 = KotlinVersion(2_1_20)
    }
}