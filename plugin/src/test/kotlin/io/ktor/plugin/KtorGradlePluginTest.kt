package io.ktor.plugin

import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
            "buildNativeImage" // KTOR-4596 Disable Native image related tasks
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
        assertTrue(project.plugins.hasPlugin(ApplicationPlugin::class.java))
    }

    @Test
    fun `plugin does not add any dependencies except the bom file`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.ktor.plugin")
        val deps = project.configurations.flatMap { it.dependencies }
        assertEquals(1, deps.size)
        val bom = deps.single()
        assertEquals("io.ktor", bom.group)
        assertEquals("ktor-bom", bom.name)
        assertEquals(KTOR_VERSION, bom.version)
    }
}
