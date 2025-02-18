package io.ktor.plugin.internal

import org.gradle.api.Project
import org.gradle.api.plugins.JavaApplication

internal val Project.application: JavaApplication
    get() = extensions.getByType(JavaApplication::class.java)

internal fun Project.application(configure: JavaApplication.() -> Unit) {
    extensions.configure(JavaApplication::class.java, configure)
}
