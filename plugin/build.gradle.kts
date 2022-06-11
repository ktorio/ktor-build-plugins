import org.gradle.api.tasks.testing.logging.TestLogEvent

@Suppress("DSL_SCOPE_VIOLATION") // "libs" produces a false-positive warning, see https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.gradle.plugin.publish)
}

group = libs.plugins.ktor.get().pluginId
version = libs.plugins.ktor.get().version

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())

    implementation(libs.shadow.gradle.plugin)
    implementation(libs.jib.gradle.plugin)
    implementation(libs.graalvm.gradle.plugin)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter.params)
}

object PluginBundle {
    const val SHORT_NAME = "ktor"
    const val ID = "io.ktor.plugin"
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

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging.events(*TestLogEvent.values())
}

// To run tests on build
//tasks.withType<Jar> {
//    dependsOn("test")
//}

// Allow publishing to local repository on `publish` command
publishing {
    repositories {
        maven {
            name = "localPluginRepository"
            url = uri("/Users/Rustam.Musin/my/local-plugin-repository")
        }
    }
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.logging.log4j") {
            useVersion(libs.versions.log4j.get())
            because("zero-day exploit, required for Shadow v6")
        }
    }
}
