package io.ktor.plugin

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KtorGradlePluginTest {
    @Test
    fun `plugin exists`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.ktor.plugin")
    }

    @Test
    fun `plugin creates all public tasks`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.ktor.plugin")
        val expectedTasks = listOf(
            "buildFatJar",
            "publishImageToLocalRegistry", "publishImage", "buildImage", "runDocker",
            "buildNativeImage"
        )
        for (taskName in expectedTasks) {
            val task = requireNotNull(project.tasks.findByName(taskName)) { "Task $taskName not found" }
            assertEquals("Ktor", task.group)
        }
    }
}