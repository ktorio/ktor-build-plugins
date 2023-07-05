import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
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
}
