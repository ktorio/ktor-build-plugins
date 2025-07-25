package io.ktor.compiler.services

import io.ktor.compiler.services.KtorTestEnvironmentProperties.testSamplesLocation
import io.ktor.openapi.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.testInfo

class OpenApiRegistrarConfigurator(
    testServices: TestServices,
) : EnvironmentConfigurator(testServices) {

    @OptIn(ExperimentalSerializationApi::class)
    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        val testCase = testServices.testInfo.methodName.removePrefix("test").lowercase()
        val openApiConfig = OpenApiProcessorConfig(
            enabled = true,
            outputFile = "$testSamplesLocation/openapi/$testCase.json",
        )
        val extension = OpenApiExtension(openApiConfig)
        FirExtensionRegistrarAdapter.registerExtension(extension)
        registerDisposable {
            extension.saveSpecification(Json {
                prettyPrint = true
                prettyPrintIndent = "    "
            })
        }
    }
}
