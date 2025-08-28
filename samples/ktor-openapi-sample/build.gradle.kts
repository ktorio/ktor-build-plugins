import io.ktor.plugin.OpenApiPreview
import org.gradle.kotlin.dsl.openApi
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
}

application.mainClass = "io.ktor.samples.openapi.ApplicationKt"

ktor {
    @OptIn(OpenApiPreview::class)
    openApi {
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
    implementation(libs.ktor.json)
    implementation(libs.logback)
}

tasks.withType<KotlinCompile>().configureEach {
    incremental = false
}