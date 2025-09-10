plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    // resolve compiler plugin from project files
    configurations.all {
        resolutionStrategy.dependencySubstitution {
            substitute(
                module("io.ktor:ktor-compiler-plugin")
            ).using(
                project(":ktor-compiler-plugin")
            )
        }
    }
}