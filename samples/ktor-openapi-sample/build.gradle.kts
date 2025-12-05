plugins {
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

ktor {
    openApi {
        enabled = true
        onlyCommented = true
    }
}

application.mainClass = "io.ktor.samples.openapi.ApplicationKt"

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.openApi)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.routing.annotate)
    implementation(libs.ktor.json)
    implementation(libs.logback)
}