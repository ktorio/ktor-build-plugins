@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("plugin")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        mavenCentral()
    }
}

include("samples:ktor-fatjar-sample")
include("samples:ktor-docker-sample")
//include("samples:ktor-native-image-sample") // KTOR-4596 Disable Native image related tasks
