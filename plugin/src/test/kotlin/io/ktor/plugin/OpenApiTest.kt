package io.ktor.plugin

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class OpenApiTest : IntegrationTest() {

    @Test
    fun `openapi compiler plugin works with incremental compilation`() {
        // Enable build reports so we can assert IC behavior in a relatively stable way.
        val reportsDir = file("build/kotlin-build-reports").absolutePath
        file("gradle.properties").writeText(
            """
            kotlin.incremental=true

            # Avoid "no compilation happened" because of caching/up-to-date shortcuts
            org.gradle.caching=false

            kotlin.build.report.output=file
            kotlin.build.report.file.output_dir=$reportsDir
            kotlin.build.report.label=openapi-ic-test
            """.trimIndent()
        )

        buildFile.writeGradle(
            """
            $APPLY_KOTLIN_JVM_AND_KTOR

            repositories {
                mavenCentral()
            }

            ktor {
                openApi {
                    enabled = true
                }
            }

            dependencies {
                implementation("io.ktor:ktor-server-core:$KTOR_VERSION")
                implementation("io.ktor:ktor-server-routing-openapi:$KTOR_VERSION")
            }
            """.trimIndent()
        )

        // A tiny app split across files so we can change *only* Routes.kt between builds.
        file("src/main/kotlin/io/ktor/samples/openapi/Application.kt").writeText(
            """
            package io.ktor.samples.openapi

            import io.ktor.server.application.*
            import io.ktor.server.routing.*

            fun Application.module() {
                routing {
                    registerUserRoutes()
                }
            }
            """.trimIndent()
        )

        val routesFile = file("src/main/kotlin/io/ktor/samples/openapi/Routes.kt")
        routesFile.writeText(
            """
            package io.ktor.samples.openapi

            import io.ktor.server.response.*
            import io.ktor.server.routing.*
            import io.ktor.http.*

            fun Routing.registerUserRoutes() {
                route("/api") {
                    /**
                     * Get a list of users.
                     *
                     * @response 200 [String] ok
                     */
                    get("/users") {
                        call.respondText("users-v1", ContentType.Text.Plain)
                    }
                }
            }
            """.trimIndent()
        )

        // 1) First compilation (baseline)
        runBuild("compileKotlin", "--info")

        // 2) Isolated change: only Routes.kt (should trigger incremental recompilation of just that file)
        routesFile.writeText(
            """
            package io.ktor.samples.openapi

            import io.ktor.server.response.*
            import io.ktor.server.routing.*
            import io.ktor.http.*

            fun Routing.registerUserRoutes() {
                route("/api") {
                    /**
                     * Get a list of users. (v2)
                     *
                     * @response 200 [String] ok
                     */
                    get("/users") {
                        call.respondText("users-v2", ContentType.Text.Plain)
                    }
                }
            }
            """.trimIndent()
        )

        val incrementalCompileRun = runBuild("compileKotlin", "--info")
        assertContains(
            incrementalCompileRun.output,
            "Ktor compiler plugin is enabled",
            message = "Expected Ktor compiler plugin to be enabled in incremental compilation."
        )

        val reportText = findLatestKotlinBuildReportText()
        assertNotNull(reportText, "Expected Kotlin build report to be present.")
        // We want evidence that only Routes.kt was recompiled, not the whole source set.
        // Report formats vary by Kotlin version, so keep checks intentionally tolerant.
        assertContains(
            reportText,
            "Routes.kt",
            message = "Expected Kotlin build report to mention Routes.kt as a compiled/affected source."
        )
        assertFalse(
            "Application.kt" in reportText,
            message = "Expected Kotlin build report to NOT mention Application.kt as compiled due to an isolated change."
        )
    }

    private fun findLatestKotlinBuildReportText(): String? {
        val dir = projectDir.resolve("build/kotlin-build-reports")
        if (!dir.isDirectory) return null

        val report =
            dir.listFiles()
                ?.filter { it.isFile && (it.extension.equals("txt", true) || it.extension.equals("log", true) || it.extension.equals("json", true)) }
                ?.maxByOrNull { it.lastModified() }
                ?: return null

        return report.readText()
    }
}
