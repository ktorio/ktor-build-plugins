package io.ktor.plugin.features

import io.ktor.plugin.internal.*
import org.graalvm.buildtools.gradle.NativeImagePlugin
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.graalvm.buildtools.gradle.dsl.GraalVMReachabilityMetadataRepositoryExtension
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.SetProperty

/**
 * Configuration for GraalVM native image generation.
 */
abstract class NativeImageExtension(project: Project) {
    /**
     * Specifies whether to enable verbose output. Defaults to `true`.
     */
    val verbose = project.property(defaultValue = true)

    /**
     * Specifies a name of executable file. Defaults to `"native-image"`.
     */
    val imageName = project.property(defaultValue = "native-image")

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
    val attachAgent = project.property(defaultValue = false)

    /**
     * Specifies packages or classes to be initialized at build time.
     */
    val initializeAtBuildTime: SetProperty<String> = project.objects.setProperty(String::class.java)

    /**
     * Specifies packages or classes to be initialized at run time.
     * Useful when some class or package has to be initialized at run time,
     * but it's included in [initializeAtBuildTime].
     */
    val initializeAtRunTime: SetProperty<String> = project.objects.setProperty(String::class.java)
}

private const val NATIVE_IMAGE_EXTENSION_NAME = "nativeImage"

const val BUILD_NATIVE_IMAGE_TASK_NAME = "buildNativeImage"
private const val BUILD_NATIVE_IMAGE_TASK_DESCRIPTION = "Builds a GraalVM native image."

private val PACKAGES_TO_INITIALIZE_AT_BUILD_TIME = setOf("io.ktor", "kotlin", "ch.qos.logback", "kotlinx")

private const val CONFIGURE_GRAALVM_TASK_NAME = "configureGraalVM"

private fun Project.configureGraalVM(nativeImageExtension: NativeImageExtension) {
    graalvmNative {
        metadataRepository.enabled.set(true)
        binaries.named("main") { nativeImageOptions ->
            nativeImageOptions.apply {
                verbose.set(nativeImageExtension.verbose)
                agent.enabled.set(nativeImageExtension.attachAgent)

                buildArgs.add(nativeImageExtension.initializeAtBuildTime.map {
                    "--initialize-at-build-time=${(it + PACKAGES_TO_INITIALIZE_AT_BUILD_TIME).joinToString(",")}"
                })

                buildArgs.add(nativeImageExtension.initializeAtRunTime.map {
                    "--initialize-at-run-time=${it.joinToString(",")}"
                })

                buildArgs.add("-H:+InstallExitHandlers")
                buildArgs.add("-H:+ReportUnsupportedElementsAtRuntime")
                buildArgs.add("-H:+ReportExceptionStackTraces")

                resources.autodetect()

                imageName.set(nativeImageExtension.imageName)
            }
        }
    }
}

internal fun Project.configureNativeImage() {
    apply<JavaPlugin>() // required for NativeImagePlugin
    apply<NativeImagePlugin>()

    val nativeImageExtension = createKtorExtension<NativeImageExtension>(NATIVE_IMAGE_EXTENSION_NAME)
    val configureGraalVMTask = tasks.register(CONFIGURE_GRAALVM_TASK_NAME) {
        // This configuration has to be done in the configuration phase.
        configureGraalVM(nativeImageExtension)
    }

    val nativeCompileTask = tasks.named(NativeImagePlugin.NATIVE_COMPILE_TASK_NAME) {
        it.dependsOn(configureGraalVMTask)
    }

    tasks.registerKtorTask(BUILD_NATIVE_IMAGE_TASK_NAME, BUILD_NATIVE_IMAGE_TASK_DESCRIPTION) {
        dependsOn(nativeCompileTask)
    }
}

private fun Project.graalvmNative(configure: GraalVMExtension.() -> Unit) {
    extensions.configure(GraalVMExtension::class.java, configure)
}

private val GraalVMExtension.metadataRepository: GraalVMReachabilityMetadataRepositoryExtension
    get() = (this as ExtensionAware).extensions.getByType(GraalVMReachabilityMetadataRepositoryExtension::class.java)
