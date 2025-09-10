package io.ktor.compiler

import io.ktor.openapi.OpenApiExtension
import io.ktor.openapi.OpenApiProcessorConfig
import io.ktor.openapi.model.SpecInfo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.incrementalCompilation
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalSerializationApi::class)
class KtorCompilerPluginRegistrar : CompilerPluginRegistrar() {

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val openApiConfig = readConfiguration(configuration)
        if (!openApiConfig.enabled) {
            return
        }

        val extension = OpenApiExtension(openApiConfig)
        FirExtensionRegistrarAdapter.registerExtension(extension)
        registerDisposable {
            extension.saveSpecification(Json {
                prettyPrint = true
                prettyPrintIndent = "    "
            })
        }
    }

    private fun readConfiguration(
        cc: CompilerConfiguration,
    ): OpenApiProcessorConfig =
        with(KtorCommandLineProcessor) {
            OpenApiProcessorConfig(
                enabled = cc[OPENAPI_ENABLED_KEY]?.toBooleanStrictOrNull() ?: false,
                outputFile = cc[OPENAPI_OUTPUT_KEY] ?: "openapi.json",
                info = SpecInfo(
                    title = cc[OPENAPI_TITLE_KEY] ?: "API Documentation",
                    version = cc[OPENAPI_VERSION_KEY] ?: "1.0.0",
                    summary = cc[OPENAPI_SUMMARY_KEY],
                    description = cc[OPENAPI_DESCRIPTION_KEY],
                    termsOfService = cc[OPENAPI_TERMS_OF_SERVICE_KEY],
                    contact = cc[OPENAPI_CONTACT_KEY],
                    license = cc[OPENAPI_LICENSE_KEY]
                )
            )
        }
}