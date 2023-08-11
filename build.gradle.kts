import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.kotlin.jvm)
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
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
