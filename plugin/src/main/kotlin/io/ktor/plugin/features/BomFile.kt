package io.ktor.plugin.features

import io.ktor.plugin.*
import io.ktor.plugin.internal.*
import io.ktor.plugin.internal.KotlinPluginType.*
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

internal fun Project.configureBomFile() {
    whenKotlinPluginApplied { pluginType ->
        when (pluginType) {
            JVM -> configureJvmDependency()
            Multiplatform -> configureMultiplatformDependency()
        }
    }
}

private fun Project.configureJvmDependency() {
    dependencies.add("implementation", dependencies.ktorBom)
}

private fun Project.configureMultiplatformDependency() {
    with(kotlinExtension as KotlinMultiplatformExtension) {
        sourceSets.commonMain.dependencies {
            implementation(dependencies.ktorBom)
        }
    }
}

private val DependencyHandler.ktorBom: Dependency
    get() = platform("io.ktor:ktor-bom:${KtorGradlePlugin.KTOR_VERSION}")
