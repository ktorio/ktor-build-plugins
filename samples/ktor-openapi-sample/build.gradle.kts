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
    openApi {
        enabled = true
        title = "OpenAPI example"
        version = "2.1"
        summary = "This is a sample API"
        output = layout.projectDirectory.file("api.json")
    }
}

// Use project module for the compiler plugin
// TODO this does not work!
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(
            module("io.ktor:ktor-compiler-plugin")
        ).using(
            project(":compiler-plugin")
        )
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