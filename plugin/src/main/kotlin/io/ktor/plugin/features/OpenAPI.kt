package io.ktor.plugin.features

import io.ktor.plugin.*
import io.ktor.plugin.internal.KotlinVersion
import io.ktor.plugin.internal.whenKotlinPluginApplied
import org.gradle.api.Project
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

internal fun Project.configureOpenApi() {
    val ext = createKtorExtension<OpenApiExtension>("openApi")

    whenKotlinPluginApplied {
        // The Ktor OpenAPI compiler plugin artifact depends on Kotlin compiler internals that are
        // only available starting from KotlinVersion.V2_4_0; loading it on older Kotlin versions
        // results in a NoClassDefFoundError at compile time. Skip applying it on incompatible
        // Kotlin versions instead of failing the build for users who don't even use OpenAPI.
        if (!isOpenApiCompilerPluginSupported()) return@whenKotlinPluginApplied
        pluginManager.apply(CompilerPlugin::class.java)
    }

    afterEvaluate {
        try {
            if (ext.enabled.get()) {
                if (!isOpenApiCompilerPluginSupported()) {
                    logger.warn(
                        "warning: Ktor OpenAPI inference is enabled but requires Kotlin " +
                            "${KotlinVersion.V2_4_0} or higher (found ${getKotlinPluginVersion()}). " +
                            "OpenAPI inference will be skipped."
                    )
                    return@afterEvaluate
                }
                if (!hasRoutingAnnotateDependency()) {
                    // Automatically add the missing dependency to the implementation configuration
                    dependencies.add("implementation", "io.ktor:ktor-server-routing-openapi:${KtorGradlePlugin.KTOR_VERSION}")
                    logger.info("Ktor annotations dependency automatically included")
                }
            } else {
                logger.debug("OpenAPI inference is disabled")
            }
        } catch (_: Throwable) {
            logger.warn("Could not apply compiler plugin. OpenAPI inference will not be available.")
        }
    }
}

/**
 * Whether the currently applied Kotlin Gradle plugin is new enough to load the Ktor OpenAPI
 * compiler plugin artifact. The compiler plugin uses Kotlin compiler internals that are only
 * available starting from [KotlinVersion.V2_4_0].
 */
private fun Project.isOpenApiCompilerPluginSupported(): Boolean {
    val rawVersion = try {
        getKotlinPluginVersion()
    } catch (_: Throwable) {
        return false
    }
    val version = try {
        KotlinVersion.parse(rawVersion)
    } catch (_: Throwable) {
        // If we can't parse the version, assume it's a recent enough development build.
        return true
    }
    return version >= KotlinVersion.V2_4_0
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
     * Enables debug logging for OpenAPI feature.
     * Defaults to `false`.
     */
    public val debug: Property<Boolean> = objects.property(defaultValue = false)

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
