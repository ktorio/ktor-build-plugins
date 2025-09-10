// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.server.application.Application
import io.ktor.server.request.header
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.parameters() {
    routing {
        get("/parameters/{a}/{b}/{c}") {
            call.respondText(listOf(
                call.parameters["a"],
                call.pathParameters["b"],
                call.request.pathVariables["c"],
                call.queryParameters["d"],
                call.request.queryParameters["e"],
                call.request.headers["f"],
                call.request.headers.getAll("g"),
                call.request.header("h")
            ).joinToString())
        }
    }
}

/* GENERATED_FIR_TAGS: funWithExtensionReceiver, functionDeclaration */
