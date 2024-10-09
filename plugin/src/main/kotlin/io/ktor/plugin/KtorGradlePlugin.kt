package io.ktor.plugin

import io.ktor.plugin.features.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin

const val KTOR_VERSION = "3.0.0"

@Suppress("unused") // Gradle Plugin is not used directly
abstract class KtorGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(ApplicationPlugin::class.java)
        configureFatJar(project)
        configureDocker(project)
        configureBomFile(project)
        // Disabled until the native image generation is not possible with a single task with default configs
        // See https://youtrack.jetbrains.com/issue/KTOR-4596/Disable-Native-image-related-tasks
        // configureNativeImage(project)
    }
}
