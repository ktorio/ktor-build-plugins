import org.gradle.api.tasks.testing.logging.TestLogEvent

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

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging.events(*TestLogEvent.values())
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.logback)
}
