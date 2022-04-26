import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

val kotlin_logging_version: String by project
val logback_version: String by project

plugins {
    java
    kotlin("jvm") version "1.6.21" apply false
    kotlin("plugin.serialization") version "1.6.21" apply false
}

subprojects {
    apply {
        plugin("java")
        plugin("org.jetbrains.kotlin.jvm")
    }

    repositories {
        mavenCentral()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    dependencies {
        implementation("io.github.microutils:kotlin-logging-jvm:$kotlin_logging_version")
        implementation("ch.qos.logback:logback-classic:$logback_version")
    }

    val kotlin = (this as ExtensionAware).extensions.getByName("kotlin") as KotlinJvmProjectExtension
    kotlin.sourceSets.all {
        languageSettings.apply {
            optIn("kotlin.time.ExperimentalTime")
            optIn("kotlin.ExperimentalStdlibApi")
            optIn("kotlinx.coroutines.ObsoleteCoroutinesApi")
        }
    }
}