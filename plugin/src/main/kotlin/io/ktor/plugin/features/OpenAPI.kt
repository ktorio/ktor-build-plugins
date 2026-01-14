package io.ktor.plugin.features

import io.ktor.plugin.*
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

internal fun Project.configureOpenApi() {
    val ext = createKtorExtension<OpenApiExtension>("openApi")
    afterEvaluate {
        try {
            val enabled = ext.enabled.get()
            if (enabled) {
                if (!hasRoutingAnnotateDependency()) {
                    // Automatically add the missing dependency to the implementation configuration
                    dependencies.add("implementation", "io.ktor:ktor-server-routing-openapi:${KtorGradlePlugin.KTOR_VERSION}")
                    logger.info("Ktor annotations dependency automatically included")
                }
                // Apply compiler plugin
                pluginManager.apply(CompilerPlugin::class.java)
            } else {
                logger.debug("OpenAPI inference is disabled")
            }
        } catch (_: Throwable) {
            logger.warn("Could not apply compiler plugin. OpenAPI inference will not be available.")
        }
    }
}

private fun Project.hasRoutingAnnotateDependency(): Boolean {
    return configurations.any { configuration ->
        configuration.allDependencies.any {
            it.name == "ktor-server-routing-openapi"
        }
    }
}

public abstract class OpenApiExtension(
    objects: ObjectFactory,
    layout: ProjectLayout,
) {

    @Suppress("unused") // Used for injection
    internal constructor(project: Project) : this(project.objects, project.layout)

    /**
     * The output path for the generated OpenAPI specification.
     * Defaults to "build/resources/main/openapi/generated.json"
     */
    @Deprecated("The specification is now generated at runtime.  You may remove this property")
    public val target: Property<RegularFile> = objects.fileProperty()
        .convention(layout.ktorOutputDir.map { it.file("openapi/generated.json") })

    /**
     * Global flag to enable or disable OpenAPI route annotation code generation.
     * Defaults to `false`.
     */
    public val enabled: Property<Boolean> = objects.property(defaultValue = false)

    /**
     * Enables code inference that augments routing with inferred metadata.
     * Defaults to `true`.
     */
    public val codeInferenceEnabled: Property<Boolean> = objects.property(defaultValue = true)

    /**
     * When enabled, only routing calls with a preceding comment (KDoc or line comment) are processed.
     * Defaults to `false`, meaning all routing calls are processed except those explicitly marked with `@ignore`.
     */
    public val onlyCommented: Property<Boolean> = objects.property(defaultValue = false)

    /**
     * The title of the API.
     */
    @Deprecated("This information is now configured in the application at runtime.  You may remove this property")
    public val title: Property<String> = objects.property(defaultValue = null)

    /**
     * A short summary of the API.
     */
    @Deprecated("This information is now configured in the application at runtime.  You may remove this property")
    public val summary: Property<String> = objects.property(defaultValue = null)

    /**
     * A description of the API. CommonMark syntax MAY be used for rich text representation.
     */
    @Deprecated("This information is now configured in the application at runtime.  You may remove this property")
    public val description: Property<String> = objects.property(defaultValue = null)

    /**
     * A URI for the Terms of Service for the API. This MUST be in the form of a URI.
     */
    @Deprecated("This information is now configured in the application at runtime.  You may remove this property")
    public val termsOfService: Property<String> = objects.property(defaultValue = null)

    /**
     * The contact information for the exposed API.
     */
    @Deprecated("This information is now configured in the application at runtime.  You may remove this property")
    public val contact: Property<String> = objects.property(defaultValue = null)

    /**
     * The license information for the exposed API.
     */
    @Deprecated("This information is now configured in the application at runtime.  You may remove this property")
    public val license: Property<String> = objects.property(defaultValue = null)

    /**
     * The version of the OpenAPI Document (which is distinct from the OpenAPI Specification version or the version of the API being described or the version of the OpenAPI Description).
     */
    @Deprecated("This information is now configured in the application at runtime.  You may remove this property")
    public val version: Property<String> = objects.property(defaultValue = null)
}