plugins {
    alias(libs.plugins.kotlin.jvm)
}

description = "HotSwapAgent plugin that reloads a Ktor application's modules after a class redefinition"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // SPI annotations/classes only — never bundled. hotswap-agent is GPLv2 and is resolved as an
    // ordinary Maven dependency in the *consumer's* build (see HotRun.kt); this module only compiles
    // against its API to produce our own small, separately-licensed plugin class.
    compileOnly(libs.hotswapAgent)
}

tasks.jar {
    // Fixed name, independent of project version, so `plugin/build.gradle.kts` and `HotRun.kt` can
    // agree on the bundled resource path without depending on a resolved version string.
    archiveBaseName.set("hotswap-agent-plugin")
    archiveVersion.set("")
}
