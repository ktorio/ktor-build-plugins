import io.ktor.plugin.features.*

plugins {
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.jvm)
}

application.mainClass = "io.ktor.samples.docker.ApplicationKt"

ktor {
    docker {
        jreVersion = JavaVersion.VERSION_17
        localImageName = "sample-docker-image"
        imageTag = "my-docker-sample"

        externalRegistry = DockerImageRegistry.dockerHub(
            appName = provider { "ktor-app" },
            username = providers.environmentVariable("DOCKER_HUB_USERNAME"),
            password = providers.environmentVariable("DOCKER_HUB_PASSWORD")
        )

        environmentVariable("NAME", "\"Container\"")
    }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.logback)
}