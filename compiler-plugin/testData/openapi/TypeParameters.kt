// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * Application extension with generics for testing various type parameter scenarios.
 * Uses repositories with different entity types and advanced generic operations.
 */
fun Application.typeParameterTests(
    userRepository: GenericRepository<UserEntity, UserSearchCriteria>,
    taskRepository: GenericRepository<TaskEntity, TaskSearchCriteria>,
    documentRepository: GenericRepository<DocumentEntity, DocumentSearchCriteria>,
    notificationService: NotificationService<NotificationContext>,
    validationService: ValidationService<ValidationResult>,
    cacheProvider: CacheProvider<String, SerializableValue>
) {
    install(ContentNegotiation) {
        json()
    }

    routing {
        route("/api/v1") {
            typedCrudEndpoints<UserEntity, UserSearchCriteria>("users", userRepository)

            typedCrudEndpoints<TaskEntity, TaskSearchCriteria>("tasks", taskRepository)

            typedCrudEndpoints<DocumentEntity, DocumentSearchCriteria>("documents", documentRepository)

            route("/notifications") {
                /**
                 * Send notification with generic context
                 * @request [NotificationRequest] Notification request
                 * @response 200 [NotificationResult] Notification result
                 * @response 400 Invalid request
                 */
                post {
                    val request = call.receive<NotificationRequest>()
                    val context = NotificationContext(request.channel, request.priority)
                    val result = notificationService.send(request.recipient, request.message, context)
                    call.respond(result)
                }
            }

            route("/validation") {
                /**
                 * Validate data with generic result
                 * @request [ValidationRequest] Validation request
                 * @response 200 [ValidationResult] Validation result
                 */
                post {
                    val request = call.receive<ValidationRequest>()
                    val result = validationService.validate(request.data, request.rules)
                    call.respond(result)
                }
            }

            route("/cache") {
                /**
                 * Get cached value
                 * @path key [String] Cache key
                 * @response 200 Cached value
                 * @response 404 Value not found
                 */
                get("{key}") {
                    val key = call.parameters["key"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val value = cacheProvider.get(key)
                    if (value != null) {
                        call.respond(value)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                /**
                 * Put value in cache
                 * @path key [String] Cache key
                 * @request [CacheRequest] Cache value request
                 * @response 204 Value cached
                 */
                put("{key}") {
                    val key = call.parameters["key"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val request = call.receive<CacheRequest>()
                    cacheProvider.put(key, request.value)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        route("/api/v2") {
            route("/users") {
                userSpecificOperations(userRepository)
            }

            route("/tasks") {
                taskSpecificOperations(taskRepository)
            }

            route("/documents") {
                documentSpecificOperations(documentRepository)
            }
        }

        route("/api/v3") {
            /**
             * Get combined data with multiple generic repositories
             * @query type [String] Entity type (users, tasks, documents)
             * @response 200 [CombinedResult] Combined data
             */
            get("/combined") {
                val type = call.request.queryParameters["type"]
                val result = when (type) {
                    "users" -> CombinedResult(
                        entities = userRepository.findAll().map { SerializableEntity(it) },
                        count = userRepository.count()
                    )

                    "tasks" -> CombinedResult(
                        entities = taskRepository.findAll().map { SerializableEntity(it) },
                        count = taskRepository.count()
                    )

                    "documents" -> CombinedResult(
                        entities = documentRepository.findAll().map { SerializableEntity(it) },
                        count = documentRepository.count()
                    )

                    else -> CombinedResult(entities = emptyList(), count = 0)
                }
                call.respond(result)
            }
        }

        apiVersionsWithUserAndTask(userRepository, taskRepository)
    }
}

/**
 * Type-aware CRUD endpoint generator for entities with search criteria
 */
private inline fun <reified E : Any, reified C : SearchCriteria> Route.typedCrudEndpoints(
    path: String,
    repository: GenericRepository<E, C>
) {
    route(path) {
        /**
         * Get all entities with optional filtering
         * @query page [Int] Page number
         *   minimum: 1
         * @query size [Int] Page size
         *   minimum: 1
         *   maximum: 100
         * @response 200 [PagedResult<E>] List of entities
         */
        get {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
            val criteria = call.request.queryParameters.createCriteria<C>()
            val result = repository.findPaged(criteria, page, size)
            call.respond(result)
        }

        /**
         * Get entity by ID
         * @path id [String] Entity ID
         * @response 200 [E] Entity
         * @response 404 Entity not found
         */
        get("{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val entity = repository.findById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(entity)
        }

        /**
         * Create new entity
         * @request [E] Entity to create
         * @response 201 [E] Created entity
         * @response 400 Invalid entity
         */
        post {
            val entity = call.receive<E>()
            val result = repository.create(entity)
            call.respond(HttpStatusCode.Created, result)
        }

        /**
         * Update entity
         * @path id [String] Entity ID
         * @request [E] Updated entity
         * @response 200 [E] Updated entity
         * @response 404 Entity not found
         */
        put("{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val entity = call.receive<E>()
            val updated = repository.update(id, entity) ?: return@put call.respond(HttpStatusCode.NotFound)
            call.respond(updated)
        }

        /**
         * Delete entity
         * @path id [String] Entity ID
         * @response 204 Entity deleted
         * @response 404 Entity not found
         */
        delete("{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val deleted = repository.delete(id)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        /**
         * Count entities
         * @response 200 [CountResult] Count of entities
         */
        get("count") {
            val count = repository.count()
            call.respond(CountResult(count))
        }

        /**
         * Export entities
         * @query format [String] Export format
         *   enum: [json, csv, xml]
         * @response 200 Exported entities
         */
        get("export") {
            val format = call.request.queryParameters["format"] ?: "json"
            val result = repository.export(format)
            val contentType = when (format) {
                "csv" -> ContentType.Text.CSV
                "xml" -> ContentType.Application.Xml
                else -> ContentType.Application.Json
            }
            call.respondText(result, contentType)
        }
    }
}

/**
 * User-specific operations with generic repository
 */
private fun Route.userSpecificOperations(repository: GenericRepository<UserEntity, UserSearchCriteria>) {
    /**
     * Get user profile
     * @path id [String] User ID
     * @response 200 [UserProfileResult] User profile
     * @response 404 User not found
     */
    get("{id}/profile") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val user = repository.findById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
        val profile = UserProfileResult(user.id, user.name, user.email, "active")
        call.respond(profile)
    }

    /**
     * Search users by criteria
     * @request [UserSearchRequest] Search criteria
     * @response 200 [List<UserEntity>] Matching users
     */
    post("search") {
        val request = call.receive<UserSearchRequest>()
        val criteria = UserSearchCriteria(request.name, request.email, request.status)
        val users = repository.find(criteria)
        call.respond(users)
    }
}

/**
 * Task-specific operations with generic repository
 */
private fun Route.taskSpecificOperations(repository: GenericRepository<TaskEntity, TaskSearchCriteria>) {
    /**
     * Complete task
     * @path id [String] Task ID
     * @response 200 [TaskEntity] Updated task
     * @response 404 Task not found
     */
    post("{id}/complete") {
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val task = repository.findById(id) ?: return@post call.respond(HttpStatusCode.NotFound)
        val updated = task.copy(completed = true)
        val result = repository.update(id, updated)
        call.respond(result!!)
    }

    /**
     * Get tasks by status
     * @query status [String] Task status
     *   enum: [pending, completed, cancelled]
     * @response 200 [List<TaskEntity>] Tasks
     */
    get("by-status") {
        val status = call.request.queryParameters["status"]
        val criteria = TaskSearchCriteria(status = status)
        val tasks = repository.find(criteria)
        call.respond(tasks)
    }
}

/**
 * Document-specific operations with generic repository
 */
private fun Route.documentSpecificOperations(repository: GenericRepository<DocumentEntity, DocumentSearchCriteria>) {
    /**
     * Publish document
     * @path id [String] Document ID
     * @response 200 [DocumentEntity] Updated document
     * @response 404 Document not found
     */
    post("{id}/publish") {
        val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val document = repository.findById(id) ?: return@post call.respond(HttpStatusCode.NotFound)
        val updated = document.copy(status = "published")
        val result = repository.update(id, updated)
        call.respond(result!!)
    }

    /**
     * Get document content
     * @path id [String] Document ID
     * @response 200 Document content
     * @response 404 Document not found
     */
    get("{id}/content") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val document = repository.findById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondText(document.content)
    }
}

/**
 * API version parameterization with generics - fixed with explicit type parameters
 */
private fun Route.apiVersionsWithUserAndTask(
    userRepository: GenericRepository<UserEntity, UserSearchCriteria>,
    taskRepository: GenericRepository<TaskEntity, TaskSearchCriteria>
) {
    for (version in listOf("v1", "v2", "v3")) {
        route("/api-$version") {
            /**
             * Get API statistics
             * @response 200 [ApiStats] API statistics
             */
            get("stats") {
                val userCount = userRepository.count()
                val taskCount = taskRepository.count()
                call.respond(ApiStats(version, userCount, taskCount))
            }
        }
    }
}

/**
 * Helper to create search criteria from query parameters
 */
private inline fun <reified C : SearchCriteria> Parameters.createCriteria(): C {
    return when (C::class) {
        UserSearchCriteria::class -> UserSearchCriteria(
            name = this["name"],
            email = this["email"],
            status = this["status"]
        ) as C

        TaskSearchCriteria::class -> TaskSearchCriteria(
            title = this["title"],
            assignee = this["assignee"],
            status = this["status"]
        ) as C

        DocumentSearchCriteria::class -> DocumentSearchCriteria(
            title = this["title"],
            type = this["type"],
            status = this["status"]
        ) as C

        else -> throw IllegalArgumentException("Unsupported criteria type: ${C::class}")
    }
}

/**
 * Base interface for search criteria
 */
interface SearchCriteria

/**
 * Generic repository interface with typed operations
 */
interface GenericRepository<E, in C : SearchCriteria> {
    fun findById(id: String): E?
    fun findAll(): List<E>
    fun find(criteria: C): List<E>
    fun findPaged(criteria: C, page: Int, size: Int): PagedResult<E>
    fun create(entity: E): E
    fun update(id: String, entity: E): E?
    fun delete(id: String): Boolean
    fun count(): Int
    fun export(format: String): String
}

/**
 * Generic notification service
 */
interface NotificationService<in C> {
    fun send(recipient: String, message: String, context: C): NotificationResult
}

/**
 * Generic validation service
 */
interface ValidationService<R> {
    fun validate(data: Map<String, SerializableValue>, rules: List<String>): R
}

/**
 * Generic cache provider
 */
interface CacheProvider<K, V> {
    fun get(key: K): V?
    fun put(key: K, value: V)
    fun remove(key: K): Boolean
}

@Serializable
data class UserEntity(
    val id: String,
    val name: String,
    val email: String,
    val role: String
)

@Serializable
data class TaskEntity(
    val id: String,
    val title: String,
    val description: String,
    val assignee: String,
    val completed: Boolean
)

@Serializable
data class DocumentEntity(
    val id: String,
    val title: String,
    val content: String,
    val type: String,
    val status: String
)

@Serializable
data class UserSearchCriteria(
    val name: String? = null,
    val email: String? = null,
    val status: String? = null
) : SearchCriteria

@Serializable
data class TaskSearchCriteria(
    val title: String? = null,
    val assignee: String? = null,
    val status: String? = null
) : SearchCriteria

@Serializable
data class DocumentSearchCriteria(
    val title: String? = null,
    val type: String? = null,
    val status: String? = null
) : SearchCriteria

@Serializable
data class NotificationContext(
    val channel: String,
    val priority: Int
)

@Serializable
data class NotificationRequest(
    val recipient: String,
    val message: String,
    val channel: String,
    val priority: Int
)

@Serializable
data class NotificationResult(
    val id: String,
    val status: String,
    val timestamp: Long
)

/**
 * Serializable value type for Any replacements
 */
@Serializable
data class SerializableValue(
    val stringValue: String? = null,
    val intValue: Int? = null,
    val doubleValue: Double? = null,
    val boolValue: Boolean? = null,
    val arrayValue: List<SerializableValue>? = null,
    val mapValue: Map<String, SerializableValue>? = null
)

@Serializable
data class ValidationRequest(
    val data: Map<String, SerializableValue>,
    val rules: List<String>
)

@Serializable
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

@Serializable
data class CacheRequest(
    val value: SerializableValue
)

@Serializable
data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int
)

@Serializable
data class CountResult(
    val count: Int
)

@Serializable
data class UserProfileResult(
    val id: String,
    val name: String,
    val email: String,
    val status: String
)

@Serializable
data class UserSearchRequest(
    val name: String? = null,
    val email: String? = null,
    val status: String? = null
)

/**
 * Wrapper for any entity to make it serializable
 */
@Serializable
data class SerializableEntity(
    val type: String,
    val id: String,
    val properties: Map<String, String>
) {
    constructor(entity: Any) : this(
        type = entity.javaClass.simpleName,
        id = when (entity) {
            is UserEntity -> entity.id
            is TaskEntity -> entity.id
            is DocumentEntity -> entity.id
            else -> "unknown"
        },
        properties = when (entity) {
            is UserEntity -> mapOf(
                "name" to entity.name,
                "email" to entity.email,
                "role" to entity.role
            )

            is TaskEntity -> mapOf(
                "title" to entity.title,
                "assignee" to entity.assignee,
                "completed" to entity.completed.toString()
            )

            is DocumentEntity -> mapOf(
                "title" to entity.title,
                "type" to entity.type,
                "status" to entity.status
            )

            else -> emptyMap()
        }
    )
}

@Serializable
data class CombinedResult(
    val entities: List<SerializableEntity>,
    val count: Int
)

@Serializable
data class ApiStats(
    val version: String,
    val userCount: Int,
    val taskCount: Int
)