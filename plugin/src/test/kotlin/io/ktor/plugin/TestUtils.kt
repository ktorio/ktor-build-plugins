package io.ktor.plugin

import io.ktor.plugin.features.*
import org.gradle.api.Project
import org.gradle.testkit.runner.GradleRunner
import java.io.File

fun Project.applyPlugin(configureExt: KtorExtension.() -> Unit = {}) {
    project.plugins.apply(KtorGradlePlugin::class.java)
    project.extensions.configure(KtorExtension::class.java) { ext ->
        ext.configureExt()
    }
}

fun createGradleRunner(projectDir: File): GradleRunner =
    GradleRunner.create().withProjectDir(projectDir).withPluginClasspath()
