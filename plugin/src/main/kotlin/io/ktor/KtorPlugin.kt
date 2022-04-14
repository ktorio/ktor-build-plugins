package io.ktor

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused") // Gradle Plugin is not used directly
abstract class KtorPlugin : Plugin<Project> {
    override fun apply(project: Project) = println("Ktor plugin is loaded")
}