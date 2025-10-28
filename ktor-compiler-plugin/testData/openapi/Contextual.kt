// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Application.contextual() {
    routing {
        get("/contextual") {
            call.respond(Resp(LocalDateTime.now()))
        }
    }
}

@Serializable
class Resp @OptIn(ExperimentalUuidApi::class) constructor(
    @Contextual val dt: LocalDateTime,
    @Contextual val uuid: UUID? = null,
    @Contextual val kUuid: Uuid? = null,
    @Contextual val duration: Duration = Duration.ZERO,
    @Contextual val any: Any? = null
)

/* GENERATED_FIR_TAGS: classDeclaration, primaryConstructor, propertyDeclaration */
