package io.ktor.plugin.features

import io.ktor.plugin.*
import io.ktor.plugin.internal.*
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

fun configureBomFile(project: Project) = with(project) {
    whenKotlinJvmApplied {
        dependencies.add("implementation", dependencies.ktorBom)
    }

    whenKotlinMultiplatformApplied {
        with(kotlinExtension as KotlinMultiplatformExtension) {
            sourceSets.commonMain.dependencies {
                implementation(dependencies.ktorBom)
            }
        }
    }
}

private val DependencyHandler.ktorBom: Dependency
    get() = platform("io.ktor:ktor-bom:$KTOR_VERSION")
