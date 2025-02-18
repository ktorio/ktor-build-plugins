package io.ktor.plugin

import io.ktor.plugin.features.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin

const val KTOR_VERSION = "3.1.0"

@Suppress("unused") // Gradle Plugin is not used directly
abstract class KtorGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        extensions.create(KtorExtension.NAME, KtorExtension::class.java)
        configureApplication()
        configureFatJar()
        configureDocker()
        configureBomFile()
        // Disabled until the native image generation is not possible with a single task with default configs
        // See https://youtrack.jetbrains.com/issue/KTOR-4596/Disable-Native-image-related-tasks
        // configureNativeImage()
    }

    private fun Project.configureApplication() {
        plugins.apply(ApplicationPlugin::class.java)
    }
}
