package io.ktor.plugin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FatJarTest {
    companion object {
        private val BUILD_GRADLE_KTS_CONTENT = """
            plugins {
                kotlin("jvm") version "1.7.0"
                id("io.ktor.plugin")
            }
            application.mainClass.set("my.org.MainKt")
            repositories.mavenCentral()
            dependencies.implementation("com.fasterxml.jackson.core:jackson-databind:2.13.3")
        """.trimIndent()

        private val SETTINGS_GRADLE_KTS_CONTENT = """
            rootProject.name = "test-fat-jar"
        """.trimIndent()

        private val MAIN_KT_CONTENT = """
            package my.org

            import com.fasterxml.jackson.databind.ObjectMapper
            import java.io.File

            fun main() {
                data class User(val name: String, val age: Int)
                ObjectMapper().writeValue(File("result.txt"), User("John", 30))
            }
        """.trimIndent()
    }

    private fun testFatJar(
        projectDir: File,
        buildGradleKtsContent: String,
        generatedFatJarFileName: String,
        taskName: String = "buildFatJar"
    ) {
        projectDir.resolve("build.gradle.kts").writeText(buildGradleKtsContent)
        projectDir.resolve("settings.gradle.kts").writeText(SETTINGS_GRADLE_KTS_CONTENT)
        projectDir
            .resolve("src/main/kotlin/my/org")
            .also { it.mkdirs() }
            .resolve("Main.kt")
            .writeText(MAIN_KT_CONTENT)

        createGradleRunner(projectDir).withArguments(taskName).build()

        val expected = javaClass.classLoader.getResource("fat.jar")!!.file
        val actual = projectDir.resolve("build/libs/$generatedFatJarFileName")
        assertZipFilesEqual(ZipFile(expected), ZipFile(actual))
    }

    @Test
    fun `build fat jar without extra settings builds name-all_jar`(@TempDir projectDir: File) = testFatJar(
        projectDir,
        BUILD_GRADLE_KTS_CONTENT,
        generatedFatJarFileName = "test-fat-jar-all.jar"
    )

    @Test
    fun `build fat jar with version builds name-version-all_jar`(@TempDir projectDir: File) = testFatJar(
        projectDir,
        buildGradleKtsContent = "$BUILD_GRADLE_KTS_CONTENT\nversion = \"1.2.3\"",
        generatedFatJarFileName = "test-fat-jar-1.2.3-all.jar"
    )

    @Test
    fun `build fat jar with fixed jar name builds fixed-named-jar`(@TempDir projectDir: File) = testFatJar(
        projectDir,
        buildGradleKtsContent = "$BUILD_GRADLE_KTS_CONTENT\nktor.fatJar.archiveFileName = \"fat.jar\"",
        generatedFatJarFileName = "fat.jar"
    )

    @Test
    fun `build fat jar with fixed jar name and version builds fixed-named-jar`(@TempDir projectDir: File) = testFatJar(
        projectDir,
        buildGradleKtsContent = "$BUILD_GRADLE_KTS_CONTENT\nktor.fatJar.archiveFileName = \"fat.jar\"\nversion = \"1.2.3\"",
        generatedFatJarFileName = "fat.jar"
    )

    @Test
    fun `build fat jar without mainClass set but with mainClassName set works`(@TempDir projectDir: File) = testFatJar(
        projectDir,
        buildGradleKtsContent = BUILD_GRADLE_KTS_CONTENT
            .replace("""application { mainClass.set("my.org.MainKt") }""", "")
            .plus("\nsetProperty(\"mainClassName\", \"my.org.MainKt\")"),
        generatedFatJarFileName = "test-fat-jar-all.jar"
    )

    @Test
    fun `runFatJar runs the fat jar`(@TempDir projectDir: File) {
        testFatJar(
            projectDir,
            BUILD_GRADLE_KTS_CONTENT,
            generatedFatJarFileName = "test-fat-jar-all.jar",
            taskName = "runFatJar"
        )
        val resultFile = projectDir.resolve("result.txt")
        assertTrue(resultFile.exists(), "Resulting file has not been created")
        assertEquals("""{"name":"John","age":30}""", resultFile.readText(), "Content differs")
    }
}
