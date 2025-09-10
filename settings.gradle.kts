@file:Suppress("UnstableApiUsage")

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

pluginManagement {
    includeBuild("plugin")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

includeBuild("build-logic")

include("compiler-plugin")
include("samples:ktor-fatjar-sample")
include("samples:ktor-docker-sample")
include("samples:ktor-openapi-sample")
//include("samples:ktor-native-image-sample") // KTOR-4596 Disable Native image related tasks
