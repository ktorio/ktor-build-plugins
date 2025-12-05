package io.ktor.samples.openapi

import io.ktor.annotate.*
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(CIO, port = 8080) {
        install(ContentNegotiation) {
            json(Json {
                encodeDefaults = false
            })


        routing {
            // Main page for marketing
            get("/") {
                call.respondText("<html><body><h1>Hello, World</h1></body></html>", ContentType.Text.Html)
            }

            get("/spec.json") {
                val spec = generateOpenApiSpec(
                    info = OpenApiInfo("My API", version = "1.0.0"),
                    route = call.application.routingRoot
                ).let {
                    it.copy(
                        paths = it.paths - "/spec.json"
                    )
                }
                call.respond(spec)
            }

            /**
             * API endpoints for users.
             */
            userCrud(ListRepository())

            /**
             * Get the OpenAPI specification.
             */
            // openAPI("/docs", swaggerFile = "openapi/generated.json")
        }
    }.start(wait = true)
}

fun Routing.userCrud(repository: Repository<User>) {
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
             * @path id The ID of the user
             * @response 404 The user was not found
             * @response 200 [User] The user.
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
}



interface Repository<E> {
    fun get(id: String): E?
    fun save(entity: E)
    fun delete(id: String)
    fun list(query: Map<String, List<String>>): List<E>
}

interface Entity {
    val id: String
}

class ListRepository<E: Entity>: Repository<E> {
    private val list: MutableList<E> = mutableListOf()

    override fun list(query: Map<String, List<String>>): List<E> {
        return list.toList()
    }

    override fun get(id: String): E? {
        return list.find { it.id == id }
    }

    override fun save(entity: E) {
        list.add(entity)
    }

    override fun delete(id: String) {
        list.removeIf { it.id == id }
    }
}

@Serializable
data class User(override val id: String, val name: String): Entity