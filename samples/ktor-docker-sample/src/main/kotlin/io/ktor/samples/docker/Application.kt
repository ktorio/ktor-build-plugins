package io.ktor.samples.docker

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(CIO, port = 8080) {
        routing {
            get("/") {
                call.respondText("Hello ${System.getenv()["NAME"] ?: "World"}!")
            }
        }
    }.start(wait = true)
}
