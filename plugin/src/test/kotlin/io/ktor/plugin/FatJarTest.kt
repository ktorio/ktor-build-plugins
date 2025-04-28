package io.ktor.plugin

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.ktor.plugin.features.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class FatJarTest {
    @Test
    fun `output filename is built from the project name by default`() {
        val project = createProject { withName("fat-example") }
        project.applyKtorPlugin()

        val shadowJarTask = project.tasks.named("shadowJar", ShadowJar::class.java)
        assertEquals("fat-example-all.jar", shadowJarTask.get().archiveFileName.get())
    }

    @Test
    fun `output filename is overridden by the extension`() {
        val project = createProject()
        project.applyKtorPlugin()

        project.extensions.configure(KtorExtension::class.java) {
            it.getExtension<FatJarExtension>().archiveFileName.set("fat.jar")
        }
        val shadowJarTask = project.tasks.named("shadowJar", ShadowJar::class.java).get()
        assertEquals("fat.jar", shadowJarTask.archiveFileName.get())
    }

    @Test
    fun `has buildFatJar task that just depends on shadowJar task`() {
        val project = createProject()
        project.applyKtorPlugin()

        val buildTask = project.tasks.named("buildFatJar").get()
        val dependencies = buildTask.taskDependencies.getDependencies(buildTask)
        assertEquals(1, dependencies.size)
        assertEquals("shadowJar", dependencies.first().name)
    }

    @Test
    fun `runShadow task depends on buildFatJar task`() {
        val project = createProject()
        project.applyKtorPlugin()

        val runTask = project.tasks.named("runShadow").get()
        val dependencies = runTask.taskDependencies.getDependencies(runTask)
        assertNotNull(dependencies.find { it.name == "buildFatJar" }, "runShadow task should depend on buildFatJar")
    }

    @Test
    fun `has runFatJar task that depends on runShadow task`() {
        val project = createProject()
        project.applyKtorPlugin()

        val runTask = project.tasks.named("runFatJar").get()
        assertEquals("Ktor", runTask.group)
        assertFalse { runTask.description.isNullOrEmpty() }

        val dependencies = runTask.taskDependencies.getDependencies(runTask)
        assertEquals(1, dependencies.size)
        assertNotNull(dependencies.first(), "runShadow task should depend on buildFatJar")
    }
}
