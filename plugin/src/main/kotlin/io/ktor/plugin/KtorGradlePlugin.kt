package io.ktor.plugin

import io.ktor.plugin.features.*
import io.ktor.plugin.internal.*
import io.ktor.plugin.internal.KotlinPluginType.*
import org.gradle.api.Plugin
import org.gradle.api.Project

public abstract class KtorGradlePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        val extension = extensions.create(KtorExtension.NAME, KtorExtension::class.java)
        configureApplication(extension)
        configureFatJar()
        configureDocker()
        configureBomFile()
        // Disabled until the native image generation is not possible with a single task with default configs
        // See https://youtrack.jetbrains.com/issue/KTOR-4596/Disable-Native-image-related-tasks
        // configureNativeImage()

        var kotlinPluginApplied = false
        whenKotlinPluginApplied { pluginType ->
            if (pluginType == Multiplatform) reportKmpCompatibilityWarning()
            kotlinPluginApplied = true
        }

        afterEvaluate {
            if (!kotlinPluginApplied) reportKotlinPluginMissingWarning()
        }
    }

    private fun Project.reportKmpCompatibilityWarning() {
        logger.warn("warning: The Ktor Gradle plugin is not fully compatible with the Kotlin Multiplatform plugin.")
        logger.lifecycle(
            """
            |
            |Building a fat JAR or a Docker image for a Ktor application is not supported when the plugin is applied to a multiplatform project.
            |As a workaround, create a JVM-only project with the Ktor plugin applied and add the multiplatform project as a dependency.
            |If this limitation affects your use case, let us know by commenting on the issue: https://youtrack.jetbrains.com/issue/KTOR-8464
            |
            """.trimMargin()
        )
    }

    private fun Project.reportKotlinPluginMissingWarning() {
        logger.warn("warning: The Ktor Gradle plugin requires the Kotlin Gradle plugin.")
        logger.lifecycle(
            """
            |To fix this, apply the Kotlin Gradle plugin to your project:
            |
            |  plugins {
            |      kotlin("jvm")
            |  }
            |
            """.trimMargin()
        )
    }

    public companion object {
        /** The Ktor plugin version. Usually it is equal to the Ktor version used in a project. */
        public const val VERSION: String = "3.2.0"

        /** The group name used for Ktor tasks. */
        public const val TASK_GROUP: String = "Ktor"
    }
}

@Deprecated(
    "Use KtorGradlePlugin.VERSION instead",
    ReplaceWith(
        "KtorGradlePlugin.VERSION",
        "io.ktor.plugin.KtorGradlePlugin",
    )
)
public const val KTOR_VERSION: String = KtorGradlePlugin.VERSION
