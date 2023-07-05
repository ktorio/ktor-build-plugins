import io.ktor.plugin.features.*

plugins {
    id(libs.plugins.ktor.get().pluginId)
}

application.mainClass.set("io.ktor.samples.docker.ApplicationKt")

ktor {
    dependencies {
        implementation(rootProject.libs.ktor.server.core)
        implementation(rootProject.libs.ktor.server.cio)
        implementation(rootProject.libs.logback)
    }

    docker {
        jreVersion.set(JreVersion.JRE_17)
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
