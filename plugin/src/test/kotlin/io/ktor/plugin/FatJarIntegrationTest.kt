package io.ktor.plugin

import org.junit.jupiter.api.Test
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.test.assertContains

class FatJarIntegrationTest : IntegrationTest() {

    @Test
    fun `buildFatJar merges META-INF service files from dependencies`() {
        val serviceFile = "META-INF/services/com.example.MyService"
        writeJarWithEntry(file("libs/lib-a.jar"), serviceFile, "com.example.ProviderA\n")
        writeJarWithEntry(file("libs/lib-b.jar"), serviceFile, "com.example.ProviderB\n")

        buildFile.writeGradle(
            APPLY_KOTLIN_JVM_AND_KTOR,
            """
            repositories {
                mavenCentral()
            }

            application {
                mainClass.set("MainKt")
            }

            dependencies {
                implementation(files("libs/lib-a.jar", "libs/lib-b.jar"))
            }
            """,
        )

        runBuild("buildFatJar")

        val fatJar = projectDir.resolve("build/libs/test-all.jar")
        val providers = JarFile(fatJar).use { jar ->
            val entry = jar.getJarEntry(serviceFile)
                ?: error("$serviceFile is missing from the fat jar")
            jar.getInputStream(entry).bufferedReader().readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        assertContains(providers, "com.example.ProviderA")
        assertContains(providers, "com.example.ProviderB")
    }

    private fun writeJarWithEntry(jarFile: File, @Suppress("SameParameterValue") entryName: String, content: String) {
        JarOutputStream(jarFile.outputStream()).use { jos ->
            jos.putNextEntry(JarEntry(entryName))
            jos.write(content.toByteArray(Charsets.UTF_8))
            jos.closeEntry()
        }
    }
}
