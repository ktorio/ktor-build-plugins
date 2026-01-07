@file:Suppress("UnstableApiUsage")

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        mavenLocal()
    }
}

includeBuild("build-logic")
includeBuild("plugin")

include("ktor-compiler-plugin")
include("samples:ktor-fatjar-sample")
include("samples:ktor-docker-sample")
include("samples:ktor-openapi-sample")
//include("samples:ktor-native-image-sample") // KTOR-4596 Disable Native image related tasks
