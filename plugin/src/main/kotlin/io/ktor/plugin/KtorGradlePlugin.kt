package io.ktor.plugin

import io.ktor.plugin.features.*
import org.gradle.api.Plugin
import org.gradle.api.Project

const val KTOR_VERSION = "3.1.3"

@Suppress("unused") // Gradle Plugin is not used directly
abstract class KtorGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(KtorExtension.NAME, KtorExtension::class.java)
        project.configureApplication(extension)
        configureFatJar(project)
        configureDocker(project)
        configureBomFile(project)
        // Disabled until the native image generation is not possible with a single task with default configs
        // See https://youtrack.jetbrains.com/issue/KTOR-4596/Disable-Native-image-related-tasks
        // configureNativeImage(project)
    }
}
