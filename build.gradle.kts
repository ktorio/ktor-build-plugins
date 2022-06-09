import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm") version "1.6.21" apply false
    kotlin("plugin.serialization") version "1.6.21" apply false
}

subprojects {
    repositories.mavenCentral()
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging.events(*TestLogEvent.values())
    }
}
