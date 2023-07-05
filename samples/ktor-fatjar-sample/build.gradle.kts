plugins {
    id(libs.plugins.ktor.get().pluginId)
}

application.mainClass.set("io.ktor.samples.fatjar.ApplicationKt")

ktor {
    dependencies {
        implementation(rootProject.libs.ktor.server.core)
        implementation(rootProject.libs.ktor.server.cio)
        implementation(rootProject.libs.logback)
    }

    fatJar {
        archiveFileName.set("fat.jar")
    }
}
