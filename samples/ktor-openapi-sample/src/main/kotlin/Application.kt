package io.ktor.samples.openapi

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

fun main() {
    embeddedServer(CIO, port = 8080) {
        routing {
            // Main page for marketing
            get("/") {
                call.respondText("<html><body><h1>Hello, World</h1></body></html>", ContentType.Text.Html)
            }

            /*
             * Save a message.
             *
             * @body [Message] the message to save
             */
            post("/messages") {
                call.respond(HttpStatusCode.Created)
            }

            /**
             * Get a list of messages.
             *
             * @response 200 [List<Message>] A list of messages
             */
            get("/messages") {
                call.respond(listOf(Message("Hello, world!")))
            }
        }
    }.start(wait = true)
}

@Serializable
data class Message(val message: String)