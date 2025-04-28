package io.ktor.plugin.internal

import org.gradle.api.Project

internal fun Project.whenKotlinMultiplatformApplied(block: () -> Unit) {
    pluginManager.withPlugin(KMP_PLUGIN_ID) { block() }
}

internal fun Project.whenKotlinJvmApplied(block: () -> Unit) {
    pluginManager.withPlugin(KOTLIN_JVM_PLUGIN_ID) { block() }
}
