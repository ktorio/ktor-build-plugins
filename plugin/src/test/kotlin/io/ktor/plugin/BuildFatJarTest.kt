package io.ktor.plugin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile

class BuildFatJarTest {
    companion object {
        private val BUILD_GRADLE_KTS_CONTENT = """
            plugins {
                application
                kotlin("jvm") version "1.6.21"
                id("io.ktor.plugin")
            }

            application { mainClass.set("my.org.MainKt") }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation("com.fasterxml.jackson.core:jackson-databind:2.13.3")
            }
        """.trimIndent()

        private val SETTINGS_GRADLE_KTS_CONTENT = """
            rootProject.name = "test-fat-jar"
        """.trimIndent()

        private val MAIN_KT_CONTENT = """
            package my.org

            import com.fasterxml.jackson.databind.ObjectMapper

            fun main() {
                data class User(val name: String, val age: Int)
                ObjectMapper().writeValue(System.out, User("John", 30))
            }
        """.trimIndent()
    }

    private fun buildFatJar(
        projectDir: File,
        buildGradleKtsContent: String,
        generatedFatJarFileName: String
    ) {
        projectDir.resolve("build.gradle.kts").writeText(buildGradleKtsContent)
        projectDir.resolve("settings.gradle.kts").writeText(SETTINGS_GRADLE_KTS_CONTENT)
        projectDir
            .resolve("src/main/kotlin/my/org")
            .also { it.mkdirs() }
            .resolve("Main.kt")
            .writeText(MAIN_KT_CONTENT)

        createGradleRunner(projectDir).withArguments("buildFatJar").build()

        val expected = javaClass.classLoader.getResource("fat.jar")!!.file
        val actual = projectDir.resolve("build").resolve("libs").resolve(generatedFatJarFileName)
        assertZipFilesEqual(ZipFile(expected), ZipFile(actual))
    }

    @Test
    fun `build fat jar without extra settings builds name-all_jar`(@TempDir projectDir: File) = buildFatJar(
        projectDir,
        BUILD_GRADLE_KTS_CONTENT,
        "test-fat-jar-all.jar"
    )

    @Test
    fun `build fat jar with version builds name-version-all_jar`(@TempDir projectDir: File) = buildFatJar(
        projectDir,
        "$BUILD_GRADLE_KTS_CONTENT\nversion = \"1.2.3\"",
        "test-fat-jar-1.2.3-all.jar"
    )

    @Test
    fun `build fat jar with fixed jar name builds fixed-named-jar`(@TempDir projectDir: File) = buildFatJar(
        projectDir,
        "$BUILD_GRADLE_KTS_CONTENT\nktor.fatJar.archiveFileName = \"fat.jar\"",
        "fat.jar"
    )

    @Test
    fun `build fat jar with fixed jar name and version builds fixed-named-jar`(@TempDir projectDir: File) = buildFatJar(
        projectDir,
        "$BUILD_GRADLE_KTS_CONTENT\nktor.fatJar.archiveFileName = \"fat.jar\"\nversion = \"1.2.3\"",
        "fat.jar"
    )

    @Test
    fun `build fat jar without application_mainClass set but mainClassName propertySet works`(@TempDir projectDir: File) =
        buildFatJar(
            projectDir,
            BUILD_GRADLE_KTS_CONTENT
                .replace("""application { mainClass.set("my.org.MainKt") }""", "")
                .plus("\nsetProperty(\"mainClassName\", \"my.org.MainKt\")"),
            "test-fat-jar-all.jar"
        )
}