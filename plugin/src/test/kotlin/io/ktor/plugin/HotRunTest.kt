package io.ktor.plugin

import io.ktor.plugin.features.HotswapAgentJarMerger
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HotRunTest {

    @field:TempDir
    lateinit var tempDir: File

    @Test
    fun `has runHot task`() {
        val project = createProject()
        project.applyKtorPlugin()

        val runHotTask = project.tasks.named("runHot").get()
        assertEquals("Ktor", runHotTask.group)
    }

    @Test
    fun `merge preserves the original agent manifest and adds the bundled plugin classes`() {
        val agentJar = writeAgentJar(fakePremainClass = "com.example.Agent")
        val outputDir = File(tempDir, "out")

        val mergedJar = HotswapAgentJarMerger.merge(agentJar, outputDir)

        JarFile(mergedJar).use { jar ->
            assertEquals("com.example.Agent", jar.manifest.mainAttributes.getValue("Premain-Class"))
            assertNotNull(
                jar.getJarEntry("org/hotswap/agent/plugin/ktor/KtorReloadPlugin.class"),
                "merged jar is missing the bundled KtorReloadPlugin class",
            )
            assertNotNull(
                jar.getJarEntry("org/example/Agent.class"),
                "merged jar is missing the original agent's own class entries",
            )
        }
    }

    @Test
    fun `merge overwrites a previously merged jar rather than reusing stale content`() {
        val outputDir = File(tempDir, "out")
        val firstJar = HotswapAgentJarMerger.merge(writeAgentJar(fakePremainClass = "com.example.Agent"), outputDir)
        assertEquals("com.example.Agent", JarFile(firstJar).use { it.manifest.mainAttributes.getValue("Premain-Class") })

        val secondJar = HotswapAgentJarMerger.merge(writeAgentJar(fakePremainClass = "com.example.OtherAgent"), outputDir)

        assertEquals(firstJar, secondJar)
        assertEquals(
            "com.example.OtherAgent",
            JarFile(secondJar).use { it.manifest.mainAttributes.getValue("Premain-Class") },
            "merge must reflect the latest agent/plugin jars, not a stale cached result from a prior call",
        )
    }

    private fun writeAgentJar(fakePremainClass: String): File {
        val jarFile = File(tempDir, "agent-${fakePremainClass.hashCode()}.jar")
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Premain-Class", fakePremainClass)
        }
        JarOutputStream(jarFile.outputStream(), manifest).use { jar ->
            jar.putNextEntry(java.util.zip.ZipEntry("org/example/Agent.class"))
            jar.write(byteArrayOf(1, 2, 3))
            jar.closeEntry()
        }
        return jarFile
    }
}
