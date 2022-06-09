package io.ktor.plugin

import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KtorGradlePluginTest {
    @Test
    fun `plugin exists`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.ktor.plugin")
    }

    @Test
    fun `plugin creates all public tasks`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.ktor.plugin")
        val expectedTasks = listOf(
            "buildFatJar",
            "publishImageToLocalRegistry", "publishImage", "buildImage", "runDocker",
            "buildNativeImage"
        )
        for (taskName in expectedTasks) {
            val task = assertNotNull(project.tasks.findByName(taskName), "Task $taskName not found")
            assertEquals("Ktor", task.group)
        }
    }

    @Test
    fun `plugin applies application plugin`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.ktor.plugin")
        project.plugins.hasPlugin(ApplicationPlugin::class.java)
    }
}
