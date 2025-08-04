import org.gradle.kotlin.dsl.provideDelegate

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.kotlinx.serialization)
    `maven-publish`
    `java-test-fixtures`
    idea
}

val artifact = "ktor-compiler-plugin"

group = "io.ktor"
version = libs.plugins.ktor.get().version

if (hasProperty("versionSuffix")) {
    val suffix = property("versionSuffix")
    version = "$version-$suffix"
}

val testSamples by configurations.creating
val testData by sourceSets.creating {
    java.setSrcDirs(listOf("testData"))
    compileClasspath += testSamples
    runtimeClasspath += testSamples
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
    testFixtures {
        java.setSrcDirs(listOf("test-fixtures"))
    }
    test {
        java.setSrcDirs(listOf("test-gen"))
    }
}

idea.module.generatedSourceDirs.add(projectDir.resolve("test-gen"))

dependencies {
    compileOnly(libs.kotlin.compiler)
    implementation(libs.kotlinx.json)

    testFixturesApi(libs.kotlin.test.junit5)
    testFixturesApi(libs.kotlin.compiler.testFramework)
    testFixturesApi(libs.kotlin.compiler)
    testFixturesApi(libs.kotlinx.json)

    testSamples(libs.ktor.server.core)
    testSamples(libs.ktor.server.cio)
    testSamples(libs.ktor.server.contentNegotiation)
    testSamples(libs.ktor.json)

    testRuntimeOnly(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.kotlin.test)
    testRuntimeOnly(libs.kotlin.reflect)
    testRuntimeOnly(libs.kotlin.script.runtime)
    testRuntimeOnly(libs.kotlin.annotations.jvm)
}

buildConfig {
    useKotlinOutput {
        internalVisibility = true
    }

    packageName(rootProject.group.toString())
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}.$artifact\"")
}

kotlin {
    jvmToolchain(11)
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks {
    test {
        dependsOn(testSamples)

        useJUnitPlatform()
        workingDir = rootDir

        systemProperty("testSamples.classpath", testSamples.asPath)
        systemProperty("testSamples.location", layout.projectDirectory.dir("testData").asFile.absolutePath)

        // Properties required to run the internal test framework.
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib-jdk8", "kotlin-stdlib-jdk8")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
        setLibraryProperty("org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")

        systemProperty("idea.ignore.disabled.plugins", "true")
        systemProperty("idea.home.path", rootDir)
    }


    val updateSnapshots by registering(Test::class) {
        group = "verification"
        useJUnitPlatform()
        environment("REPLACE_OPENAPI_SNAPSHOTS", "true")
        systemProperty("testSamples.replaceSnapshots", "true")
        include("**/OpenapiTestGenerated.class")
    }

    val generateTests by registering(JavaExec::class) {
        inputs
            .dir(layout.projectDirectory.dir("testData"))
            .withPropertyName("testData")
            .withPathSensitivity(PathSensitivity.RELATIVE)
        outputs
            .dir(layout.projectDirectory.dir("test-gen"))
            .withPropertyName("generatedTests")

        classpath = sourceSets.testFixtures.get().runtimeClasspath
        mainClass.set("io.ktor.compiler.GenerateTestsKt")
        workingDir = rootDir
    }

    compileTestKotlin {
        dependsOn(generateTests)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = artifact
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}

fun Test.setLibraryProperty(
    propName: String,
    jarName: String,
) {
    val path =
        project.configurations
            .testRuntimeClasspath
            .get()
            .files
            .find { """$jarName-\d.*jar""".toRegex().matches(it.name) }
            ?.absolutePath
            ?: return
    systemProperty(propName, path)
}