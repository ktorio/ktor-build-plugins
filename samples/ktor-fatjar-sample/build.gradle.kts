import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.jvm)
}

application.mainClass.set("io.ktor.samples.fatjar.ApplicationKt")

ktor {
    fatJar {
        archiveFileName.set("fat.jar")
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
