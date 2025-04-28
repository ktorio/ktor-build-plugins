package io.ktor.plugin.internal

import org.gradle.api.Project

internal fun Project.whenKotlinMultiplatformApplied(block: () -> Unit) {
    plugins.withId(KMP_PLUGIN_ID) { block() }
}

internal fun Project.whenKotlinJvmApplied(block: () -> Unit) {
    plugins.withId(KOTLIN_JVM_PLUGIN_ID) { block() }
}
