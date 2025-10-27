package io.ktor.plugin.features

import io.ktor.plugin.KtorGradlePlugin.Companion.COMPILER_PLUGIN_ID
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

        kotlinCompilation.dependencies {
            compileOnly("$COMPILER_PLUGIN_ID:$VERSION")
            // annotate / openapi dependency
        }

        return project.provider {
            buildList {
                ktorOption("openapiEnabled", extension.enabled.getOrElse(false))
                ktorOption("openapiDescription", extension.description.orNull)
                ktorOption("openapiTitle", extension.title.orNull)
                ktorOption("openapiSummary", extension.summary.orNull)
                ktorOption("openapiContact", extension.contact.orNull)
                ktorOption("openapiTermsOfService", extension.termsOfService.orNull)
                ktorOption("openapiLicense", extension.license.orNull)
                ktorOption("openapiVersion", extension.version.orNull)
            }
        }
    }

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = "io.ktor",
            artifactId = "ktor-compiler-plugin",
            version = VERSION
        )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        kotlinCompilation.target.project.plugins.hasPlugin(CompilerPlugin::class.java)
}

private fun <T : Any> MutableList<SubpluginOption>.ktorOption(key: String, value: T?) {
    if (value == null) return
    add(SubpluginOption(key, value.toString()))
}