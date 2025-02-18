package io.ktor.plugin

import io.ktor.plugin.features.*
import org.gradle.api.Project

internal fun Project.ktor(configure: KtorExtension.() -> Unit) {
    extensions.configure(KtorExtension::class.java, configure)
}
