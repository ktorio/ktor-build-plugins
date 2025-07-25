package io.ktor.plugin.features

import io.ktor.plugin.KtorGradleCompilerPlugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property

public abstract class OpenAPIExtension(project: Project) {
    /**
     * Enables the OpenAPI generation.
     * When enabled, the Kotlin compiler plugin will be applied to detect routing functions
     * and generate OpenAPI documentation based on KDoc comments.
     */
    public val enabled: Property<Boolean> = project.property(false)

    /**
     * The output path for the generated OpenAPI specification.
     * Defaults to "build/resources/main/openapi/generated.json"
     * TODO seems to be impossible to leave this optional
     */
    public val output: Property<RegularFile?> = project.objects.fileProperty()

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

internal const val OPENAPI_EXTENSION_KEY = "ktor.openapi.extension"

internal fun Project.configureOpenAPI() {
    val extension = createKtorExtension<OpenAPIExtension>("openApi")
    extensions.extraProperties.set(OPENAPI_EXTENSION_KEY, extension)
    plugins.apply(KtorGradleCompilerPlugin::class.java)
}
