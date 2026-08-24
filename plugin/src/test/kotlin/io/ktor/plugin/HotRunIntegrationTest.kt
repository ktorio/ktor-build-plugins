package io.ktor.plugin

import org.junit.jupiter.api.Test
import kotlin.test.assertContains

class HotRunIntegrationTest : IntegrationTest() {

    @Test
    fun `embeddedServerAccessor property is forwarded as a system property on runHot`() {
        buildFile.writeGradle(
            APPLY_KOTLIN_JVM_AND_KTOR,
            """
            application {
                mainClass.set("MainKt")
            }

            tasks.register("printAccessorProperty") {
                doLast {
                    val runHot = tasks.named("runHot", org.gradle.api.tasks.JavaExec::class.java).get()
                    println("ACCESSOR=" + runHot.systemProperties["io.ktor.hotReload.embeddedServerAccessor"])
                }
            }
            """,
        )

        val result = runBuild(
            "printAccessorProperty",
            "-Pio.ktor.hotReload.embeddedServerAccessor=MainKt#getEmbeddedServer",
        )

        assertContains(result.output, "ACCESSOR=MainKt#getEmbeddedServer")
    }

    @Test
    fun `embeddedServerAccessor system property is absent when the property is not set`() {
        buildFile.writeGradle(
            APPLY_KOTLIN_JVM_AND_KTOR,
            """
            application {
                mainClass.set("MainKt")
            }

            tasks.register("printAccessorProperty") {
                doLast {
                    val runHot = tasks.named("runHot", org.gradle.api.tasks.JavaExec::class.java).get()
                    println("ACCESSOR=" + runHot.systemProperties["io.ktor.hotReload.embeddedServerAccessor"])
                }
            }
            """,
        )

        val result = runBuild("printAccessorProperty")

        assertContains(result.output, "ACCESSOR=null")
    }
}
