package io.ktor.plugin

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert
import org.junit.Test

class KtorGradlePluginTest {
    @Test
    fun `plugin exists`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.ktor.ktor-gradle-plugin")
    }

    @Test
    fun `plugin creates a new task named buildFatJar`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.ktor.ktor-gradle-plugin")
        val task = project.tasks.findByName("buildFatJar")
        Assert.assertNotNull(task)
    }
}