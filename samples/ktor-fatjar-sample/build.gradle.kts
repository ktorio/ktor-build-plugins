import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id(libs.plugins.ktor.get().pluginId)
    kotlin("jvm")
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
    implementation(rootProject.libs.ktor.server.core)
    implementation(rootProject.libs.ktor.server.cio)
    implementation(rootProject.libs.logback)
}
