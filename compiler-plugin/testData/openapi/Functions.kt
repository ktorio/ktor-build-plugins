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

fun Application.extracted(userRepository: Repository1<User1>, messageRepository: Repository1<Message1>) {
    install(ContentNegotiation) {
        json()
    }

    routing {
        route("/api") {
            userEndpoints(userRepository)
            messageEndpoints(messageRepository)
        }
    }
}

private fun Route.userEndpoints(repository: Repository1<User1>) {
    route("/users") {
        userReadEndpoints(repository)
        userModificationEndpoints(repository)
    }
}

private fun Route.messageEndpoints(repository: Repository1<Message1>) {
    route("/messages") {
        messageReadEndpoints(repository)
        messageModificationEndpoints(repository)
    }
}

private fun Route.userModificationEndpoints(repository: Repository1<User1>) {
    /**
     * Save a new user.
     *
     * @body [User1] The user to save.
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

private fun Route.userReadEndpoints(repository: Repository1<User1>) {
    /**
     * Get a list of users.
     *
     * @response 200 A list of users.
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
}


private fun Route.messageModificationEndpoints(repository: Repository1<Message1>) {
    /**
     * Save a new message.
     *
     * @body [Message1] The message to save.
     */
    post {
        repository.save(call.receive())
        call.respond(HttpStatusCode.Created)
    }

    /**
     * Delete a message.
     * @path id The ID of the message
     */
    delete("{id}") {
        repository.delete(call.parameters["id"]!!)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.messageReadEndpoints(repository: Repository1<Message1>) {
    /**
     * Get a list of messages.
     *
     * @response 200 A list of messages.
     */
    get {
        val query = call.request.queryParameters.toMap()
        val list = repository.list(query)
        call.respond(list)
    }

    /**
     * Get a single message
     *
     * @path id The ID of the message
     * @response 404 The message was not found
     */
    get("{id}") {
        val message = repository.get(call.parameters["id"]!!)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(message)
    }
}

interface Repository1<E> {
    fun get(id: String): E?
    fun save(entity: E)
    fun delete(id: String)
    fun list(query: Map<String, List<String>>): List<E>
}

data class User1(val id: String, val name: String)
data class Message1(val id: String, val text: String)