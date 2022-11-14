package io.ktor.plugin.features

import io.ktor.plugin.*
import org.gradle.api.Project

fun configureBomFile(project: Project) {
    with(project.dependencies) {
        add("implementation", platform("io.ktor:ktor-bom:$KTOR_VERSION"))
    }
}
