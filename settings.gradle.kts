pluginManagement {
    @Suppress("UnstableApiUsage")
    includeBuild("plugin")
}

include("samples:ktor-fatjar-sample")
include("samples:ktor-docker-sample")
//include("samples:ktor-native-image-sample") // KTOR-4596 Disable Native image related tasks
