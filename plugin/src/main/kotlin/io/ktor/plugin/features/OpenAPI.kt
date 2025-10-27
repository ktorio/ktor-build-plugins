package io.ktor.plugin.features

import io.ktor.plugin.*
import io.ktor.plugin.internal.whenKotlinPluginApplied
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

internal fun Project.configureOpenApi() {
    createKtorExtension<OpenApiExtension>("openApi")
    try {
        whenKotlinPluginApplied {
            pluginManager.apply(CompilerPlugin::class.java)
        }
    } catch (_: Throwable) {
        logger.warn("Could not apply compiler plugin. OpenAPI generation might not work.")
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
     * Whether to generate an OpenAPI specification or not.
     * Defaults to `true`.
     */
    public val enabled: Property<Boolean> = objects.property(defaultValue = true)

    /**
     * The title of the API.
     */
    public val title: Property<String> = objects.property(defaultValue = null)

    /**
     * A short summary of the API.
     */
    public val summary: Property<String> = objects.property(defaultValue = null)

    /**
     * A description of the API. CommonMark syntax MAY be used for rich text representation.
     */
    public val description: Property<String> = objects.property(defaultValue = null)

    /**
     * A URI for the Terms of Service for the API. This MUST be in the form of a URI.
     */
    public val termsOfService: Property<String> = objects.property(defaultValue = null)

    /**
     * The contact information for the exposed API.
     */
    public val contact: Property<String> = objects.property(defaultValue = null)

    /**
     * The license information for the exposed API.
     */
    public val license: Property<String> = objects.property(defaultValue = null)

    /**
     * The version of the OpenAPI Document (which is distinct from the OpenAPI Specification version or the version of the API being described or the version of the OpenAPI Description).
     */
    public val version: Property<String> = objects.property(defaultValue = null)
}