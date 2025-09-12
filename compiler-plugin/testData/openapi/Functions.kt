// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.util.toMap
import kotlinx.serialization.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

fun Application.extracted(userRepository: Repository1<User1>, messageRepository: Repository1<Message1>) {
    install(ContentNegotiation) {
        json()
    }

    routing {
        route("/api") {
            userEndpoints(userRepository)
            messageEndpoints(messageRepository)
            advancedEndpoints()
        }
    }
}

private fun Route.userEndpoints(repository: Repository1<User1>) {
    route("/users") {
        userReadEndpoints(repository)
        userModificationEndpoints(repository)
        userAdvancedEndpoints(repository)
    }
}

private fun Route.messageEndpoints(repository: Repository1<Message1>) {
    route("/messages") {
        messageReadEndpoints(repository)
        messageModificationEndpoints(repository)
        messageAdvancedEndpoints(repository)
    }
}

private fun Route.userModificationEndpoints(repository: Repository1<User1>) {
    /**
     * Save a new user.
     *
     * @body [User1] The user to save.
     * @response 201 User created successfully
     * @response 400 Invalid user data
     */
    post {
        repository.save(call.receive())
        call.respond(HttpStatusCode.Created)
    }

    /**
     * Delete a user.
     * @path id The ID of the user
     * @response 204 User deleted successfully
     * @response 404 User not found
     */
    delete("{id}") {
        repository.delete(call.parameters["id"]!!)
        call.respond(HttpStatusCode.NoContent)
    }
    /**
     * Update an existing user.
     *
     * @path id The ID of the user
     * @body [UserUpdateRequest] The user data to update
     * @response 200 User updated successfully
     * @response 400 Invalid user data
     * @response 404 User not found
     */
    put("{id}") {
        val id = call.parameters["id"]!!
        val updateRequest = call.receive<UserUpdateRequest>()

        if (updateRequest.name?.isBlank() == true) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("INVALID_NAME", "Name cannot be blank"))
            return@put
        }

        val user = repository.get(id) ?: return@put call.respond(
            HttpStatusCode.NotFound,
            ErrorResponse("USER_NOT_FOUND", "User with ID $id not found")
        )

        val updatedUser = User1(id = id, name = updateRequest.name ?: user.name)
        repository.save(updatedUser)

        call.respond(HttpStatusCode.OK, updatedUser)
    }

    /**
     * Partially update a user.
     *
     * @path id The ID of the user
     * @body [UserPatchRequest] The user fields to update
     * @response 200 User updated successfully
     * @response 404 User not found
     */
    patch("{id}") {
        val id = call.parameters["id"]!!
        val patchRequest = call.receive<UserPatchRequest>()

        val user = repository.get(id) ?: return@patch call.respond(
            HttpStatusCode.NotFound,
            ErrorResponse("USER_NOT_FOUND", "User with ID $id not found")
        )

        val updatedName = patchRequest.name ?: user.name
        val updatedUser = User1(id = id, name = updatedName)
        repository.save(updatedUser)

        call.respond(HttpStatusCode.OK, updatedUser)
    }
}


private fun Route.userReadEndpoints(repository: Repository1<User1>) {
    /**
     * Get a list of users.
     *
     * @query page Page number for pagination (defaults to 1)
     * @query pageSize Number of results per page (defaults to 20, max 100)
     * @query nameFilter Filter users by name (optional)
     * @query sortBy Sort field (optional, one of "id", "name")
     * @query sortOrder Sort direction (optional, one of "asc", "desc")
     * @response 200 A list of users with pagination information.
     */
    get {
        val query = call.request.queryParameters.toMap()
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20).coerceAtMost(100)

        val list = repository.list(query)
        val paginatedResponse = PaginatedResponse(
            data = list,
            pagination = PaginationInfo(
                page = page,
                pageSize = pageSize,
                totalItems = list.size,
                totalPages = (list.size + pageSize - 1) / pageSize
            )
        )

        call.respond(paginatedResponse)
    }

    /**
     * Get a single user
     *
     * @path id The ID of the user
     * @response 200 The requested user
     * @response 404 The user was not found
     */
    get("{id}") {
        val user = repository.get(call.parameters["id"]!!)
            ?: return@get call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("USER_NOT_FOUND", "User not found")
            )
        call.respond(user)
    }

    /**
     * Get multiple users by their IDs
     *
     * @query ids Comma-separated list of user IDs
     * @response 200 List of found users
     */
    get("batch") {
        val ids = call.request.queryParameters["ids"]?.split(",") ?: emptyList()

        val users = ids.mapNotNull { id -> repository.get(id) }

        call.respond(users)
    }
}

private fun Route.userAdvancedEndpoints(repository: Repository1<User1>) {
    /**
     * Get user profile with extended information
     *
     * @path id The ID of the user
     * @response 200 The user profile
     * @response 404 User not found
     */
    get("{id}/profile") {
        val id = call.parameters["id"]!!
        val user = repository.get(id) ?: return@get call.respond(
            HttpStatusCode.NotFound,
            ErrorResponse("USER_NOT_FOUND", "User not found")
        )

        val profile = UserProfile(
            id = id,
            name = user.name,
            joinDate = LocalDate.now().minusDays(30).toString(),
            lastActive = LocalDateTime.now().toString(),
            preferences = UserPreferences(
                theme = "dark",
                notifications = true,
                language = "en"
            ),
            metadata = mapOf(
                "browser" to "Chrome",
                "platform" to "Windows",
                "ipAddress" to "192.168.1.1"
            )
        )

        call.respond(profile)
    }

    /**
     * Update user preferences
     *
     * @path id The ID of the user
     * @body [UserPreferences] The preferences to update
     * @response 200 Updated preferences
     * @response 404 User not found
     */
    put("{id}/preferences") {
        val id = call.parameters["id"]!!
        val preferences = call.receive<UserPreferences>()

        val user = repository.get(id) ?: return@put call.respond(
            HttpStatusCode.NotFound,
            ErrorResponse("USER_NOT_FOUND", "User not found")
        )

        call.respond(HttpStatusCode.OK, preferences)
    }
}

private fun Route.messageModificationEndpoints(repository: Repository1<Message1>) {
    /**
     * Save a new message.
     *
     * @body [Message1] The message to save.
     * @response 201 Message created successfully
     * @response 400 Invalid message data
     */
    post {
        repository.save(call.receive())
        call.respond(HttpStatusCode.Created)
    }

    /**
     * Delete a message.
     * @path id The ID of the message
     * @response 204 Message deleted successfully
     * @response 404 Message not found
     */
    delete("{id}") {
        repository.delete(call.parameters["id"]!!)
        call.respond(HttpStatusCode.NoContent)
    }
    /**
     * Update an existing message.
     *
     * @path id The ID of the message
     * @body [MessageUpdateRequest] The message data to update
     * @response 200 Message updated successfully
     * @response 400 Invalid message data
     * @response 404 Message not found
     */
    put("{id}") {
        val id = call.parameters["id"]!!
        val updateRequest = call.receive<MessageUpdateRequest>()

        val message = repository.get(id) ?: return@put call.respond(
            HttpStatusCode.NotFound,
            ErrorResponse("MESSAGE_NOT_FOUND", "Message with ID $id not found")
        )

        val updatedMessage = Message1(id = message.id, text = updateRequest.text)
        repository.save(updatedMessage)

        call.respond(HttpStatusCode.OK, updatedMessage)
    }
}

private fun Route.messageReadEndpoints(repository: Repository1<Message1>) {
    /**
     * Get a list of messages.
     *
     * @query page Page number for pagination (defaults to 1)
     * @query pageSize Number of results per page (defaults to 20, max 100)
     * @query textFilter Filter messages by text content (optional)
     * @query sortBy Sort field (optional, one of "id", "text")
     * @query sortOrder Sort direction (optional, one of "asc", "desc")
     * @response 200 A list of messages with pagination information
     */
    get {
        val query = call.request.queryParameters.toMap()

        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20).coerceAtMost(100)

        val list = repository.list(query)

        val paginatedResponse = PaginatedResponse(
            data = list,
            pagination = PaginationInfo(
                page = page,
                pageSize = pageSize,
                totalItems = list.size,
                totalPages = (list.size + pageSize - 1) / pageSize
            )
        )

        call.respond(paginatedResponse)
    }

    /**
     * Get a single message
     *
     * @path id The ID of the message
     * @response 200 The requested message
     * @response 404 The message was not found
     */
    get("{id}") {
        val message = repository.get(call.parameters["id"]!!)
            ?: return@get call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse("MESSAGE_NOT_FOUND", "Message not found")
            )
        call.respond(message)
    }
}

private fun Route.messageAdvancedEndpoints(repository: Repository1<Message1>) {
    /**
     * Get messages by a specific status
     *
     * @path status The message status (one of: DRAFT, SENT, DELIVERED, READ)
     * @response 200 List of messages with the specified status
     */
    get("by-status/{status}") {
        val statusParam = call.parameters["status"]

        val status = try {
            MessageStatus.valueOf(statusParam?.uppercase() ?: "")
        } catch (e: IllegalArgumentException) {
            return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("INVALID_STATUS", "Invalid message status")
            )
        }

        val messages = repository.list(emptyMap())

        call.respond(messages)
    }

    /**
     * Search messages by content
     *
     * @query q Search query (required)
     * @query type Search type (optional, one of: EXACT, CONTAINS, FUZZY)
     * @response 200 Search results
     * @response 400 Missing or invalid search parameters
     */
    get("search") {
        val query = call.request.queryParameters["q"]

        if (query.isNullOrBlank()) {
            return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("MISSING_QUERY", "Search query is required")
            )
        }

        val searchType = call.request.queryParameters["type"] ?: "CONTAINS"

        val messages = repository.list(emptyMap())
            .filter { it.text.contains(query, ignoreCase = true) }

        call.respond(
            SearchResultT(
                query = query,
                type = searchType,
                results = messages,
                totalResults = messages.size
            )
        )
    }
}

private fun Route.advancedEndpoints() {
    route("/advanced") {
        /**
         * Get system health information
         *
         * @response 200 System health information
         */
        get("health") {
            call.respond(
                SystemHealth(
                    status = "UP",
                    timestamp = LocalDateTime.now().toString(),
                    components = mapOf(
                        "database" to ComponentHealth("UP", null),
                        "storage" to ComponentHealth("UP", null),
                        "cache" to ComponentHealth("UP", null)
                    ),
                    metrics = mapOf(
                        "cpu" to 0.42,
                        "memory" to 0.78,
                        "diskSpace" to 0.56
                    )
                )
            )
        }

        /**
         * Testing different response content types
         *
         * @query format Response format (one of: json, xml, html, text)
         * @response 200 Data in requested format
         */
        get("formats") {
            val format = call.request.queryParameters["format"] ?: "json"

            when (format.lowercase()) {
                "json" -> call.respond(
                    mapOf(
                        "message" to "This is JSON",
                        "format" to "json"
                    )
                )

                "html" -> call.respondText(
                    """<!DOCTYPE html><html><body><h1>This is HTML</h1></body></html>""",
                    ContentType.Text.Html
                )

                "text" -> call.respondText("This is plain text")
                else -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_FORMAT", "Unsupported format: $format")
                )
            }
        }

        /**
         * File upload simulation
         *
         * @response 200 File upload success
         */
        post("upload") {
            call.respond(
                FileUploadResponse(
                    success = true,
                    filename = "example.txt",
                    size = 1024,
                    contentType = "text/plain",
                    uploadId = UUID.randomUUID().toString()
                )
            )
        }

        /**
         * Create a complex nested object
         *
         * @body [ComplexObject] The complex object to create
         * @response 201 Object created successfully
         * @response 400 Invalid object data
         */
        post("complex-objects") {
            val complexObject = call.receive<ComplexObject>()

            val validationErrors = mutableMapOf<String, String>()

            if (complexObject.name.isBlank()) {
                validationErrors["name"] = "Name cannot be blank"
            }

            if (complexObject.nested.isEmpty()) {
                validationErrors["nested"] = "Must have at least one nested object"
            }

            if (validationErrors.isNotEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "Validation failed", validationErrors)
                )
                return@post
            }

            call.respond(HttpStatusCode.Created, complexObject)
        }

        /**
         * Get nested resources with polymorphic response
         *
         * @path type Resource type (one of: A, B, C)
         * @response 200 Resource of the specified type
         * @response 400 Invalid resource type
         */
        get("resources/{type}") {
            val type = call.parameters["type"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("MISSING_TYPE", "Resource type is required")
            )

            val resource = when (type.uppercase()) {
                "A" -> ResourceA("resource-a", "Resource A", 42)
                "B" -> ResourceB("resource-b", "Resource B", listOf("tag1", "tag2"))
                "C" -> ResourceC("resource-c", "Resource C", mapOf("key1" to "value1", "key2" to "value2"))
                else -> return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("INVALID_TYPE", "Invalid resource type: $type")
                )
            }

            call.respond(resource)
        }

        /**
         * Test various parameter types
         *
         * @path id Path parameter
         * @query flag Boolean flag (true or false)
         * @query count Integer count
         * @query tags Array of string tags (comma separated)
         * @response 200 Parameters received
         */
        get("params/{id}") {
            val id = call.parameters["id"]!!
            val flag = call.request.queryParameters["flag"]?.toBoolean() ?: false
            val count = call.request.queryParameters["count"]?.toIntOrNull() ?: 0
            val tags = call.request.queryParameters["tags"]?.split(",") ?: emptyList()

            call.respond(
                mapOf(
                    "id" to id,
                    "flag" to flag,
                    "count" to count,
                    "tags" to tags
                )
            )
        }
    }
}

interface Repository1<E> {
    fun get(id: String): E?
    fun save(entity: E)
    fun delete(id: String)
    fun list(query: Map<String, List<String>>): List<E>
}

@Serializable
data class User1(val id: String, val name: String)

@Serializable
data class Message1(val id: String, val text: String)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
    val timestamp: String = LocalDateTime.now().toString()
)

@Serializable
data class PaginationInfo(
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int
)

@Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    val pagination: PaginationInfo
)

@Serializable
data class UserUpdateRequest(val name: String?)

@Serializable
data class UserPatchRequest(val name: String? = null)

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val joinDate: String,
    val lastActive: String,
    val preferences: UserPreferences,
    val metadata: Map<String, String>
)

@Serializable
data class UserPreferences(
    val theme: String,
    val notifications: Boolean,
    val language: String
)

@Serializable
data class MessageUpdateRequest(val text: String)

@Serializable
enum class MessageStatus {
    DRAFT,
    SENT,
    DELIVERED,
    READ
}

@Serializable
data class SearchResultT<T>(
    val query: String,
    val type: String,
    val results: List<T>,
    val totalResults: Int
)

@Serializable
data class SystemHealth(
    val status: String,
    val timestamp: String,
    val components: Map<String, ComponentHealth>,
    val metrics: Map<String, Double>
)

@Serializable
data class ComponentHealth(
    val status: String,
    val details: String?
)

@Serializable
data class FileUploadResponse(
    val success: Boolean,
    val filename: String,
    val size: Long,
    val contentType: String,
    val uploadId: String
)

@Serializable
data class ComplexObject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val type: String,
    val created: String = LocalDateTime.now().toString(),
    val flags: Map<String, Boolean> = emptyMap(),
    val nested: List<NestedObject>,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class NestedObject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val value: Int,
    val properties: Map<String, String> = emptyMap()
)

@Serializable
sealed class Resource {
    abstract val id: String
    abstract val name: String
}

@Serializable
@SerialName("resource_a")
data class ResourceA(
    override val id: String,
    override val name: String,
    val count: Int
) : Resource()

@Serializable
@SerialName("resource_b")
data class ResourceB(
    override val id: String,
    override val name: String,
    val tags: List<String>
) : Resource()

@Serializable
@SerialName("resource_c")
data class ResourceC(
    override val id: String,
    override val name: String,
    val properties: Map<String, String>
) : Resource()