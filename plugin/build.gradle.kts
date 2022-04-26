plugins {
    kotlin("jvm") version "1.6.21"
    id("java-gradle-plugin")

    // TODO: Upgrade to 0.19.0+ when we understand how to replace vulnerable log4j version with an non-vulnerable one in shadow plugin
    // TODO: Upgrade to 1.0.0 when it's released https://plugins.gradle.org/plugin/com.gradle.plugin-publish
    id("com.gradle.plugin-publish") version "0.18.0"
}

val kotlin_version: String by project
val junit_version: String by project

object PluginCoordinates {
    const val ID = "io.ktor.ktor-gradle-plugin"
    const val GROUP = "io.ktor"
    const val VERSION = "0.0.1"
    const val IMPLEMENTATION_CLASS = "io.ktor.plugin.KtorGradlePlugin"
}

group = PluginCoordinates.GROUP
version = PluginCoordinates.VERSION

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())

    // https://mvnrepository.com/artifact/com.github.johnrengelman.shadow/com.github.johnrengelman.shadow.gradle.plugin
    implementation("com.github.johnrengelman.shadow:com.github.johnrengelman.shadow.gradle.plugin:7.1.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("org.junit.vintage:junit-vintage-engine:$junit_version")
}

object PluginBundle {
    const val VCS = "https://github.com/ktorio/ktor-build-plugins"
    const val WEBSITE = "https://github.com/ktorio/ktor-build-plugins"
    const val DESCRIPTION = "Ktor Gradle Plugin"
    const val DISPLAY_NAME = "Ktor Gradle Plugin"
    val TAGS = listOf<String>()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

gradlePlugin {
    plugins {
        create(PluginCoordinates.ID) {
            id = PluginCoordinates.ID
            implementationClass = PluginCoordinates.IMPLEMENTATION_CLASS
            version = PluginCoordinates.VERSION
        }
    }
}

// Configuration Block for the Plugin Marker artifact on Plugin Central
pluginBundle {
    website = PluginBundle.WEBSITE
    vcsUrl = PluginBundle.VCS
    description = PluginBundle.DESCRIPTION
    tags = PluginBundle.TAGS

    (plugins) {
        getByName(PluginCoordinates.ID) {
            displayName = PluginBundle.DISPLAY_NAME
        }
    }

    mavenCoordinates {
        groupId = PluginCoordinates.GROUP
        artifactId = PluginCoordinates.ID.removePrefix("$groupId.")
        version = PluginCoordinates.VERSION
    }
}

tasks.create("setupPluginUploadFromEnvironment") {
    doLast {
        val key = System.getenv("GRADLE_PUBLISH_KEY")
        val secret = System.getenv("GRADLE_PUBLISH_SECRET")

        if (key == null || secret == null) {
            throw GradleException("gradlePublishKey and/or gradlePublishSecret are not defined environment variables")
        }

        System.setProperty("gradle.publish.key", key)
        System.setProperty("gradle.publish.secret", secret)
    }
}

// To run tests on build
tasks.withType<Jar> {
    dependsOn("test")
}