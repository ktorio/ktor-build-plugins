import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.pluginPublish)
    alias(libs.plugins.binaryCompatibilityValidator)
}

group = libs.plugins.ktor.get().pluginId
version = libs.plugins.ktor.get().version

if (hasProperty("versionSuffix")) {
    val suffix = property("versionSuffix")
    version = "$version-$suffix"
}

dependencies {
    implementation(gradleApi())

    compileOnly(libs.gradlePlugin.kotlin)
    implementation(libs.gradlePlugin.shadow)
    implementation(libs.gradlePlugin.jib)
    implementation(libs.gradlePlugin.graalvm)

    testImplementation(libs.gradlePlugin.kotlin)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter.params)

    constraints {
        // TODO: Check if this constraint is still needed after each JIB update
        implementation("org.apache.commons:commons-lang3:[3.18.0,)") {
            because("Versions 3.0..<3.18.0 are affected by CVE-2025-48924")
        }
    }
}

kotlin {
    explicitApi()
    jvmToolchain(11)

    compilerOptions {
        // See https://docs.gradle.org/current/userguide/compatibility.html#kotlin
        languageVersion = KotlinVersion.KOTLIN_1_8
        apiVersion = KotlinVersion.KOTLIN_1_8
    }
}

gradlePlugin {
    website = "https://ktor.io"
    vcsUrl = "https://github.com/ktorio/ktor"

    plugins {
        create("ktor") {
            id = "io.ktor.plugin"
            displayName = "Ktor Gradle Plugin"
            implementationClass = "io.ktor.plugin.KtorGradlePlugin"
            description = "Ktor Gradle Plugin configures deployment and version management for Ktor applications"
            tags = setOf("ktor", "kotlin", "web", "async", "asynchronous", "web-framework", "fatjar", "docker", "jib", "graalvm")
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

val publishToMavenLocal = tasks.named("publishToMavenLocal")
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging.events(*TestLogEvent.values())

    // The plugin should be published to MavenLocal to be available in integration tests
    // We can't use GradleRunner.withPluginClasspath() because of https://github.com/gradle/gradle/issues/22466
    dependsOn(publishToMavenLocal)
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
