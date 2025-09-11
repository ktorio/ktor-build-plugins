plugins {
    `kotlin-dsl`
}

kotlin {
    // Should be in sync with gradle/gradle-daemon-jvm.properties
    jvmToolchain(21)
}

dependencies {
    implementation(libs.gradlePlugin.mavenPublishing)
}
