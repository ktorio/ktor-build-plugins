package samples.basic

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