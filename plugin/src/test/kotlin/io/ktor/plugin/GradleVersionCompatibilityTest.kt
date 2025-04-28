package io.ktor.plugin

import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class GradleVersionCompatibilityTest {
    @ParameterizedTest
    @ValueSource(strings = ["8.3", "8.14"])
    fun testProjectBuild(gradleVersion: String, @TempDir projectDir: File) {
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.1.20"
                id("io.ktor.plugin")
            }
            """.trimIndent()
        )
        createGradleRunner(projectDir)
            .withGradleVersion(gradleVersion)
            .build()
    }
}
