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
    fun `plugin creates a new task named buildShadowJar`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.ktor.ktor-gradle-plugin")
        val buildShadowJar = project.tasks.findByName("buildShadowJar")
        Assert.assertNotNull(buildShadowJar)
    }

    @Test
    fun `task buildShadowJar depends on shadowJar`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.ktor.ktor-gradle-plugin")
        val buildShadowJar = requireNotNull(project.tasks.findByName("buildShadowJar"))
        Assert.assertTrue(buildShadowJar.dependsOn.any { it == "shadowJar" })
    }
}