package io.ktor.plugin

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused") // Gradle Plugin is not used directly
abstract class KtorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        println("Ktor plugin is loaded")
        project.plugins.apply(ShadowPlugin::class.java)
        project.tasks.create("buildShadowJar") {
            it.dependsOn("shadowJar")
        }
    }
}