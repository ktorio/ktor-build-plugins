package io.ktor.plugin.features

import io.ktor.plugin.KtorGradlePlugin.Companion.VERSION
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import javax.inject.Inject

public class CompilerPlugin @Inject constructor(
    private val objects: ObjectFactory
) : KotlinCompilerPluginSupportPlugin {

    override fun getCompilerPluginId(): String =
        "io.ktor.ktor-compiler-plugin"

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val ktorExtension = project.extensions.getByType(KtorExtension::class.java)
        val extension = ktorExtension.getExtension<OpenApiExtension>()

        return project.provider {
            buildList {
                ktorOption("openapiEnabled", extension.enabled.getOrElse(false))
                ktorOption("openapiCodeInference", extension.codeInferenceEnabled.getOrElse(false))
                ktorOption("openapiOnlyCommented", extension.onlyCommented.getOrElse(false))
            }
        }
    }

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = "io.ktor",
            artifactId = "ktor-compiler-plugin",
            version = VERSION
        )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        try {
            val ktorExtension = kotlinCompilation.target.project.extensions.findByType(KtorExtension::class.java)
                ?: return false
            val openApiExtension = ktorExtension.getExtension<OpenApiExtension>()
            return openApiExtension.enabled.getOrElse(false)
        } catch (e: Exception) {
            kotlinCompilation.target.project.logger.warn("Failed to check if OpenAPI is enabled: ${e.message}")
            return false
        }
    }
}

private fun <T : Any> MutableList<SubpluginOption>.ktorOption(key: String, value: T?) {
    if (value == null) return
    add(SubpluginOption(key, value.toString()))
}