package io.ktor.plugin

import io.ktor.plugin.features.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ApplicationPlugin

const val KTOR_VERSION = "2.3.2"

@Suppress("unused") // Gradle Plugin is not used directly
abstract class KtorGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(ApplicationPlugin::class.java)
        configureFatJar(project)
        configureDocker(project)
        configureBomFile(project)
        configureNativeImage(project)
    }
}
