@Suppress("DSL_SCOPE_VIOLATION") // "libs" produces a false-positive warning, see https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    id(libs.plugins.ktor.get().pluginId)
}

application.mainClass.set("io.ktor.samples.fatjar.ApplicationKt")

ktor {
    fatJar {
        archiveFileName = "fat.jar"
    }
}
