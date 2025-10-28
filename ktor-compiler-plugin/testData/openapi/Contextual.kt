// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

fun Application.contextual() {
    routing {
        get("/contextual") {
            call.respond(Resp(LocalDateTime.now()))
        }
    }
}

@Serializable
class Resp (@Contextual val dt: LocalDateTime)

/* GENERATED_FIR_TAGS: classDeclaration, primaryConstructor, propertyDeclaration */
