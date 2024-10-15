plugins {
    alias(libs.plugins.ktor)
}

application.mainClass.set("io.ktor.samples.native.ApplicationKt")

ktor {
    nativeImage {
        imageName.set("native-image-sample")
    }
}
