package io.ktor.plugin

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertContains
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
        taskName: String = "buildFatJar",
        expectSuccess: Boolean = true
    ) = buildProject(
        projectDir = projectDir,
        buildGradleKtsContent = buildGradleKtsContent,
        settingsGradleKtsContent = SETTINGS_GRADLE_KTS_CONTENT,
        mainKtContent = MAIN_KT_CONTENT,
        buildCommand = taskName,
        expectSuccess = expectSuccess
    ).also {
        if (expectSuccess) {
            val expected = javaClass.classLoader.getResource("fat.jar")!!.file
            val actual = projectDir.resolve("build/libs/$generatedFatJarFileName")
            assertZipFilesEqual(ZipFile(expected), ZipFile(actual))
        }
    }

    @Nested
    inner class BuildFatJar {
        @Test
        fun `without extra settings builds name-all_jar`(@TempDir projectDir: File) {
            testFatJar(
                projectDir,
                BUILD_GRADLE_KTS_CONTENT,
                generatedFatJarFileName = "test-fat-jar-all.jar"
            )
        }

        @Test
        fun `with fixed jar name builds fixed-named-jar`(@TempDir projectDir: File) {
            testFatJar(
                projectDir,
                BUILD_GRADLE_KTS_CONTENT.plus("\nktor.fatJar.archiveFileName.set(\"fat.jar\")"),
                generatedFatJarFileName = "fat.jar"
            )
        }

        @Test
        fun `without mainClass set but with mainClassName set works`(@TempDir projectDir: File) {
            testFatJar(
                projectDir,
                BUILD_GRADLE_KTS_CONTENT
                    .replace("""application { mainClass.set("my.org.MainKt") }""", "")
                    .plus("\nsetProperty(\"mainClassName\", \"my.org.MainKt\")"),
                generatedFatJarFileName = "test-fat-jar-all.jar"
            )
        }

        @Test
        fun `fails when mainClass and mainClassName are not set`(@TempDir projectDir: File) {
            val result = testFatJar(
                projectDir,
                BUILD_GRADLE_KTS_CONTENT.replace("application.mainClass.set(\"my.org.MainKt\")", ""),
                generatedFatJarFileName = "test-fat-jar-all.jar",
                expectSuccess = false
            )
            assertContains(
                charSequence = result.output,
                other = "Cannot query the value of extension 'application' property 'mainClass' because it has no value available."
            )
        }
    }

    @Nested
    inner class RunFatJar {
        @Test
        fun `task runs after buildFatJar`(@TempDir projectDir: File) {
            val result = testFatJar(
                projectDir,
                BUILD_GRADLE_KTS_CONTENT,
                generatedFatJarFileName = "test-fat-jar-all.jar",
                taskName = "runFatJar"
            )
            assertContains(result.tasks.map { it.path }, ":buildFatJar")
        }

        @Test
        fun `builds and runs the fat jar`(@TempDir projectDir: File) {
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
}
