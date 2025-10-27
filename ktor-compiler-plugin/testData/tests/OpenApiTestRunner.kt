package tests

import com.typesafe.config.ConfigFactory
import io.ktor.annotate.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.openapi.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.writeText
import kotlin.test.assertEquals

const val MODULE_REFERENCE = ""
const val ACTUAL_FILE = ""
const val SNAPSHOT_FILE = ""
const val SNAPSHOT_REPLACE = false
const val EXPECTED_JSON = ""

val json = Json {
    encodeDefaults = false
    prettyPrint = true
    prettyPrintIndent = "    "
}

fun box(): String {
    testApplication {
        environment {
            config = HoconApplicationConfig(
                ConfigFactory.parseString(
                    """
                        ktor {
                            application {
                                modules = [ $MODULE_REFERENCE ]
                            }
                        }
                    """.trimIndent()
                )
            )
        }

        routing {
            get("/openapi.json") {
                val root = call.application.routingRoot
                root.findPathItems()
                val routes = generateOpenApiSpec(
                    info = OpenApiInfo("OpenAPI Document", version = "1.0.0"),
                    route = root,
                )
                call.respondText(json.encodeToString(routes.copy(
                    paths = routes.paths - "/routes"
                )))
            }
        }
        val responseJson = client.get("/openapi.json").bodyAsText()
        val actualFile = Path(ACTUAL_FILE)
        val expectedFile = Path(SNAPSHOT_FILE)
        actualFile.writeText(responseJson)
        if (SNAPSHOT_REPLACE) {
            actualFile.copyTo(expectedFile, overwrite = true)
        } else {
            assertEquals(EXPECTED_JSON.trim(), responseJson.trim())
        }
    }
    return "OK"
}