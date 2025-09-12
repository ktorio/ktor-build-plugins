// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

const val API_ROOT = "/api/v1"
const val API_VERSION = "v1"
const val API_LATEST = "latest"
const val ID_PARAM = "id"
const val UUID_PARAM = "uuid"
const val NAME_PARAM = "name"
const val QUERY_PARAM = "q"
const val SORT_PARAM = "sort"
const val PAGE_PARAM = "page"
const val SIZE_PARAM = "size"
const val FILTER_PARAM = "filter"

object EntityType {
    const val REPORTS = "reports"
    const val USERS = "users"
    const val ITEMS = "items"
    const val ANALYTICS = "analytics"
    const val DOCUMENTS = "documents"
}

const val UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
const val DATE_REGEX = "\\d{4}-\\d{2}-\\d{2}"

fun Application.templateRoutes(
    reportService: ReportService,
    userService: UserService,
    itemService: ItemService,
    analyticsService: AnalyticsService,
    documentService: DocumentService
) {
    install(ContentNegotiation) {
        json()
    }

    routing {
        route(API_ROOT) {
            /**
             * Get all reports.
             * @response 200 [List<Report>] List of reports
             */
            get("/${EntityType.REPORTS}") {
                call.respond(reportService.getAllReports())
            }

            /**
             * Get report by ID.
             * @path id [Long] Report ID
             *   minimum: 1
             *   maximum: 999999999999
             *   required: true
             * @response 200 [Report] Report details
             * @response 404 Report not found
             */
            get("/${EntityType.REPORTS}/{$ID_PARAM}") {
                val id = call.parameters[ID_PARAM]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID format")

                val report = reportService.getReportById(id)
                if (report != null) {
                    call.respond(report)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Report not found")
                }
            }

            /**
             * Get user reports.
             * @path userId [Long] User ID
             * @response 200 [List<Report>] User reports
             */
            get("/${EntityType.USERS}/{userId}/${EntityType.REPORTS}") {
                val userId = call.parameters["userId"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")

                call.respond(reportService.getReportsByUserId(userId))
            }

            /**
             * Get filtered user reports for a specific date.
             * @path userId [Long] User ID
             * @path date [String] Report date (YYYY-MM-DD)
             *   pattern: $DATE_REGEX
             * @query type [String] Report type
             * @response 200 [List<Report>] Filtered user reports
             */
            get("/${EntityType.USERS}/{userId}/${EntityType.REPORTS}/{date}") {
                val userId = call.parameters["userId"]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")

                val date = call.parameters["date"]
                if (date == null || !date.matches(Regex(DATE_REGEX))) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid date format, use YYYY-MM-DD")
                }

                val type = call.request.queryParameters["type"]
                call.respond(reportService.getFilteredReportsByUserIdAndDate(userId, date, type))
            }
        }

        /**
         * Get API information for specific version.
         * @path version [String] API version
         *   enum: [v1, v2, latest]
         * @response 200 [ApiInfo] API information
         */
        get("/api/{version}/info") {
            val version = call.parameters["version"] ?: API_LATEST
            call.respond(ApiInfo(version = version, status = "active"))
        }

        /**
         * Get item by UUID.
         * @path uuid [String] Item UUID
         *   pattern: $UUID_REGEX
         * @response 200 [Item] Item details
         * @response 404 Item not found
         */
        get("/items/{$UUID_PARAM}") {
            val uuid = call.parameters[UUID_PARAM]
            if (uuid == null || !uuid.matches(Regex(UUID_REGEX))) {
                return@get call.respond(HttpStatusCode.BadRequest, "Invalid UUID format")
            }

            val item = itemService.getItemByUuid(uuid)
            if (item != null) {
                call.respond(item)
            } else {
                call.respond(HttpStatusCode.NotFound, "Item not found")
            }
        }

        /**
         * Get analytics data.
         * @path type [String] Analytics type
         *   enum: [daily, weekly, monthly, yearly]
         * @path metric [String] Metric name
         * @response 200 [AnalyticsData] Analytics data
         */
        get("/analytics/{type}/{metric}") {
            val type = call.parameters["type"] ?: "daily"
            val metric = call.parameters["metric"] ?: "views"

            call.respond(analyticsService.getAnalyticsData(type, metric))
        }

        route("/documents") {
            /**
             * Search documents.
             * @query q [String] Search query
             * @query type [String] Document type
             * @response 200 [List<Document>] Search results
             */
            get {
                val query = call.request.queryParameters[QUERY_PARAM] ?: ""
                val type = call.request.queryParameters["type"]

                call.respond(documentService.searchDocuments(query, type))
            }

            /**
             * Get document by ID.
             * @path id [String] Document ID
             * @response 200 [Document] Document details
             * @response 404 Document not found
             */
            get("/{$ID_PARAM}") {
                val id = call.parameters[ID_PARAM] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")

                val document = documentService.getDocumentById(id)
                if (document != null) {
                    call.respond(document)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Document not found")
                }
            }

            /**
             * Get document content.
             * @path id [String] Document ID
             * @path format [String] Content format
             *   enum: [html, text, pdf]
             * @response 200 Document content
             * @response 404 Document not found
             */
            get("/{$ID_PARAM}/content/{format}") {
                val id = call.parameters[ID_PARAM] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing ID")
                val format = call.parameters["format"] ?: "text"

                val contentType = when (format) {
                    "html" -> ContentType.Text.Html
                    "pdf" -> ContentType.Application.Pdf
                    else -> ContentType.Text.Plain
                }

                val content = documentService.getDocumentContent(id, format)
                if (content != null) {
                    call.respondText(content, contentType)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Document not found")
                }
            }
        }

        route("/${EntityType.USERS}") {
            /**
             * Get all users.
             * @query sort [String] Sort field
             *   enum: [name, created, role]
             * @query order [String] Sort order
             *   enum: [asc, desc]
             * @response 200 [List<UserProfileTemplate>] List of users
             */
            get {
                val sort = call.request.queryParameters[SORT_PARAM] ?: "name"
                val order = call.request.queryParameters["order"] ?: "asc"

                call.respond(userService.getAllUsers(sort, order))
            }

            /**
             * Get user by ID.
             * @path id [Long] User ID
             * @response 200 [UserProfileTemplate] User details
             * @response 404 User not found
             */
            get("/{$ID_PARAM}") {
                val id = call.parameters[ID_PARAM]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID format")

                val user = userService.getUserById(id)
                if (user != null) {
                    call.respond(user)
                } else {
                    call.respond(HttpStatusCode.NotFound, "User not found")
                }
            }

            /**
             * Get user items.
             * @path id [Long] User ID
             * @query filter [String] Item filter
             * @response 200 [List<Item>] User items
             */
            get("/{$ID_PARAM}/${EntityType.ITEMS}") {
                val id = call.parameters[ID_PARAM]?.toLongOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID format")

                val filter = call.request.queryParameters[FILTER_PARAM]
                call.respond(itemService.getItemsByUserId(id, filter))
            }
        }

        /**
         * Get custom report.
         * @path year [Int] Report year
         *   minimum: 2000
         *   maximum: 2030
         * @path month [Int] Report month
         *   minimum: 1
         *   maximum: 12
         * @path day [Int] Report day
         *   minimum: 1
         *   maximum: 31
         * @path format [String] Report format
         *   enum: [json, csv, xml]
         * @response 200 Custom report
         */
        get("/reports/{year}/{month}/{day}.{format}") {
            val year = call.parameters["year"]?.toIntOrNull() ?: 2023
            val month = call.parameters["month"]?.toIntOrNull() ?: 1
            val day = call.parameters["day"]?.toIntOrNull() ?: 1
            val format = call.parameters["format"] ?: "json"

            val contentType = when (format) {
                "csv" -> ContentType.Text.CSV
                "xml" -> ContentType.Application.Xml
                else -> ContentType.Application.Json
            }

            val report = reportService.getCustomReport(year, month, day, format)
            call.respondText(report, contentType)
        }
    }
}

interface ReportService {
    fun getAllReports(): List<Report>
    fun getReportById(id: Long): Report?
    fun getReportsByUserId(userId: Long): List<Report>
    fun getFilteredReportsByUserIdAndDate(userId: Long, date: String, type: String?): List<Report>
    fun getCustomReport(year: Int, month: Int, day: Int, format: String): String
}

interface UserService {
    fun getAllUsers(sort: String, order: String): List<UserProfileTemplate>
    fun getUserById(id: Long): UserProfileTemplate?
}

interface ItemService {
    fun getItemByUuid(uuid: String): Item?
    fun getItemsByUserId(userId: Long, filter: String?): List<Item>
}

interface AnalyticsService {
    fun getAnalyticsData(type: String, metric: String): AnalyticsData
}

interface DocumentService {
    fun searchDocuments(query: String, type: String?): List<Document>
    fun getDocumentById(id: String): Document?
    fun getDocumentContent(id: String, format: String): String?
}

@Serializable
data class Report(
    val id: Long,
    val title: String,
    val type: String,
    val date: String,
    val data: Map<String, Double>
)

@Serializable
data class UserProfileTemplate(
    val id: Long,
    val username: String,
    val email: String,
    val role: String
)

@Serializable
data class Item(
    val uuid: String,
    val name: String,
    val description: String,
    val price: Double
)

@Serializable
data class AnalyticsData(
    val type: String,
    val metric: String,
    val values: List<DataPoint>
)

@Serializable
data class DataPoint(
    val label: String,
    val value: Double
)

@Serializable
data class Document(
    val id: String,
    val title: String,
    val type: String,
    val createdAt: String
)

@Serializable
data class ApiInfo(
    val version: String,
    val status: String
)