package io.ktor.plugin

import io.ktor.plugin.features.*
import io.ktor.plugin.internal.*
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import java.io.File

internal fun Project.applyKtorPlugin(configureExt: KtorExtension.() -> Unit = {}) {
    apply<KotlinPluginWrapper>()
    apply<KtorGradlePlugin>()
    project.extensions.configure(KtorExtension::class.java) { ext ->
        ext.configureExt()
    }
}

internal fun Project.evaluate() {
    (this as ProjectInternal).evaluate()
}

internal fun createGradleRunner(projectDir: File): GradleRunner =
    GradleRunner.create().withProjectDir(projectDir).withArguments("--stacktrace")

internal fun createProject(configure: ProjectBuilder.() -> Unit = {}): Project =
    ProjectBuilder.builder().apply(configure).build()

internal fun File.writeKotlin(@Language("kt") code: String) = writeCodeBlocks(code)

// .gradle.kts language injection is not supported, but maybe some day it will be
// https://youtrack.jetbrains.com/issue/KTIJ-23863/Support-Language-injection-for-Gradle-Kotlin-DSL-kts-scripts
internal fun File.writeGradle(@Language("gradle.kts") vararg codeBlocks: String) = writeCodeBlocks(*codeBlocks)

internal fun File.writeCodeBlocks(vararg codeBlocks: String) {
    writeText(codeBlocks.joinToString("\n\n") { it.trimIndent() })
}
