package io.ktor.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

class KtorCommandLineProcessor : CommandLineProcessor {
    companion object {
        const val PLUGIN_ID = "io.ktor.ktor-compiler-plugin"

        val MAIN_CLASS_KEY = CompilerConfigurationKey<String>("mainClass")
        val OPENAPI_ENABLED_KEY = CompilerConfigurationKey<String>("openapi.enabled")
        val OPENAPI_OUTPUT_KEY = CompilerConfigurationKey<String>("openapi.output")
        val OPENAPI_DESCRIPTION_KEY = CompilerConfigurationKey<String>("openapi.description")
        val OPENAPI_TITLE_KEY = CompilerConfigurationKey<String>("openapi.title")
        val OPENAPI_SUMMARY_KEY = CompilerConfigurationKey<String>("openapi.summary")
        val OPENAPI_TERMS_OF_SERVICE_KEY = CompilerConfigurationKey<String>("openapi.termsOfService")
        val OPENAPI_CONTACT_KEY = CompilerConfigurationKey<String>("openapi.contact")
        val OPENAPI_LICENSE_KEY = CompilerConfigurationKey<String>("openapi.license")
        val OPENAPI_VERSION_KEY = CompilerConfigurationKey<String>("openapi.version")

        val MAIN_CLASS_OPTION = CliOption(
            "mainClass",
            "<fqName>",
            "Fully qualified name of the application's main class",
            required = false
        )

        val OPENAPI_ENABLED_OPTION = CliOption(
            "openapi.enabled",
            "<boolean>",
            "Enables the OpenAPI generation",
            required = false
        )

        val OPENAPI_OUTPUT_OPTION = CliOption(
            "openapi.output",
            "<path>",
            "The output path for the generated OpenAPI specification",
            required = false
        )

        val OPENAPI_DESCRIPTION_OPTION = CliOption(
            "openapi.description",
            "<text>",
            "A description of the API. CommonMark syntax MAY be used for rich text representation",
            required = false
        )

        val OPENAPI_TITLE_OPTION = CliOption(
            "openapi.title",
            "<text>",
            "The title of the API",
            required = false
        )

        val OPENAPI_SUMMARY_OPTION = CliOption(
            "openapi.summary",
            "<text>",
            "A short summary of the API",
            required = false
        )

        val OPENAPI_TERMS_OF_SERVICE_OPTION = CliOption(
            "openapi.termsOfService",
            "<uri>",
            "A URI for the Terms of Service for the API",
            required = false
        )

        val OPENAPI_CONTACT_OPTION = CliOption(
            "openapi.contact",
            "<info>",
            "The contact information for the exposed API",
            required = false
        )

        val OPENAPI_LICENSE_OPTION = CliOption(
            "openapi.license",
            "<info>",
            "The license information for the exposed API",
            required = false
        )

        val OPENAPI_VERSION_OPTION = CliOption(
            "openapi.version",
            "<version>",
            "The version of the OpenAPI Document",
            required = false
        )
    }

    override val pluginId: String get() = PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> get() = listOf(
        MAIN_CLASS_OPTION,
        OPENAPI_ENABLED_OPTION,
        OPENAPI_OUTPUT_OPTION,
        OPENAPI_DESCRIPTION_OPTION,
        OPENAPI_TITLE_OPTION,
        OPENAPI_SUMMARY_OPTION,
        OPENAPI_TERMS_OF_SERVICE_OPTION,
        OPENAPI_CONTACT_OPTION,
        OPENAPI_LICENSE_OPTION,
        OPENAPI_VERSION_OPTION
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option) {
            MAIN_CLASS_OPTION -> configuration.put(MAIN_CLASS_KEY, value)
            OPENAPI_ENABLED_OPTION -> configuration.put(OPENAPI_ENABLED_KEY, value)
            OPENAPI_OUTPUT_OPTION -> configuration.put(OPENAPI_OUTPUT_KEY, value)
            OPENAPI_DESCRIPTION_OPTION -> configuration.put(OPENAPI_DESCRIPTION_KEY, value)
            OPENAPI_TITLE_OPTION -> configuration.put(OPENAPI_TITLE_KEY, value)
            OPENAPI_SUMMARY_OPTION -> configuration.put(OPENAPI_SUMMARY_KEY, value)
            OPENAPI_TERMS_OF_SERVICE_OPTION -> configuration.put(OPENAPI_TERMS_OF_SERVICE_KEY, value)
            OPENAPI_CONTACT_OPTION -> configuration.put(OPENAPI_CONTACT_KEY, value)
            OPENAPI_LICENSE_OPTION -> configuration.put(OPENAPI_LICENSE_KEY, value)
            OPENAPI_VERSION_OPTION -> configuration.put(OPENAPI_VERSION_KEY, value)
            else -> error("Unexpected option: ${option.optionName}")
        }
    }
}