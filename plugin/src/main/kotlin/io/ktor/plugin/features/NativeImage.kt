package io.ktor.plugin.features

import org.graalvm.buildtools.gradle.NativeImagePlugin
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.SetProperty

/**
 * Configuration for GraalVM native image generation.
 */
abstract class NativeImageExtension(project: Project) {
    /**
     * Specifies whether to enable verbose output. Defaults to `true`.
     */
    var verbose = true

    /**
     * Specifies a name of executable file. Defaults to `"native-image"`.
     */
    var imageName = "native-image"

    /**
     * Specifies whether to attach a GraalVM agent on an image building or not.
     * Attaching an agent will not produce an image,
     * but instead will run the application
     * and create useful configs such as `reflect-config.json` in the build folder.
     *
     * For further manual and usages, see
     * [official GraalVM documentation](https://www.graalvm.org/reference-manual/native-image/Agent/).
     *
     * Defaults to `false`.
     */
    var attachAgent = false

    /**
     * Specifies packages or classes to be initialized at build time.
     */
    var initializeAtBuildTime: SetProperty<String> = project.objects.setProperty(String::class.java)

    /**
     * Specifies packages or classes to be initialized at run time.
     * Useful when some class or package has to be initialized at run time,
     * but it's included in [initializeAtBuildTime].
     */
    var initializeAtRunTime: SetProperty<String> = project.objects.setProperty(String::class.java)
}

private const val NATIVE_IMAGE_EXTENSION_NAME = "nativeImage"

const val BUILD_NATIVE_IMAGE_TASK_NAME = "buildNativeImage"
private const val BUILD_NATIVE_IMAGE_TASK_DESCRIPTION = "Builds a GraalVM native image."

private val PACKAGES_TO_INITIALIZE_AT_BUILD_TIME = setOf("io.ktor", "kotlin", "ch.qos.logback", "kotlinx")

private const val CONFIGURE_GRAALVM_TASK_NAME = "configureGraalVM"

private fun configureGraalVM(project: Project, nativeImageExtension: NativeImageExtension) {
    project.extensions.configure(GraalVMExtension::class.java) { graalVMExtension ->
        graalVMExtension.binaries.named("main") { nativeImageOptions ->
            nativeImageOptions.apply {
                verbose.set(nativeImageExtension.verbose)
                agent.enabled.set(nativeImageExtension.attachAgent)

                val initializeAtBuildTime =
                    nativeImageExtension.initializeAtBuildTime.get() + PACKAGES_TO_INITIALIZE_AT_BUILD_TIME
                buildArgs.add("--initialize-at-build-time=${initializeAtBuildTime.joinToString(",")}")

                val initializeAtRunTime = nativeImageExtension.initializeAtRunTime.get()
                if (initializeAtRunTime.isNotEmpty())
                    buildArgs.add("--initialize-at-run-time=${initializeAtRunTime.joinToString(",")}")

                buildArgs.add("-H:+InstallExitHandlers")
                buildArgs.add("-H:+ReportUnsupportedElementsAtRuntime")
                buildArgs.add("-H:+ReportExceptionStackTraces")

                resources.autodetect()

                imageName.set(nativeImageExtension.imageName)
            }
        }
    }
}

fun configureNativeImage(project: Project) {
    project.plugins.apply(JavaPlugin::class.java) // required for NativeImagePlugin
    project.plugins.apply(NativeImagePlugin::class.java)

    val nativeImageExtension = project.createKtorExtension<NativeImageExtension>(NATIVE_IMAGE_EXTENSION_NAME, project)
    val configureGraalVMTask = project.tasks.register(CONFIGURE_GRAALVM_TASK_NAME) {
        // This configuration has to be done in the configuration phase.
        configureGraalVM(project, nativeImageExtension)
    }

    val nativeCompileTask = project.tasks.named(NativeImagePlugin.NATIVE_COMPILE_TASK_NAME) {
        it.dependsOn(configureGraalVMTask)
    }

    project.tasks.registerKtorTask(BUILD_NATIVE_IMAGE_TASK_NAME, BUILD_NATIVE_IMAGE_TASK_DESCRIPTION) {
        dependsOn(nativeCompileTask)
    }
}
