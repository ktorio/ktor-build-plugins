// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.jsonIo
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondSource
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.utils.io.writeInt
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
fun Application.installResponses() {
    val fs: FileSystem = SystemFileSystem

    install(ContentNegotiation) {
        jsonIo()
    }

    routing {
        get("/json") {
            call.respondText("\"Hello, world!\"", ContentType.Application.Json)
        }
        get("/binary") {
            call.response.headers.append("X-Question", "life, the universe, and everything")
            call.respondBytesWriter {
                writeInt(42)
            }
        }
        get("/file") {
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"foo.zip\"")
            call.respondFile(File("foo.zip"))
        }
        get("/audio") {
            call.respondSource(fs.source(Path("foo.mp3")), contentType = ContentType.Audio.MPEG)
        }
        route("/api") {
            get("/dudes") {
                call.respond(listOf(
                    Dude(1, "John"),
                    Dude(2, "Denis")
                ))
            }
        }
    }
}

@Serializable
data class Dude(val id: Int, val name: String)

/* GENERATED_FIR_TAGS: classDeclaration, classReference, data, funWithExtensionReceiver, functionDeclaration,
primaryConstructor, propertyDeclaration */
