package io.ktor.plugin

import io.ktor.plugin.extension.*
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused") // Gradle Plugin is not used directly
abstract class KtorGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        configureFatJar(project)
        configureDocker(project)
    }
}