plugins {
    alias(libs.plugins.ktor)
}

application.mainClass = "io.ktor.samples.native.ApplicationKt"

ktor {
    nativeImage {
        imageName = "native-image-sample"
    }
}
