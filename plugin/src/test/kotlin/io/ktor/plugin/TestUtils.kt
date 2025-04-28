package io.ktor.plugin

import io.ktor.plugin.features.*
import io.ktor.plugin.internal.*
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import java.io.File

internal fun Project.applyKtorPlugin(configureExt: KtorExtension.() -> Unit = {}) {
    project.plugins.apply<KotlinPluginWrapper>()
    project.plugins.apply<KtorGradlePlugin>()
    project.extensions.configure(KtorExtension::class.java) { ext ->
        ext.configureExt()
    }
}

internal fun Project.evaluate() {
    (this as ProjectInternal).evaluate()
}

internal fun createGradleRunner(projectDir: File): GradleRunner =
    GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments("--stacktrace")

internal fun createProject(configure: ProjectBuilder.() -> Unit = {}): Project =
    ProjectBuilder.builder().apply(configure).build()
