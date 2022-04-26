package io.ktor.plugin

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class KtorGradlePluginTest {
    @Test
    fun `plugin exists`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.ktor.ktor-gradle-plugin")
    }
}
