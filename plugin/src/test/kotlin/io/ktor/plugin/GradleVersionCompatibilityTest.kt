package io.ktor.plugin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class GradleVersionCompatibilityTest {
    private fun buildProject(gradleVersion: String, projectDir: File, expectSuccess: Boolean) {
        projectDir.resolve("build.gradle").writeText("plugins { id 'io.ktor.plugin' }")
        createGradleRunner(projectDir)
            .withGradleVersion(gradleVersion)
            .run { if (expectSuccess) build() else buildAndFail() }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "6.7",
//            "6.7.1", "6.8", "6.8.1", "6.8.2", "6.8.3", "6.9", "6.9.1", "6.9.2",
//            "7.0", "7.0.1", "7.0.2", "7.1", "7.1.1", "7.2", "7.3", "7.3.1", "7.3.2", "7.3.3", "7.4", "7.4.1",
            "7.4.2"
        ]
    )
    fun testProjectBuild(gradleVersion: String, @TempDir projectDir: File) =
        buildProject(gradleVersion, projectDir, true)

    @Test
    fun testProjectBuildFailsOnOlderVersions(@TempDir projectDir: File) =
        buildProject("6.6.1", projectDir, false)
}
