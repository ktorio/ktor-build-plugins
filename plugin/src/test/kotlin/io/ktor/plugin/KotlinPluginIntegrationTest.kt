package io.ktor.plugin

import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertContains

class KotlinPluginIntegrationTest : IntegrationTest() {

    @Test
    fun `applied after kotlin-jvm plugin`() {
        buildFile.writeGradle(
            APPLY_KOTLIN_JVM_AND_KTOR,
            PRINT_KTOR_TASKS,
        )

        runBuild().assertKtorTasksAdded(allTasks)
    }

    @Test
    fun `applied before kotlin-jvm plugin`() {
        buildFile.writeGradle(
            """
            plugins {
                id("io.ktor.plugin") version "$KTOR_VERSION"
                kotlin("jvm") version "$KOTLIN_VERSION"
            }
            """,
            PRINT_KTOR_TASKS,
        )

        runBuild().assertKtorTasksAdded(allTasks)
    }

    @Test
    fun `applied with kotlin-multiplatform plugin before 2-1-20`() {
        buildFile.writeGradle(
            """
            plugins {
                kotlin("multiplatform") version "2.1.0"
                id("io.ktor.plugin") version "$KTOR_VERSION"
            }

            kotlin {
                jvm()
            }
            """,
            PRINT_KTOR_TASKS,
        )

        runBuild().assertKtorTasksAdded(fatJarTasks)
    }

    @Test
    fun `applied with kotlin-multiplatform plugin after 2-1-20`() {
        buildFile.writeGradle(
            """
            plugins {
                kotlin("multiplatform") version "$KOTLIN_VERSION"
                id("io.ktor.plugin") version "$KTOR_VERSION"
            }

            kotlin {
                jvm()
            }
            """,
            PRINT_KTOR_TASKS,
        )

        runBuild().assertKtorTasksAdded(emptySet())
    }

    @ParameterizedTest
    @CsvSource(
        "true,  [-Dio.ktor.development=true]",
        "false, []",
    )
    fun `applied with kotlin-multiplatform plugin with executions and development mode configured`(
        development: Boolean,
        expectedArgs: String
    ) {
        file("src/jvmMain/kotlin/Main.kt").writeKotlin(
            """
            fun main() {
                println("Hello, world!")
            }
            """
        )

        buildFile.writeGradle(
            """
            import org.gradle.api.tasks.JavaExec

            plugins {
                kotlin("multiplatform") version "$KOTLIN_VERSION"
                id("io.ktor.plugin") version "$KTOR_VERSION"
            }

            kotlin {
                jvm {
                    binaries {
                        executable {
                            mainClass = "MainKt"
                        }
                    }
                }
            }
            
            ktor {
                development = $development
            }

            afterEvaluate {
                val runJvm = tasks.named<JavaExec>("runJvm").get()
                println("JVM args: " + runJvm.jvmArguments.get())
            }
            """,
        )

        val result = runBuild()
        assertContains(result.output.lines(), "JVM args: $expectedArgs")
    }

    private companion object {
        const val PRINT_KTOR_TASKS = """
            afterEvaluate {
               val tasks = tasks.filter { it.group == "Ktor" }
               println("Ktor tasks: " + tasks.map { it.name }.sorted())
            }
        """

        val fatJarTasks = setOf("buildFatJar", "runFatJar")
        val jibTasks = setOf("buildImage", "publishImage", "publishImageToLocalRegistry", "runDocker")
        val allTasks = fatJarTasks + jibTasks

        /** Should be used together with [PRINT_KTOR_TASKS]. */
        fun BuildResult.assertKtorTasksAdded(tasks: Set<String>) {
            assertContains(output.lines(), "Ktor tasks: " + tasks.sorted())
        }
    }
}
