package io.ktor.plugin.extension

import org.graalvm.buildtools.gradle.NativeImagePlugin
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Input

/**
 * Configuration for GraalVM native image generation.
 */
abstract class NativeExtension {
    /**
     * Specifies whether to enable verbose output. Defaults to `true`.
     */
    var verbose = true

    /**
     * Specifies a name of executable file. Defaults to `"native-image"`.
     */
    @get:Input
    var imageName = "native-image"

    /**
     * Specifies packages or classes to be initialized at build time.
     */
    @get:Input
    var initializeAtBuildTime = mutableListOf<String>()

    /**
     * Specifies packages or classes to be initialized at run time.
     * Useful when some class or package has to be initialized at run time, but it's included in [initializeAtBuildTime].
     */
    @get:Input
    var initializeAtRunTime = mutableListOf<String>()
}

private const val NATIVE_EXTENSION_NAME = "nativeImage"

private const val CONFIGURE_NATIVE_TASK_NAME = "configureNative"

const val BUILD_NATIVE_IMAGE_TASK_NAME = "buildNativeImage"
private const val BUILD_NATIVE_IMAGE_TASK_DESCRIPTION = "Builds GraalVM native image."

private val PACKAGES_TO_INITIALIZE_AT_BUILD_TIME = setOf("io.ktor", "kotlin")

fun configureNative(project: Project) {
    project.plugins.apply(JavaPlugin::class.java) // required for NativeImagePlugin
    project.plugins.apply(NativeImagePlugin::class.java)

    val nativeExtension = project.createKtorExtension<NativeExtension>(NATIVE_EXTENSION_NAME)
    val configureNativeTask = project.tasks.register(CONFIGURE_NATIVE_TASK_NAME) {
        project.extensions.configure(GraalVMExtension::class.java) { extension ->
            extension.binaries.named("main") { options ->
                options.fallback.set(false)
                options.verbose.set(nativeExtension.verbose)

                val initializeAtBuildTime = nativeExtension.initializeAtBuildTime + PACKAGES_TO_INITIALIZE_AT_BUILD_TIME
                options.buildArgs.add("--initialize-at-build-time=${initializeAtBuildTime.joinToString(",")}")

                val initializeAtRunTime = nativeExtension.initializeAtRunTime
                if (initializeAtRunTime.isNotEmpty())
                    options.buildArgs.add("--initialize-at-run-time=${initializeAtRunTime.joinToString(",")}")

                options.buildArgs.add("-H:+InstallExitHandlers")
                options.buildArgs.add("-H:+ReportUnsupportedElementsAtRuntime")
                options.buildArgs.add("-H:+ReportExceptionStackTraces")

                options.resources.autodetect()

                options.imageName.set(nativeExtension.imageName)
            }
        }
    }

    val nativeCompileTask = project.tasks.named(NativeImagePlugin.NATIVE_COMPILE_TASK_NAME) {
        it.dependsOn(configureNativeTask)
    }
    project.tasks.registerKtorTask(BUILD_NATIVE_IMAGE_TASK_NAME, BUILD_NATIVE_IMAGE_TASK_DESCRIPTION) {
        dependsOn(nativeCompileTask)
    }
}