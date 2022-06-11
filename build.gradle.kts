import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    @Suppress("DSL_SCOPE_VIOLATION") // "libs" produces a false-positive warning, see https://youtrack.jetbrains.com/issue/KTIJ-19369
    alias(libs.plugins.kotlin.jvm)
}

repositories.mavenCentral()

subprojects {
    repositories.mavenCentral()
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging.events(*TestLogEvent.values())
    }
    apply(plugin = rootProject.libs.plugins.kotlin.jvm.get().pluginId)
    dependencies {
        implementation(rootProject.libs.ktor.server.core)
        implementation(rootProject.libs.ktor.server.cio)
        implementation(rootProject.libs.logback)
    }
}
