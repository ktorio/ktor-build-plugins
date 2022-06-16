import io.ktor.plugin.features.*

plugins {
    @Suppress("DSL_SCOPE_VIOLATION") // "libs" produces a false-positive warning, see https://youtrack.jetbrains.com/issue/KTIJ-19369
    id(libs.plugins.ktor.get().pluginId)
}

application.mainClass.set("io.ktor.samples.docker.ApplicationKt")

ktor {
    docker {
        jreVersion.set(JreVersion.JRE_17)
        localImageName.set("sample-docker-image")
        imageTag.set("my-docker-sample")

        externalRegistry.set(
            DockerImageRegistry.dockerHub(
                appName = provider { "ktor-app" },
                username = provider { System.getenv("DOCKER_HUB_USERNAME") },
                password = provider { System.getenv("DOCKER_HUB_PASSWORD") }
            )
        )
    }
}
