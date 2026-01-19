// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Application.installOddReferences() {
    val body = "My response"
    val accepted = HttpStatusCode.Accepted

    routing {
        /**
         * Referencing from a higher scope
         */
        get("/higher-scope") {
            call.respondText(
                body,
                contentType = ContentType.Text.CSV,
                status = accepted
            )
        }

        /**
         * Local declarations; external function return
         */
        get("/external-return") {
            val responseMessage = "My response"
            val customResponse = CustomResponse(responseMessage)
            val responseStatus = acceptedStatus()

            call.respond(
                status = responseStatus,
                message = customResponse,
            )
        }

        /**
         * Reassignment; branches
         */
        get("/reassignment") {
            var contentType = ContentType("application", "message+json")
            if (call.request.queryParameters["plain"] == "true")
                contentType = ContentType.Text.Plain

            call.respondText(body, contentType = contentType)
        }
    }
}

fun acceptedStatus() =
    HttpStatusCode.Accepted

@Serializable
data class CustomResponse(val message: String)