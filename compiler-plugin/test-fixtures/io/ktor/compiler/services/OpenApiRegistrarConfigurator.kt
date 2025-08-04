package io.ktor.compiler.services

import io.ktor.compiler.services.KtorTestEnvironmentProperties.openApiOutputFile
import io.ktor.openapi.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

class OpenApiRegistrarConfigurator(
    testServices: TestServices,
) : EnvironmentConfigurator(testServices) {

    lateinit var extension: OpenApiExtension

    @OptIn(ExperimentalSerializationApi::class)
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        val openApiConfig = OpenApiProcessorConfig(
            enabled = true,
            outputFile = testServices.openApiOutputFile,
        )
        extension = OpenApiExtension(openApiConfig)
        FirExtensionRegistrarAdapter.registerExtension(extension)
    }
}
