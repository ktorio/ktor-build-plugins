// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*

fun Application.routing(repository: Repository<User>) {
    routing {
        route("/api") {
            /**
             * Get a single user
             * @param id The ID of the user
             * @response 404 The user was not found
             */
            get("/users/{id}") {
                val user = repository.get(call.parameters["id"]!!)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(user)
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

data class User(val id: String, val name: String)