package io.ktor.plugin

import io.ktor.plugin.features.*
import io.ktor.plugin.generated.BuildConfig
import io.ktor.plugin.internal.*
import io.ktor.plugin.internal.KotlinPluginType.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Provider

public abstract class KtorGradlePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        val extension = extensions.create(KtorExtension.NAME, KtorExtension::class.java)
        configureApplication(extension)
        configureFatJar()
        configureDocker()
        configureBomFile()
        configureOpenApi()
        // Disabled until the native image generation is not possible with a single task with default configs
        // See https://youtrack.jetbrains.com/issue/KTOR-4596/Disable-Native-image-related-tasks
        // configureNativeImage()

        var kotlinPluginApplied = false
        whenKotlinPluginApplied { pluginType ->
            if (pluginType == Multiplatform) reportKmpCompatibilityWarning()
            kotlinPluginApplied = true
        }

        afterEvaluate {
            if (!kotlinPluginApplied) {
                reportKotlinPluginMissingWarning()
                return@afterEvaluate
            }

            project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer::class.java).forEach { sourceSet ->
                if (sourceSet.name == "main") {
                    sourceSet.resources.srcDir(project.layout.ktorOutputDir)
                }
            }
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
        public const val VERSION: String = BuildConfig.VERSION

        /** The Ktor library version, used i
         *  the bom, this can differ for EAP's **/
        public const val KTOR_VERSION: String = BuildConfig.KTOR_VERSION

        /** The group name used for Ktor tasks. */
        public const val TASK_GROUP: String = "Ktor"

        /** The name of the compiler plugin */
        public const val COMPILER_PLUGIN_ID: String = "io.ktor:ktor-compiler-plugin"
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

public val ProjectLayout.ktorOutputDir: Provider<Directory>
    get() = buildDirectory.dir("ktor")

public val ConfigurationContainer.ktorCompilerPlugins: Configuration
    get() = maybeCreate("ktorCompilerPlugin")
