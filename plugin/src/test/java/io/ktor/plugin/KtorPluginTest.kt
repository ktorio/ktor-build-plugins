package io.ktor.plugin

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class KtorPluginTest {
    @Test
    fun `plugin exists`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.ktor.plugin")
    }
}
