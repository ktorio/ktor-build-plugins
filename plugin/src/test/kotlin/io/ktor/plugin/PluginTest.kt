package io.ktor.plugin

import io.ktor.plugin.internal.*
import org.gradle.api.plugins.ApplicationPlugin
import kotlin.test.*

class PluginTest {

    private val project = createProject()

    @BeforeTest
    fun setup() {
        project.applyKtorPlugin()
    }

    @Test
    fun `plugin creates all public tasks`() {
        val expectedTasks = listOf(
            "buildFatJar",
            "publishImageToLocalRegistry", "publishImage", "buildImage", "runDocker",
//            "buildNativeImage" // KTOR-4596 Disable Native image related tasks
        )
        for (taskName in expectedTasks) {
            val task = assertNotNull(project.tasks.findByName(taskName), "Task $taskName not found")
            assertEquals("Ktor", task.group)
        }
    }

    @Test
    fun `plugin applies application plugin`() {
        assertTrue(project.plugins.hasPlugin(ApplicationPlugin::class.java))
    }

    @Test
    fun `plugin adds development mode in application args`() = with(project) {
        ktor {
            development.set(true)
        }

        application {
            applicationDefaultJvmArgs = listOf("-Dsome.prop=value")
        }

        evaluate()
        assertContentEquals(
            listOf("-Dsome.prop=value", "-Dio.ktor.development=true"),
            application.applicationDefaultJvmArgs
        )
    }

    @Test
    fun `plugin uses system property as the default value for development mode`() = with(project) {
        System.setProperty("io.ktor.development", "true")

        evaluate()
        assertEquals("-Dio.ktor.development=true", application.applicationDefaultJvmArgs.single())
    }

    @Test
    fun `plugin doesn't override development mode if it was specified manually before`() = with(project) {
        application {
            applicationDefaultJvmArgs = listOf("-Dio.ktor.development=false")
        }

        ktor {
            development.set(true)
        }

        evaluate()
        assertEquals("-Dio.ktor.development=false", application.applicationDefaultJvmArgs.single())
    }

    @Test
    fun `plugin doesn't override development mode if it was specified manually after`() = with(project) {
        ktor {
            development.set(true)
        }

        application {
            applicationDefaultJvmArgs = listOf("-Dio.ktor.development=false")
        }

        evaluate()
        assertEquals("-Dio.ktor.development=false", application.applicationDefaultJvmArgs.single())
    }

    @Test
    fun `plugin does not add any dependencies except the bom file`() {
        val deps = project.configurations
            .filter { it.name != "kotlinBuildToolsApiClasspath" }
            .flatMap { it.dependencies }
        assertEquals(1, deps.size, "Expected only the Ktor BOM dependency, but got: $deps")
        val bom = deps.single()
        assertEquals("io.ktor", bom.group)
        assertEquals("ktor-bom", bom.name)
        assertEquals(KTOR_VERSION, bom.version)
    }
}
