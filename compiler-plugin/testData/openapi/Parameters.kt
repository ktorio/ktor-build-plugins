// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

fun Application.parameters() {
    routing {
        /**
         * Basic parameter demonstration
         *
         * @path a [String] First path parameter
         * @path b [String] Second path parameter
         * @path c [String] Third path parameter
         * @query d [String] First query parameter
         * @query e [String] Second query parameter
         * @header f [String] First header parameter
         * @header g [String]+ Multiple header parameters with same name
         * @header h [String] Another header parameter
         * @response 200 All parameters as string
         */
        get("/parameters/{a}/{b}/{c}") {
            call.respondText(
                listOf(
                    call.parameters["a"],
                    call.pathParameters["b"],
                    call.request.pathVariables["c"],
                    call.queryParameters["d"],
                    call.request.queryParameters["e"],
                    call.request.headers["f"],
                    call.request.headers.getAll("g"),
                    call.request.header("h")
                ).joinToString()
            )
        }

        route("/typed-parameters") {
            /**
             * Integer parameters
             *
             * @path id [Int] Path ID
             *   minimum: 1
             *   maximum: 1000
             * @query limit [Int] Max number of items
             *   minimum: 1
             *   maximum: 100
             *   default: 20
             * @query page [Int] Page number
             *   minimum: 1
             *   default: 1
             * @response 200 [TypedResponse] Response with parsed values
             * @response 400 Invalid parameters
             */
            get("/int/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")

                if (id < 1 || id > 1000) {
                    return@get call.respond(HttpStatusCode.BadRequest, "ID must be between 1 and 1000")
                }

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                if (limit < 1 || limit > 100) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Limit must be between 1 and 100")
                }

                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                if (page < 1) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Page must be at least 1")
                }

                call.respond(
                    TypedResponse(
                        id = id,
                        limit = limit,
                        page = page,
                        type = "integer"
                    )
                )
            }

            /**
             * Float parameters
             *
             * @path value [Double] Floating point value
             *   minimum: 0.0
             *   maximum: 100.0
             * @query factor [Float] Multiplication factor
             *   minimum: 0.1
             *   maximum: 10.0
             *   default: 1.0
             * @response 200 [TypedResponse] Response with parsed values
             * @response 400 Invalid parameters
             */
            get("/float/{value}") {
                val value = call.parameters["value"]?.toDoubleOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid value")

                if (value < 0.0 || value > 100.0) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Value must be between 0.0 and 100.0")
                }

                val factor = call.request.queryParameters["factor"]?.toFloatOrNull() ?: 1.0f
                if (factor < 0.1f || factor > 10.0f) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Factor must be between 0.1 and 10.0")
                }

                call.respond(
                    TypedResponse(
                        doubleValue = value,
                        floatValue = factor,
                        type = "float"
                    )
                )
            }

            /**
             * Boolean parameters
             *
             * @query enabled [Boolean] Feature flag
             *   default: false
             * @query verbose [Boolean] Verbose output
             *   default: false
             * @header debug [Boolean] Debug mode
             *   default: false
             * @response 200 [TypedResponse] Response with parsed values
             */
            get("/boolean") {
                val enabled = call.request.queryParameters["enabled"]?.toBoolean() ?: false
                val verbose = call.request.queryParameters["verbose"]?.toBoolean() ?: false
                val debug = call.request.header("debug")?.toBoolean() ?: false

                call.respond(
                    TypedResponse(
                        enabled = enabled,
                        verbose = verbose,
                        debug = debug,
                        type = "boolean"
                    )
                )
            }

            /**
             * String parameters with pattern constraints
             *
             * @path code [String] Product code
             *   pattern: ^[A-Z]{2}\d{4}$
             * @query email [String] User email
             *   pattern: ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$
             * @query phone [String] Phone number
             *   pattern: ^\+\d{1,3}-\d{3,14}$
             * @response 200 [TypedResponse] Response with parsed values
             * @response 400 Invalid parameters
             */
            get("/pattern/{code}") {
                val code = call.parameters["code"]
                if (code == null || !Regex("^[A-Z]{2}\\d{4}$").matches(code)) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid product code format")
                }

                val email = call.request.queryParameters["email"]
                if (email != null && !Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$").matches(email)) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid email format")
                }

                val phone = call.request.queryParameters["phone"]
                if (phone != null && !Regex("^\\+\\d{1,3}-\\d{3,14}$").matches(phone)) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid phone number format")
                }

                call.respond(
                    TypedResponse(
                        code = code,
                        email = email,
                        phone = phone,
                        type = "pattern"
                    )
                )
            }

            /**
             * Enum parameters
             *
             * @path status [String] Order status
             *   enum: [PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED]
             * @query sort [String] Sort direction
             *   enum: [asc, desc]
             *   default: asc
             * @query filter [String] Result filter
             *   enum: [all, active, inactive]
             *   default: all
             * @response 200 [TypedResponse] Response with parsed values
             * @response 400 Invalid parameters
             */
            get("/enum/{status}") {
                val validStatuses = setOf("PENDING", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED")
                val status = call.parameters["status"]
                if (status == null || status !in validStatuses) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid status value")
                }

                val validSortDirections = setOf("asc", "desc")
                val sort = call.request.queryParameters["sort"] ?: "asc"
                if (sort !in validSortDirections) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid sort direction")
                }

                val validFilters = setOf("all", "active", "inactive")
                val filter = call.request.queryParameters["filter"] ?: "all"
                if (filter !in validFilters) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid filter value")
                }

                call.respond(
                    TypedResponse(
                        status = status,
                        sort = sort,
                        filter = filter,
                        type = "enum"
                    )
                )
            }

            /**
             * Array parameters
             *
             * @query ids [Int]+ List of IDs
             * @query tags [String]+ List of tags
             * @query flags [Boolean]+ List of flags
             * @response 200 [TypedResponse] Response with parsed arrays
             */
            get("/array") {
                val ids = call.request.queryParameters.getAll("ids")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                val tags = call.request.queryParameters.getAll("tags") ?: emptyList()
                val flags = call.request.queryParameters.getAll("flags")?.map { it.toBoolean() } ?: emptyList()

                call.respond(
                    TypedResponse(
                        ids = ids,
                        tags = tags,
                        flags = flags,
                        type = "array"
                    )
                )
            }

            /**
             * Date and time parameters
             *
             * @query date [String] Date in ISO format
             *   pattern: ^\d{4}-\d{2}-\d{2}$
             * @query time [String] Time in ISO format
             *   pattern: ^\d{2}:\d{2}(:\d{2})?$
             * @query datetime [String] DateTime in ISO format
             *   pattern: ^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{3})?(Z|[+-]\d{2}:\d{2})?$
             * @response 200 [TypedResponse] Response with parsed date/time values
             * @response 400 Invalid parameters
             */
            get("/datetime") {
                val date = call.request.queryParameters["date"]
                if (date != null && !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid date format")
                }

                val time = call.request.queryParameters["time"]
                if (time != null && !Regex("^\\d{2}:\\d{2}(:\\d{2})?$").matches(time)) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid time format")
                }

                val datetime = call.request.queryParameters["datetime"]
                if (datetime != null && !Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?(Z|[+-]\\d{2}:\\d{2})?$").matches(
                        datetime
                    )
                ) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid datetime format")
                }

                call.respond(
                    TypedResponse(
                        date = date,
                        time = time,
                        datetime = datetime,
                        type = "datetime"
                    )
                )
            }
        }

        /**
         * Multiple parameters with the same name
         *
         * @query filter [String]+ Multiple filter parameters
         * @header x-custom [String]+ Multiple custom headers
         * @response 200 [MultiValueResponse] Response with all parameter values
         */
        get("/multi-value") {
            val filters = call.request.queryParameters.getAll("filter") ?: emptyList()
            val customHeaders = call.request.headers.getAll("x-custom") ?: emptyList()

            call.respond(
                MultiValueResponse(
                    filters = filters,
                    customHeaders = customHeaders
                )
            )
        }

        /**
         * Required vs Optional parameters
         *
         * @path id [Int] Required path parameter
         * @query required [String] Required query parameter
         * @query optional [String] Optional query parameter
         * @header x-required [String] Required header
         * @header x-optional [String] Optional header
         * @response 200 [RequiredOptionalResponse] Response with all parameters
         * @response 400 Missing required parameter
         */
        get("/required-optional/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing required path parameter: id")

            val required = call.request.queryParameters["required"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing required query parameter: required")

            val optional = call.request.queryParameters["optional"]

            val requiredHeader = call.request.header("x-required")
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing required header: x-required")

            val optionalHeader = call.request.header("x-optional")

            call.respond(
                RequiredOptionalResponse(
                    id = id,
                    required = required,
                    optional = optional,
                    requiredHeader = requiredHeader,
                    optionalHeader = optionalHeader
                )
            )
        }

        /**
         * Parameters with default values
         *
         * @query page [Int] Page number
         *   default: 1
         * @query size [Int] Page size
         *   default: 20
         * @query sort [String] Sort field
         *   default: id
         * @query order [String] Sort order
         *   enum: [asc, desc]
         *   default: asc
         * @query filter [String] Filter
         *   default: all
         * @response 200 [DefaultValuesResponse] Response with default or provided values
         */
        get("/defaults") {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
            val sort = call.request.queryParameters["sort"] ?: "id"
            val order = call.request.queryParameters["order"] ?: "asc"
            val filter = call.request.queryParameters["filter"] ?: "all"

            call.respond(
                DefaultValuesResponse(
                    page = page,
                    size = size,
                    sort = sort,
                    order = order,
                    filter = filter
                )
            )
        }

        /**
         * Combined parameter sources
         *
         * @path id [String] Resource ID
         * @query fields [String]+ Fields to include
         * @query expand [String]+ Related resources to expand
         * @header if-modified-since [String] Only return if modified since this date
         * @header x-api-key [String] API key for authentication
         * @response 200 [CombinedParametersResponse] Response with all parameters
         * @response 400 Invalid parameters
         * @response 401 Missing or invalid API key
         */
        get("/combined/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing path parameter: id")

            val apiKey = call.request.header("x-api-key")
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Missing API key")

            if (apiKey != "valid-key") {
                return@get call.respond(HttpStatusCode.Unauthorized, "Invalid API key")
            }

            val fields = call.request.queryParameters.getAll("fields") ?: emptyList()
            val expand = call.request.queryParameters.getAll("expand") ?: emptyList()
            val ifModifiedSince = call.request.header("if-modified-since")

            call.respond(
                CombinedParametersResponse(
                    id = id,
                    fields = fields,
                    expand = expand,
                    ifModifiedSince = ifModifiedSince,
                    apiKey = apiKey
                )
            )
        }

        /**
         * Parameter encoding styles
         *
         * @query simple [String] Simple style parameter
         * @query array [String]+ Array of values
         * @query object [String] Object parameter (serialized as JSON)
         * @response 200 [EncodingStylesResponse] Response with parsed parameters
         */
        get("/encoding-styles") {
            val simple = call.request.queryParameters["simple"]
            val array = call.request.queryParameters.getAll("array") ?: emptyList()

            val objectParam = call.request.queryParameters["object"]
            val parsedObject = try {
                objectParam?.let { Json.decodeFromString<Map<String, String>>(it) }
            } catch (e: Exception) {
                null
            }

            call.respond(
                EncodingStylesResponse(
                    simple = simple,
                    array = array,
                    objectParam = parsedObject
                )
            )
        }

        /**
         * Header parameters with special handling
         *
         * @header user-agent [String] Client user agent
         * @header accept [String] Accepted content types
         * @header accept-language [String] Accepted languages
         * @header content-type [String] Content type of the request
         * @header authorization [String] Authorization header
         * @response 200 [HeaderParametersResponse] Response with parsed headers
         */
        get("/headers") {
            val userAgent = call.request.header("user-agent")
            val accept = call.request.header("accept")
            val acceptLanguage = call.request.header("accept-language")
            val contentType = call.request.header("content-type")
            val authorization = call.request.header("authorization")

            call.respond(
                HeaderParametersResponse(
                    userAgent = userAgent,
                    accept = accept,
                    acceptLanguage = acceptLanguage,
                    contentType = contentType,
                    authorization = authorization
                )
            )
        }

        /**
         * Path parameter with regex pattern
         *
         * @path uuid [String] UUID parameter
         *   pattern: ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$
         * @response 200 UUID is valid
         * @response 400 Invalid UUID format
         */
        get("/uuid/{uuid}") {
            val uuid = call.parameters["uuid"]
            val uuidPattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

            if (uuid == null || !uuidPattern.matches(uuid.lowercase())) {
                return@get call.respond(HttpStatusCode.BadRequest, "Invalid UUID format")
            }

            call.respondText("UUID is valid: $uuid")
        }

        /**
         * Cookie parameters
         *
         * @cookie session-id [String] Session identifier
         * @cookie preferences [String] User preferences
         * @response 200 [CookieParametersResponse] Response with parsed cookies
         */
        get("/cookies") {
            val sessionId = call.request.cookies["session-id"]
            val preferences = call.request.cookies["preferences"]

            call.respond(
                CookieParametersResponse(
                    sessionId = sessionId,
                    preferences = preferences
                )
            )
        }
    }
}

@Serializable
data class TypedResponse(
    val id: Int = 0,
    val limit: Int = 0,
    val page: Int = 0,
    val doubleValue: Double = 0.0,
    val floatValue: Float = 0.0f,
    val enabled: Boolean = false,
    val verbose: Boolean = false,
    val debug: Boolean = false,
    val code: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val status: String? = null,
    val sort: String? = null,
    val filter: String? = null,
    val ids: List<Int> = emptyList(),
    val tags: List<String> = emptyList(),
    val flags: List<Boolean> = emptyList(),
    val date: String? = null,
    val time: String? = null,
    val datetime: String? = null,
    val type: String
)

@Serializable
data class MultiValueResponse(
    val filters: List<String>,
    val customHeaders: List<String>
)

@Serializable
data class RequiredOptionalResponse(
    val id: Int,
    val required: String,
    val optional: String?,
    val requiredHeader: String,
    val optionalHeader: String?
)

@Serializable
data class DefaultValuesResponse(
    val page: Int,
    val size: Int,
    val sort: String,
    val order: String,
    val filter: String
)

@Serializable
data class CombinedParametersResponse(
    val id: String,
    val fields: List<String>,
    val expand: List<String>,
    val ifModifiedSince: String?,
    val apiKey: String
)

@Serializable
data class EncodingStylesResponse(
    val simple: String?,
    val array: List<String>,
    val objectParam: Map<String, String>?
)

@Serializable
data class HeaderParametersResponse(
    val userAgent: String?,
    val accept: String?,
    val acceptLanguage: String?,
    val contentType: String?,
    val authorization: String?
)

@Serializable
data class CookieParametersResponse(
    val sessionId: String?,
    val preferences: String?
)