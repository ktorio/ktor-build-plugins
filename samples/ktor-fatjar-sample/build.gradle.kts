plugins {
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.jvm)
}

application.mainClass = "io.ktor.samples.fatjar.ApplicationKt"

ktor {
    fatJar {
        archiveFileName = "fat.jar"
    }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.logback)
}
