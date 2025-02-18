package io.ktor.plugin

import io.ktor.plugin.features.*
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testkit.runner.GradleRunner
import java.io.File

internal fun Project.applyPlugin(configureExt: KtorExtension.() -> Unit = {}) {
    project.plugins.apply(KtorGradlePlugin::class.java)
    project.extensions.configure(KtorExtension::class.java) { ext ->
        ext.configureExt()
    }
}

internal fun Project.evaluate() {
    (this as ProjectInternal).evaluate()
}

internal fun createGradleRunner(projectDir: File): GradleRunner =
    GradleRunner.create().withProjectDir(projectDir).withPluginClasspath()
