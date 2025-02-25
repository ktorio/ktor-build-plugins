package io.ktor.plugin

import io.ktor.plugin.features.*
import io.ktor.plugin.features.KtorExtension.Companion.DEVELOPMENT_MODE_PROPERTY
import io.ktor.plugin.internal.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication

const val KTOR_VERSION = "3.1.1"

@Suppress("unused") // Gradle Plugin is not used directly
abstract class KtorGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(KtorExtension.NAME, KtorExtension::class.java)
        configureApplication(project, extension)
        configureFatJar(project)
        configureDocker(project)
        configureBomFile(project)
        // Disabled until the native image generation is not possible with a single task with default configs
        // See https://youtrack.jetbrains.com/issue/KTOR-4596/Disable-Native-image-related-tasks
        // configureNativeImage(project)
    }

    private fun configureApplication(project: Project, extension: KtorExtension) = with(project) {
        plugins.apply(ApplicationPlugin::class.java)

        afterEvaluate {
            if (extension.development.get()) application.enableDevelopmentMode()
        }
    }
}

private fun JavaApplication.enableDevelopmentMode() {
    val prefix = "-D$DEVELOPMENT_MODE_PROPERTY="
    if (applicationDefaultJvmArgs.none { it.startsWith(prefix) }) {
        applicationDefaultJvmArgs += "${prefix}true"
    }
}
