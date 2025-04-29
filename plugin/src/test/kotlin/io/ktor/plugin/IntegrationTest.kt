package io.ktor.plugin

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.BeforeTest

abstract class IntegrationTest {

    @field:TempDir
    protected lateinit var projectDir: File

    private val settingsFile by lazy { projectDir.resolve("settings.gradle.kts") }
    protected val buildFile by lazy { projectDir.resolve("build.gradle.kts") }

    @BeforeTest
    open fun setup() {
        settingsFile.writeCode(
            """
            pluginManagement {
                repositories {
                    mavenLocal {
                        content { includeGroup("io.ktor.plugin") }
                    }
                    gradlePluginPortal()
                }
            }

            rootProject.name = "test"
            """
        )
        buildFile.writeCode(APPLY_KOTLIN_JVM_AND_KTOR)
    }

    private val runner by lazy {
        createGradleRunner(projectDir)
            .forwardOutput()
    }

    protected fun runBuild(vararg args: String, configure: GradleRunner.() -> Unit = {}): BuildResult {
        return runner.apply(configure).withArguments(args.asList() + "--stacktrace").build()
    }

    companion object {
        const val APPLY_KOTLIN_JVM_AND_KTOR = """
            plugins {
                kotlin("jvm") version "$KOTLIN_VERSION"
                id("io.ktor.plugin") version "$KTOR_VERSION"
            }
        """
    }
}
