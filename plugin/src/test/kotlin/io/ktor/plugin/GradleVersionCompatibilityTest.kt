package io.ktor.plugin

import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import kotlin.test.Test

class GradleVersionCompatibilityTest {
    @ParameterizedTest
    @ValueSource(strings = ["6.7", "7.3.3", "7.5.1"])
    fun testProjectBuild(gradleVersion: String, @TempDir projectDir: File) {
        projectDir.resolve("build.gradle").writeText("plugins { id 'io.ktor.plugin' }")
        createGradleRunner(projectDir)
            .withGradleVersion(gradleVersion)
            .build()
    }

    @Test
    fun `test not fails when gradle is old and task is not GCC compatible`(@TempDir projectDir: File) {
        val gradleVersion = "7.3.3" // any version lower than 7.4

        // any incompatible with Gradle Configuration Cache task, see https://github.com/johnrengelman/shadow/issues/775
        val incompatibleTask = "runShadow"

        buildProject(
            projectDir = projectDir,
            buildGradleKtsContent = """
                plugins {
                    kotlin("jvm") version "1.7.0"
                    id("io.ktor.plugin")
                }
                repositories.mavenCentral()
                application.mainClass.set("my.org.MainKt")
            """.trimIndent(),
            settingsGradleKtsContent = "",
            mainKtContent = "package my.org\nfun main() {}",
            buildCommand = incompatibleTask,
            expectSuccess = true,
            gradleVersion = gradleVersion
        )
    }
}
