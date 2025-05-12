package io.ktor.plugin

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class GradleVersionCompatibilityTest : IntegrationTest() {
    @ParameterizedTest
    @ValueSource(strings = ["8.3", "8.14"])
    fun testProjectBuild(gradleVersion: String) {
        runBuild { withGradleVersion(gradleVersion) }
    }
}
