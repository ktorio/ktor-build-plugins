pluginManagement {
    @Suppress("UnstableApiUsage")
    includeBuild("plugin")
}

include("samples:fatjar")
include("samples:ktor-docker-sample")
include("samples:ktor-native-sample")