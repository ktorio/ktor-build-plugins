package io.ktor.compiler.services

import io.ktor.compiler.services.KtorTestEnvironmentProperties.openApiExpectedFile
import io.ktor.compiler.services.KtorTestEnvironmentProperties.openApiExtension
import io.ktor.compiler.services.KtorTestEnvironmentProperties.openApiOutputFile
import io.ktor.compiler.services.KtorTestEnvironmentProperties.replaceSnapshots
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.test.WrappedException
import org.jetbrains.kotlin.test.model.AfterAnalysisChecker
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions
import java.nio.file.Files
import java.nio.file.Paths

class OpenApiSnapshotVerificationService(testServices: TestServices): AfterAnalysisChecker(testServices) {
    @OptIn(ExperimentalSerializationApi::class)
    override fun check(failedAssertions: List<WrappedException>) {
        // extension should match actual file for output
        testServices.openApiExtension.saveSpecification(Json {
            prettyPrint = true
            prettyPrintIndent = "    "
        })
        val actualFile = Paths.get(testServices.openApiOutputFile)
        val actualJson = Files.readString(actualFile)
        if (replaceSnapshots) {
            Files.writeString(Paths.get(testServices.openApiExpectedFile), actualJson)
        } else {
            val expectedFile = Paths.get(testServices.openApiExpectedFile)
            if (!Files.exists(expectedFile)) {
                Files.writeString(expectedFile, actualJson)
                testServices.assertions.fail {
                    "Expected snapshot file did not exist, created it at: ${testServices.openApiExpectedFile}"
                }
            } else {
                val expected = Files.readString(expectedFile)
                testServices.assertions.assertEquals(expected, actualJson) {
                    "Expected snapshot contents to match for the file: $actualFile"
                }
            }
        }
    }
}