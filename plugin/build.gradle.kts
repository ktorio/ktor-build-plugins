import org.gradle.api.tasks.testing.logging.TestLogEvent

configurations.all {
    resolutionStrategy {
        force("com.fasterxml.jackson:jackson-bom:2.14.3") // To prevent https://github.com/gradle/gradle/issues/24390
    }
}

@Suppress("DSL_SCOPE_VIOLATION") // "libs" produces a false-positive warning, see https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.gradle.plugin.publish)
}

group = libs.plugins.ktor.get().pluginId
version = libs.plugins.ktor.get().version

if (hasProperty("versionSuffix")) {
    val suffix = property("versionSuffix")
    version = "$version-$suffix"
}

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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

gradlePlugin {
    website.set("https://ktor.io")
    vcsUrl.set("https://github.com/ktorio/ktor")

    plugins {
        create("ktor") {
            id = "io.ktor.plugin"
            displayName = "Ktor Gradle Plugin"
            implementationClass = "io.ktor.plugin.KtorGradlePlugin"
            description = "Ktor Gradle Plugin configures deployment and version management for Ktor applications"
            tags.set(setOf("ktor", "kotlin", "web", "async", "asynchronous", "web-framework", "fatjar", "docker", "jib", "graalvm"))
        }
    }
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

// This block is needed to show plugin tasks on --dry-run
//  and to not run task actions on ":plugin:task --dry-run".
//  The bug is known since June 2017 and still not fixed.
//  The workaround used below is described here: https://github.com/gradle/gradle/issues/2517#issuecomment-437490287
if (gradle.parent != null && gradle.parent!!.startParameter.isDryRun) {
    gradle.startParameter.isDryRun = true
}

tasks.named("publishPlugins") {
    dependsOn("test", setupPluginUploadFromEnvironment)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging.events(*TestLogEvent.values())
}

if (hasProperty("space")) {
    publishing {
        repositories {
            maven {
                name = "SpacePackages"
                url = uri(System.getenv("PUBLISHING_URL"))
                credentials {
                    username = System.getenv("PUBLISHING_USER")
                    password = System.getenv("PUBLISHING_PASSWORD")
                }
            }
        }
    }
}
