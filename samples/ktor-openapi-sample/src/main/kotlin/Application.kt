package io.ktor.samples.openapi

import io.ktor.annotate.*
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.openapi.*
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(CIO, port = 8080) {
        install(ContentNegotiation) {
            json(Json {
                encodeDefaults = false
            })
        }

        routing {
            // Main page for marketing
            get("/") {
                call.respondText("<html><body><h1>Hello, World</h1></body></html>", ContentType.Text.Html)
            }

            /**
             * API endpoints for users.
             *
             * These will appear in the resulting OpenAPI document.
             */
            val apiRoute = userCrud(ListRepository())

            get("/docs.json") {
                val docs = generateOpenApiDoc(
                    OpenApiDoc(info = OpenApiInfo("My API", "1.0")),
                    apiRoute.descendants()
                )
                call.respond(docs)
            }

            /**
             * View the generated UI for the API spec.
             */
            openAPI("/openApi")

            /**
             * View the Swagger flavor of the UI for the API spec.
             */
            swaggerUI("/swaggerUI") {
                info = OpenApiInfo("My API", "1.0")
                source = OpenApiDocSource.RoutingSource(ContentType.Application.Json) {
                    apiRoute.descendants()
                }
            }
        }
    }.start(wait = true)
}

fun Routing.userCrud(repository: Repository<User>): Route  =
    route("/api") {

        route("/users") {

            /**
             * Get a list of users.
             *
             * @response 200 [User]+ A list of users.
             */
            get {
                val query = call.request.queryParameters.toMap()
                val list = repository.list(query)
                call.respond(list)
            }

            /**
             * Get a single user
             *
             * - Path: id The ID of the user
             * - Response: 404 The user was not found
             * - Response: 200 [User] The user.
             */
            get("{id}") {
                val user = repository.get(call.parameters["id"]!!)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(user)
            }

            /**
             * Save a new user.
             *
             * @body [User] the user to save.
             */
            post {
                repository.save(call.receive())
                call.respond(HttpStatusCode.Created)
            }

            /**
             * Delete a user.
             * @path id The ID of the user
             * @response 204 The user was deleted
             */
            delete("{id}") {
                repository.delete(call.parameters["id"]!!)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }