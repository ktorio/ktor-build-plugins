package io.ktor.compiler

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

class KtorCommandLineProcessor : CommandLineProcessor {
    companion object {
        const val PLUGIN_ID = "io.ktor.ktor-compiler-plugin"

        val OPENAPI_ENABLED_KEY = CompilerConfigurationKey<String>("openapiEnabled")
        val OPENAPI_DEBUG_KEY = CompilerConfigurationKey<String>("openapiDebug")
        val OPENAPI_CODE_INFERENCE_KEY = CompilerConfigurationKey<String>("openapiCodeInference")
        val OPENAPI_ONLY_COMMENTED_KEY = CompilerConfigurationKey<String>("openapiOnlyCommented")

        val OPENAPI_ENABLED_OPTION = CliOption(
            "openapiEnabled",
            "<boolean>",
            "Enables the OpenAPI generation",
            required = false
        )

        val OPENAPI_DEBUG_OPTION = CliOption(
            "openapiDebug",
            "<boolean>",
            "Writes exception stack traces to messages",
            required = false
        )

        val OPENAPI_CODE_INFERENCE_OPTION = CliOption(
            "openapiCodeInference",
            "<boolean>",
            "Enables code inference for OpenAPI (experimental)",
            required = false
        )

        val OPENAPI_ONLY_COMMENTED_OPTION = CliOption(
            "openapiOnlyCommented",
            "<boolean>",
            "Only process routing calls that have a preceding comment (KDoc or line comment)",
            required = false
        )
    }

    override val pluginId: String get() = PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> get() = listOf(
        OPENAPI_ENABLED_OPTION,
        OPENAPI_DEBUG_OPTION,
        OPENAPI_CODE_INFERENCE_OPTION,
        OPENAPI_ONLY_COMMENTED_OPTION,
    )

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        when (option) {
            OPENAPI_ENABLED_OPTION -> configuration.put(OPENAPI_ENABLED_KEY, value)
            OPENAPI_DEBUG_OPTION -> configuration.put(OPENAPI_DEBUG_KEY, value)
            OPENAPI_CODE_INFERENCE_OPTION -> configuration.put(OPENAPI_CODE_INFERENCE_KEY, value)
            OPENAPI_ONLY_COMMENTED_OPTION -> configuration.put(OPENAPI_ONLY_COMMENTED_KEY, value)
            else -> error("Unexpected option: ${option.optionName}")
        }
    }
}