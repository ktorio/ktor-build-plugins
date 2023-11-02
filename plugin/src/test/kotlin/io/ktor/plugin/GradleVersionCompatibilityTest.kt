package io.ktor.plugin

import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class GradleVersionCompatibilityTest {
    @ParameterizedTest
    @ValueSource(strings = ["7.3.3", "7.5.1", "7.6"])
    fun testProjectBuild(gradleVersion: String, @TempDir projectDir: File) {
        projectDir.resolve("build.gradle").writeText("plugins { id 'io.ktor.plugin' }")
        createGradleRunner(projectDir)
            .withGradleVersion(gradleVersion)
            .build()
    }
}
