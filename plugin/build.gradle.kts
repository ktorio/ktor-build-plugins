plugins {
    kotlin("jvm") version "1.6.21"
    id("com.gradle.plugin-publish") version "1.0.0-rc-2"
}

val kotlin_version: String by project
val junit_version: String by project
val shadow_plugin_version: String by project
val jib_gradle_plugin_version: String by project
val log4j_version: String by project

group = PluginBundle.GROUP
version = PluginBundle.VERSION

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())

    implementation("com.github.johnrengelman.shadow:com.github.johnrengelman.shadow.gradle.plugin:$shadow_plugin_version")
    implementation("gradle.plugin.com.google.cloud.tools:jib-gradle-plugin:$jib_gradle_plugin_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("org.junit.vintage:junit-vintage-engine:$junit_version")
}

object PluginBundle {
    const val SHORT_NAME = "ktor"
    const val ID = "io.ktor.plugin"
    const val GROUP = "io.ktor"
    const val VERSION = "0.0.1"
    const val IMPLEMENTATION_CLASS = "io.ktor.plugin.KtorGradlePlugin"
    const val VCS = "https://github.com/ktorio/ktor"
    const val WEBSITE = "https://ktor.io"
    const val DESCRIPTION = "Provides the ability to package and containerize your Ktor application"
    const val DISPLAY_NAME = "Ktor Gradle Plugin"
    val TAGS = listOf("ktor", "kotlin", "web", "async", "asynchronous", "web-framework")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

gradlePlugin {
    plugins {
        create(PluginBundle.SHORT_NAME) {
            id = PluginBundle.ID
            displayName = PluginBundle.DISPLAY_NAME
            implementationClass = PluginBundle.IMPLEMENTATION_CLASS
            version = PluginBundle.VERSION
        }
    }
}

pluginBundle {
    website = PluginBundle.WEBSITE
    vcsUrl = PluginBundle.VCS
    description = PluginBundle.DESCRIPTION
    tags = PluginBundle.TAGS
}

val setupPluginUploadFromEnvironment = tasks.register("setupPluginUploadFromEnvironment") {
    doLast {
        val key = System.getenv("GRADLE_PUBLISH_KEY")
        val secret = System.getenv("GRADLE_PUBLISH_SECRET")

        if (key == null || secret == null) {
            throw GradleException("GRADLE_PUBLISH_KEY and/or GRADLE_PUBLISH_SECRET are not defined environment variables")
        }

        System.setProperty("gradle.publish.key", key)
        System.setProperty("gradle.publish.secret", secret)
    }
}

tasks.named("publishPlugins") {
    dependsOn(setupPluginUploadFromEnvironment)
}

// To run tests on build
tasks.withType<Jar> {
    dependsOn("test")
}

// Allow publishing to local repository on `publish` command
publishing {
    repositories {
        maven {
            name = "localPluginRepository"
            url = uri("../local-plugin-repository")
        }
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.logging.log4j") {
            useVersion(log4j_version)
            because("zero-day exploit, required for Shadow v5")
        }
    }
}
