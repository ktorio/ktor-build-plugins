plugins {
    kotlin("jvm") version "1.6.20"
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "0.21.0" // Task: Upgrade to 1.0.0 when it's released https://plugins.gradle.org/plugin/com.gradle.plugin-publish
}

object PluginCoordinates {
    const val ID = "io.ktor.plugin"
    const val GROUP = "io.ktor"
    const val VERSION = "0.0.1"
    const val IMPLEMENTATION_CLASS = "io.ktor.KtorPlugin"
}

group = PluginCoordinates.GROUP
version = PluginCoordinates.VERSION

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())

    testImplementation("org.jetbrains.kotlin:kotlin-test:1.6.20")
    testImplementation(platform("org.junit:junit-bom:5.8.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")

//    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
//    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

object PluginBundle {
    const val VCS = "https://github.com/ktorio/ktor-build-plugins"
    const val WEBSITE = "https://github.com/ktorio/ktor-build-plugins"
    const val DESCRIPTION = "Ktor plugin"
    const val DISPLAY_NAME = "Ktor plugin"
    val TAGS = listOf<String>(
//        "plugin",
//        "gradle",
//        "sample",
//        "template"
    )
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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