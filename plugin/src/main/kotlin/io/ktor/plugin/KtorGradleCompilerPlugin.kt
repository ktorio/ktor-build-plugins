package io.ktor.plugin

import io.ktor.plugin.features.OPENAPI_EXTENSION_KEY
import io.ktor.plugin.features.OpenAPIExtension
import io.ktor.plugin.internal.application
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public class KtorGradleCompilerPlugin : KotlinCompilerPluginSupportPlugin {

    override fun getCompilerPluginId(): String = "io.ktor.ktor-compiler-plugin"

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            "io.ktor",
            "ktor-compiler-plugin",
            KtorGradlePlugin.VERSION,
        )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project

        // TODO support KMP
        // Return an empty list of options for now
        // Later you can add configuration options from your Gradle extension
        return project.provider {
            val extension = project.extensions.extraProperties[OPENAPI_EXTENSION_KEY] as OpenAPIExtension
            val mainClass = project.application.mainClass.orNull

            // Only apply for main compilations
            if (kotlinCompilation.compileKotlinTaskName.contains("test", ignoreCase = true))
                return@provider emptyList()

            buildList {
                val outputPath = project.ktorOutputDir.get().file("openapi/generated-api.json").asFile.absolutePath
                mainClass?.let { add(SubpluginOption(key = "mainClass", value = mainClass)) }
                add(SubpluginOption(key = "openapi.enabled", value = extension.enabled.get().toString()))
                add(SubpluginOption(key = "openapi.output", value = outputPath))
                extension.description.orNull?.let { add(SubpluginOption(key = "openapi.description", value = it)) }
                extension.title.orNull?.let { add(SubpluginOption(key = "openapi.title", value = it)) }
                extension.summary.orNull?.let { add(SubpluginOption(key = "openapi.summary", value = it)) }
                extension.termsOfService.orNull?.let { add(SubpluginOption(key = "openapi.termsOfService", value = it)) }
                extension.contact.orNull?.let { add(SubpluginOption(key = "openapi.contact", value = it)) }
                extension.license.orNull?.let { add(SubpluginOption(key = "openapi.license", value = it)) }
                extension.version.orNull?.let { add(SubpluginOption(key = "openapi.version", value = it)) }
            }
        }
    }

}