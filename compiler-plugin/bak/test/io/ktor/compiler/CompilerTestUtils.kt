package io.ktor.compiler

import com.intellij.openapi.Disposable
import io.ktor.openapi.*
import io.ktor.openapi.model.*
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import java.io.File
import java.net.URL
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun compile(vararg paths: String): CompilationResult =
    compile(paths.map {
        Thread.currentThread().contextClassLoader.getResource("samples/$it")
            ?: error("Resource /samples/$it not found")
    })

fun compile(files: List<URL>): CompilationResult {
    // Set up compiler configuration
    val messageCollector = TestMessageCollector()
    val configuration = CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
        put(CommonConfigurationKeys.MODULE_NAME, "test")
        put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, LanguageVersionSettingsImpl.DEFAULT)
        put(CommonConfigurationKeys.USE_FIR, true)
        put(CommonConfigurationKeys.USE_FIR_EXTRA_CHECKERS, true)
        put(CommonConfigurationKeys.USE_FIR_EXPERIMENTAL_CHECKERS, true)


        put(
            JVMConfigurationKeys.JDK_HOME,
            System.getenv("JAVA_HOME")
                ?.let { File(it) }
                ?: File(System.getProperty("java.home"))
        )

        addJvmClasspathRoots(
            System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .filter { it.endsWith(".jar") }
                .map(::File)
        )
        addKotlinSourceRoot(files.first().path.substringBeforeLast("/"))
    }
    val tempFile = File.createTempFile("openApi", ".json")
    val openApiConfig = OpenApiProcessorConfig(
        enabled = true,
        mainClass = "test.MainKt",
        outputFile = tempFile.absolutePath,
        info = SpecInfo(
            title = "OpenAPI Test",
            version = "1.0.0",
        )
    )

    val disposable = Disposable {}
    // Create a simpler test environment
    val environment = KotlinCoreEnvironment.createForProduction(
        disposable,
        configuration,
        EnvironmentConfigFiles.JVM_CONFIG_FILES
    )

    val openApiExtension = OpenApiExtensionRegistrar(openApiConfig)
    FirExtensionRegistrarAdapter.registerExtensionPoint(environment.project)
    FirExtensionRegistrarAdapter.registerExtension(environment.project, openApiExtension)

    assertTrue(KotlinToJVMBytecodeCompiler.compileBunchOfSources(environment))

    assertFalse(
        messageCollector.hasErrors(),
        "Compilation failed:\n${messageCollector.getMessages().joinToString("\n").prependIndent("  - ")}"
    )

    return CompilationResult(
        messages = messageCollector.getMessages(),
        openApiOutput = tempFile.readText()
    )
}


class TestMessageCollector : MessageCollector {
    private val messages = mutableListOf<String>()

    override fun clear() {
        messages.clear()
    }

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?
    ) {
        val formattedMessage = "[$severity] ${location?.line} $message"
        println(formattedMessage)
        messages.add(formattedMessage)
    }

    override fun hasErrors(): Boolean = messages.any { it.startsWith("[ERROR]") }

    fun getMessages(): List<String> = messages.toList()
}

data class CompilationResult(
    val messages: List<String>,
    val openApiOutput: String
)