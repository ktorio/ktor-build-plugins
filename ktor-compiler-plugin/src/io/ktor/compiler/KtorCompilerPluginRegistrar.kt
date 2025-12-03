package io.ktor.compiler

import io.ktor.openapi.Logger
import io.ktor.openapi.fir.OpenApiAnalysisExtension
import io.ktor.openapi.ir.OpenApiCodeGenerationExtension
import io.ktor.openapi.OpenApiProcessorConfig
import io.ktor.openapi.model.SpecInfo
import io.ktor.openapi.routing.RouteCallLookup
import kotlinx.serialization.ExperimentalSerializationApi
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalSerializationApi::class)
class KtorCompilerPluginRegistrar : CompilerPluginRegistrar() {

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val openApiConfig = readConfiguration(configuration)
        if (!openApiConfig.enabled) {
            return
        }

        val messageCollector = configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        val logger = Logger.wrap(messageCollector, openApiConfig.debug)
        val routes: RouteCallLookup = mutableMapOf()
        // Analysis FIR plugin reads the comments and caches them to the routes graph
        FirExtensionRegistrarAdapter.registerExtension(
            OpenApiAnalysisExtension(
                logger,
                routes,
                openApiConfig.onlyCommented,
            )
        )
        // Code generation plugin introduces calls to the routing annotation API
        IrGenerationExtension.registerExtension(OpenApiCodeGenerationExtension(logger, routes, openApiConfig.codeInference))
    }

    private fun readConfiguration(
        cc: CompilerConfiguration,
    ): OpenApiProcessorConfig =
        with(KtorCommandLineProcessor) {
            OpenApiProcessorConfig(
                enabled = cc[OPENAPI_ENABLED_KEY]?.toBooleanStrictOrNull() ?: false,
                debug = cc[OPENAPI_DEBUG_KEY]?.toBooleanStrictOrNull() ?: false,
                codeInference = cc[OPENAPI_CODE_INFERENCE_KEY]?.toBooleanStrictOrNull() ?: false,
                onlyCommented = cc[OPENAPI_ONLY_COMMENTED_KEY]?.toBooleanStrictOrNull() ?: false,
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