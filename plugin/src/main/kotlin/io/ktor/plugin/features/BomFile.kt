package io.ktor.plugin.features

import io.ktor.plugin.*
import org.gradle.api.Project

internal fun Project.configureBomFile() {
    with(dependencies) {
        add("implementation", platform("io.ktor:ktor-bom:$KTOR_VERSION"))
    }
}
