import io.ktor.plugin.features.*
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id(libs.plugins.ktor.get().pluginId)
    kotlin("jvm")
}

application.mainClass.set("io.ktor.samples.docker.ApplicationKt")

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("sample-docker-image")
        imageTag.set("my-docker-sample")

        externalRegistry.set(
            DockerImageRegistry.dockerHub(
                appName = provider { "ktor-app" },
                username = providers.environmentVariable("DOCKER_HUB_USERNAME"),
                password = providers.environmentVariable("DOCKER_HUB_PASSWORD")
            )
        )

        environmentVariable("NAME", "\"Container\"")
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