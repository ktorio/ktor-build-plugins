// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.util.toMap

fun Application.simpleNested(repository: Repository0<User0>) {
    install(ContentNegotiation) {
        json()
    }

    routing {
        route("/api") {

            route("/users") {

                /**
                 * Get a list of users.
                 *
                 * @response 200 [List<User>] A list of users.
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
                 */
                get("{id}") {
                    val user = repository.get(call.parameters["id"]!!)
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respond(user)
                }

                /**
                 * Save a new user.
                 *
                 * @body [User0] the user to save.
                 */
                post {
                    repository.save(call.receive())
                    call.respond(HttpStatusCode.Created)
                }

                /**
                 * Delete a user.
                 * @path id The ID of the user
                 */
                delete("{id}") {
                    repository.delete(call.parameters["id"]!!)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

interface Repository0<E> {
    fun get(id: String): E?
    fun save(entity: E)
    fun delete(id: String)
    fun list(query: Map<String, List<String>>): List<E>
}

data class User0(val id: String, val name: String)