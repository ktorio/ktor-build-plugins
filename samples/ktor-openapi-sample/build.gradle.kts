plugins {
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

application.mainClass = "io.ktor.samples.openapi.ApplicationKt"

ktor {
    openApi {
        enabled = true
        title = "OpenAPI example"
        version = "2.1"
        summary = "This is a sample API"
    }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.openApi)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.routing.annotate)
    implementation(libs.ktor.json)
    implementation(libs.logback)
}