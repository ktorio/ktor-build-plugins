import io.ktor.plugin.extension.*

val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val junit_version: String by project

plugins {
    application
    kotlin("jvm")
    id("io.ktor.ktor-gradle-plugin")
}

group = "io.ktor.samples"
version = "0.0.1"
application {
    mainClass.set("io.ktor.samples.docker.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("org.junit.vintage:junit-vintage-engine:$junit_version")
}

ktor {
    docker {
        jreVersion = JreVersion.JRE_17
        localImageName = "sample-docker-image"
        imageTag = "my-docker-sample"

        // Comment externalRepository out if you don't want to push your image to the registry
        externalRegistry = DockerImageRegistry.dockerHub(
            appName = "ktor-app",
            username = System.getProperty("DOCKER_HUB_USERNAME"),
            password = System.getProperty("DOCKER_HUB_PASSWORD")
        )
    }
}