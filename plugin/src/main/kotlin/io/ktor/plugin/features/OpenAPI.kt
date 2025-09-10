package io.ktor.plugin.features

import io.ktor.plugin.*
import io.ktor.plugin.KtorGradlePlugin.Companion.COMPILER_PLUGIN_ID
import io.ktor.plugin.KtorGradlePlugin.Companion.VERSION
import io.ktor.plugin.internal.*
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.plugin.CompilerPluginConfig
import org.jetbrains.kotlin.gradle.plugin.KotlinApiPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

@OpenApiPreview
public abstract class OpenApiExtension(project: Project) {
    /**
     * The output path for the generated OpenAPI specification.
     * Defaults to "build/resources/main/openapi/generated.json"
     */
    public val target: Property<RegularFile?> = project.objects.fileProperty()

    /**
     * The title of the API.
     */
    public val title: Property<String?> = project.property<String?>(null)

    /**
     * A short summary of the API.
     */
    public val summary: Property<String?> = project.property<String?>(null)

    /**
     * A description of the API. CommonMark syntax MAY be used for rich text representation.
     */
    public val description: Property<String?> = project.property<String?>(null)

    /**
     * A URI for the Terms of Service for the API. This MUST be in the form of a URI.
     */
    public val termsOfService: Property<String?> = project.property<String?>(null)

    /**
     * The contact information for the exposed API.
     */
    public val contact: Property<String?> = project.property<String?>(null)

    /**
     * The license information for the exposed API.
     */
    public val license: Property<String?> = project.property<String?>(null)

    /**
     * The version of the OpenAPI Document (which is distinct from the OpenAPI Specification version or the version of the API being described or the version of the OpenAPI Description).
     */
    public val version: Property<String?> = project.property<String?>(null)
}

internal const val OPENAPI_TASK_NAME = "buildOpenApi"
internal const val OPENAPI_TASK_DESCRIPTION = "Generates OpenAPI specification based on Ktor routing definitions."

@OptIn(OpenApiPreview::class)
internal fun Project.configureOpenApi() {
    val extension = createKtorExtension<OpenApiExtension>("openApi")
    try {
        val kotlinApiPlugin = plugins.apply(KotlinApiPlugin::class.java)

        // Configure the custom OpenAPI generation task when Kotlin JVM plugin is applied
        whenKotlinJvmApplied {
            configureOpenApiGenerationTask(extension, kotlinApiPlugin)
        }
    } catch (_: Throwable) {
        logger.warn("Could not apply compiler plugin. OpenAPI generation might not work.")
    }
}

@OptIn(OpenApiPreview::class)
private fun Project.configureOpenApiGenerationTask(
    extension: OpenApiExtension,
    kotlinApiPlugin: KotlinApiPlugin
): TaskProvider<out KotlinJvmCompile>? {
    // Get the main compile task as a reference
    val mainCompileTask = tasks.withType<KotlinCompile>()
        .firstOrNull { "test" !in it.name.lowercase() }
        ?: return null

    dependencies.add(
        configurations.ktorCompilerPlugins.name,
        "$COMPILER_PLUGIN_ID:$VERSION"
    )

    return kotlinApiPlugin.registerKotlinJvmCompileTask(
        taskName = OPENAPI_TASK_NAME,
        compilerOptions = kotlinApiPlugin.createCompilerJvmOptions(),
        explicitApiMode = provider { ExplicitApiMode.Disabled }
    ).also { provider ->
        provider.configure { task ->
            task.doFirst {
                task.logger.warn("Ktor's OpenAPI generation is ** experimental **")
                task.logger.lifecycle("""
                    - It may be incompatible with Kotlin versions outside 2.2.20
                    - Behavior will likely change in future releases
                    - Please report any issues at https://youtrack.jetbrains.com/newIssue?project=KTOR
                """.trimIndent())
            }
            // Drop the class files after each run
            task.doLast {
                val directory = task.destinationDirectory.get().asFile
                if (directory.exists()) {
                    directory.deleteRecursively()
                    directory.mkdirs()
                }
            }

            task.group = KtorGradlePlugin.TASK_GROUP
            task.description = OPENAPI_TASK_DESCRIPTION
            // Copy the main compile task configuration
            task.source(mainCompileTask.sources)
            task.dependsOn(mainCompileTask.dependsOn)
            // Multiplatform is unimportant
            task.multiPlatformEnabled.set(false)
            // Update to use classpath configuration from the main task
            task.friendPaths.setFrom(mainCompileTask.friendPaths)
            task.libraries.setFrom(mainCompileTask.libraries)
            task.destinationDirectory.set(task.project.layout.buildDirectory.dir("ktor-compile/openapi"))
            // Configure the compiler to only run the frontend (skip code generation)
            task.compilerOptions {
                // Copy relevant options from main compile task
                jvmTarget.set(mainCompileTask.compilerOptions.jvmTarget)
                apiVersion.set(mainCompileTask.compilerOptions.apiVersion)
                languageVersion.set(mainCompileTask.compilerOptions.languageVersion)
                javaParameters.set(mainCompileTask.compilerOptions.javaParameters)
                moduleName.set(mainCompileTask.compilerOptions.moduleName)

                // Free compiler args to disable incremental compilation
                freeCompilerArgs.addAll(mainCompileTask.compilerOptions.freeCompilerArgs.get())
                freeCompilerArgs.add("-Xenable-incremental-compilation=false")
            }

            task.pluginClasspath.from(configurations.ktorCompilerPlugins)

            task.pluginOptions.add(CompilerPluginConfig().apply {
                val outputPath = extension.target.takeIf { it.isPresent }?.get()?.asFile?.absolutePath
                    ?: task.project.layout.ktorOutputDir.get().file("openapi/generated.json").asFile.absolutePath
                ktorOption("openapi.enabled", true)
                ktorOption("openapi.output", outputPath)
                ktorOption("openapi.description", extension.description)
                ktorOption("openapi.title", extension.title)
                ktorOption("openapi.summary", extension.summary)
                ktorOption("openapi.contact", extension.contact)
                ktorOption("openapi.termsOfService", extension.termsOfService)
                ktorOption("openapi.license", extension.license)
                ktorOption("openapi.version", extension.version)
            })
        }
    }
}

private fun <T> CompilerPluginConfig.ktorOption(key: String, value: Property<T>) {
    value.orNull?.let {
        ktorOption(key, it.toString())
    }
}
private fun <T: Any> CompilerPluginConfig.ktorOption(key: String, value: T) {
    addPluginArgument(COMPILER_PLUGIN_ID.replace(':', '.'), SubpluginOption(key, value.toString()))
}