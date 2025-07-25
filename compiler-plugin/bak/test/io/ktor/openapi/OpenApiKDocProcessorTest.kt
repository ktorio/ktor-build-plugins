
package io.ktor.openapi

import io.ktor.compiler.*
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

private val REPLACE_SNAPSHOTS = System.getenv("REPLACE_OPENAPI_SNAPSHOTS") != null

class OpenAPIKDocProcessorTest {

    @Test
    fun `test route with path parameter`() {
        val result = compile(
            "basic/DomainTypes.kt",
            "basic/SimpleRoutingModule.kt",
        )
        assertOutputMatches("expected.json", result)
    }

    private fun assertOutputMatches(expectedFileName: String, actualOutput: CompilationResult) {
        val expectedPath = Paths.get("src/test/resources/openapi/$expectedFileName")
        val expectedOutput = expectedPath.readText()
        val actualOutputJson = actualOutput.openApiOutput.trim()
        if (REPLACE_SNAPSHOTS) {
            expectedPath.writeText(actualOutputJson)
        }
        assertEquals(expectedOutput, actualOutputJson)
    }

}