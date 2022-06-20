package io.ktor.plugin

import io.ktor.plugin.features.*
import org.gradle.api.JavaVersion
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains

class DockerTest {
    companion object {
        private val BUILD_GRADLE_KTS_CONTENT = """
            import io.ktor.plugin.features.*
            plugins {
                kotlin("jvm") version "1.7.0"
                id("io.ktor.plugin")
            }
            repositories.mavenCentral()
            application.mainClass.set("my.org.MainKt")
            java.targetCompatibility = JavaVersion.VERSION_%project.java.version%
            ktor.docker.jreVersion.set(JreVersion.%docker.java.version%)
        """.trimIndent()

        private fun buildGradleKts(
            projectJava: JavaVersion = JavaVersion.VERSION_17,
            imageJava: JreVersion = JreVersion.JRE_17
        ) = BUILD_GRADLE_KTS_CONTENT
            .replace("%project.java.version%", projectJava.toString().replace('.', '_'))
            .replace("%docker.java.version%", imageJava.toString())

        private val SETTINGS_GRADLE_KTS_CONTENT = """
            rootProject.name = "docker-test"
        """.trimIndent()

        private val MAIN_KT_CONTENT = """
            package my.org

            fun main() = println("Hello, world!")
        """.trimIndent()
    }

    private fun buildDockerImage(
        projectDir: File,
        buildGradleKtsContent: String,
        expectSuccess: Boolean = true
    ) = buildProject(
        projectDir = projectDir,
        buildGradleKtsContent = buildGradleKtsContent,
        settingsGradleKtsContent = SETTINGS_GRADLE_KTS_CONTENT,
        mainKtContent = MAIN_KT_CONTENT,
        buildCommand = "buildImage",
        expectSuccess = expectSuccess
    )

    @Nested
    inner class BuildDockerImage {
        @Test
        fun `builds an image when the project java version is less than the image java version`(@TempDir projectDir: File) {
            buildDockerImage(
                projectDir = projectDir,
                buildGradleKtsContent = buildGradleKts(
                    projectJava = JavaVersion.VERSION_1_8,
                    imageJava = JreVersion.JRE_11
                )
            )
        }

        @Test
        fun `builds an image when the project java version is equal to the image java version`(@TempDir projectDir: File) {
            buildDockerImage(
                projectDir = projectDir,
                buildGradleKtsContent = buildGradleKts(
                    projectJava = JavaVersion.VERSION_1_8,
                    imageJava = JreVersion.JRE_1_8
                )
            )
        }

        @Test
        fun `fails when the project java version is higher than the image java version`(@TempDir projectDir: File) {
            val buildResult = buildDockerImage(
                projectDir = projectDir,
                buildGradleKtsContent = buildGradleKts(
                    projectJava = JavaVersion.VERSION_11,
                    imageJava = JreVersion.JRE_1_8
                ),
                expectSuccess = false
            )
            assertContains(
                charSequence = buildResult.output,
                other = "You're trying to build an image with JRE 1.8 while your project's JDK or 'java.targetCompatibility' is 11. " +
                        "Please use a higher version of an image JRE through the 'ktor.docker.jreVersion' extension in the build file, " +
                        "or set the 'java.targetCompatibility' property to a lower version.",
                message = "Actual output does not contain the expected message"
            )
        }
    }
}
