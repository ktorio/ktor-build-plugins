package io.ktor.samples.openapi

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.toMap
import kotlinx.serialization.Serializable

fun main() {
    embeddedServer(CIO, port = 8080) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            // Main page for marketing
            get("/") {
                call.respondText("<html><body><h1>Hello, World</h1></body></html>", ContentType.Text.Html)
            }

            /**
             * API endpoints for users.
             */
            userCrud(ListRepository())

            /**
             * Get the OpenAPI specification.
             */
            openAPI("/docs", swaggerFile = "api.json")
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