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

fun Application.complexExtension(userRepository: Repository2<User2>, messageRepository: Repository2<Message2>) {
    install(ContentNegotiation) {
        json()
    }

    routing {
        route("/api") {
            crudEndpoints("users", userRepository)
            crudEndpoints("messages", messageRepository)
        }
    }
}

private fun <E> Route.crudEndpoints(path: String, repository: Repository2<E>) {
    route("data/$path") {
        readEndpoints(repository)
        modificationEndpoints(repository)
    }
}


private fun <E> Route.readEndpoints(repository: Repository2<E>) {
    get {
        val query = call.request.queryParameters.toMap()
        val list = repository.list(query)
        call.respond(list)
    }
    get("{id}") {
        val item = repository.get(call.parameters["id"]!!)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(item)
    }
}

private fun <E> Route.modificationEndpoints(repository: Repository2<E>) {
    post {
        repository.save(call.receive())
        call.respond(HttpStatusCode.Created)
    }

    delete("{id}") {
        repository.delete(call.parameters["id"]!!)
        call.respond(HttpStatusCode.NoContent)
    }
}

interface Repository2<E> {
    fun get(id: String): E?
    fun save(entity: E)
    fun delete(id: String)
    fun list(query: Map<String, List<String>>): List<E>
}

data class User2(val id: String, val name: String)
data class Message2(val id: String, val text: String)