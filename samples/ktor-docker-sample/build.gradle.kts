import io.ktor.plugin.extension.*

plugins {
    @Suppress("DSL_SCOPE_VIOLATION") // "libs" produces a false-positive warning, see https://youtrack.jetbrains.com/issue/KTIJ-19369
    id(libs.plugins.ktor.get().pluginId)
}

application.mainClass.set("io.ktor.samples.docker.ApplicationKt")

ktor {
    docker {
        jreVersion = JreVersion.JRE_17
        localImageName = "sample-docker-image"
        imageTag = "my-docker-sample"

        // Uncomment externalRepository if you want to publish your image to the registry
//        externalRegistry = DockerImageRegistry.dockerHub(
//            appName = "ktor-app",
//            username = System.getenv("DOCKER_HUB_USERNAME"),
//            password = System.getenv("DOCKER_HUB_PASSWORD")
//        )
    }
}
