// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * This example demonstrates how to create various REST API endpoints
 * that can be processed by the OpenAPI generator.
 */
fun Application.resources() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        route("/articles") {
            /**
             * Get all articles
             * @query sort [String] Sort field (title, date, views)
             *   enum: [title, date, views]
             *   default: date
             * @query direction [String] Sort direction
             *   enum: [asc, desc]
             *   default: desc
             * @query limit [Int] Number of articles to return
             *   minimum: 1
             *   maximum: 100
             *   default: 20
             * @response 200 [ArticleListResponse] List of articles
             */
            get {
                val sort = call.request.queryParameters["sort"] ?: "date"
                val direction = call.request.queryParameters["direction"] ?: "desc"
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20

                call.respond(
                    ArticleListResponse(
                        articles = articles.take(limit),
                        totalCount = articles.size,
                        page = 1,
                        pageSize = limit
                    )
                )
            }

            /**
             * Get a specific article by ID
             * @path id [Int] The article ID
             * @response 200 [Article] The requested article
             * @response 404 Article not found
             */
            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid article ID")

                val foundArticle = articles.find { it.id == id }
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Article #$id not found")

                call.respond(foundArticle)
            }

            /**
             * Get comments for an article
             * @path id [Int] The article ID
             * @query page [Int] Page number
             *   minimum: 1
             *   default: 1
             * @query size [Int] Page size
             *   minimum: 1
             *   maximum: 50
             *   default: 10
             * @response 200 [CommentListResponse] List of comments
             * @response 404 Article not found
             */
            get("/{id}/comments") {
                val articleId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid article ID")

                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 10

                val foundArticle = articles.find { it.id == articleId }
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Article #$articleId not found")

                val articleComments = comments.filter { it.postId == articleId }

                call.respond(
                    CommentListResponse(
                        comments = articleComments.take(size),
                        totalCount = articleComments.size,
                        page = page,
                        pageSize = size
                    )
                )
            }

            /**
             * Get featured articles
             * @query category [String] Category filter
             * @response 200 [ArticleListResponse] List of featured articles
             */
            get("/featured") {
                val category = call.request.queryParameters["category"]

                val filteredArticles = if (category != null) {
                    articles.filter { it.featured && it.category == category }
                } else {
                    articles.filter { it.featured }
                }

                call.respond(
                    ArticleListResponse(
                        articles = filteredArticles,
                        totalCount = filteredArticles.size,
                        page = 1,
                        pageSize = filteredArticles.size
                    )
                )
            }

            /**
             * Search articles
             * @query query [String] Search term
             * @query inTitle [Boolean] Search in title
             *   default: true
             * @query inContent [Boolean] Search in content
             *   default: true
             * @query inTags [Boolean] Search in tags
             *   default: false
             * @query fromDate [String] Start date (ISO format)
             * @query toDate [String] End date (ISO format)
             * @response 200 [SearchResultResponse] Search results
             */
            get("/search") {
                val query = call.request.queryParameters["query"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing query parameter")

                val inTitle = call.request.queryParameters["inTitle"]?.toBoolean() ?: true
                val inContent = call.request.queryParameters["inContent"]?.toBoolean() ?: true
                val inTags = call.request.queryParameters["inTags"]?.toBoolean() ?: false
                val fromDate = call.request.queryParameters["fromDate"]
                val toDate = call.request.queryParameters["toDate"]

                val results = articles.filter { article ->
                    val matchesTitle = inTitle && article.title.contains(query, ignoreCase = true)
                    val matchesContent = inContent && article.content.contains(query, ignoreCase = true)
                    val matchesTags = inTags && article.tags.any { it.contains(query, ignoreCase = true) }

                    matchesTitle || matchesContent || matchesTags
                }

                call.respond(
                    SearchResultResponse(
                        query = query,
                        results = results.map {
                            SearchResult(
                                type = "article",
                                id = it.id,
                                title = it.title,
                                snippet = it.content.take(100) + "...",
                                relevance = 0.95
                            )
                        },
                        totalCount = results.size,
                        executionTimeMs = 42
                    )
                )
            }
        }

        route("/api/users") {
            /**
             * Get all users
             * @query userRole [String] Filter by role
             *   enum: [admin, editor, author, user]
             * @query userStatus [String] Filter by status
             *   enum: [active, inactive, banned, pending]
             * @response 200 [ApiUserListResponse] List of users
             */
            get {
                val userRole = call.request.queryParameters["userRole"]
                val userStatus = call.request.queryParameters["userStatus"]

                val filteredUsers = apiUsers.filter { user ->
                    (userRole == null || user.userRole == userRole) &&
                            (userStatus == null || user.userStatus == userStatus)
                }

                call.respond(
                    ApiUserListResponse(
                        users = filteredUsers,
                        totalCount = filteredUsers.size,
                        page = 1,
                        pageSize = filteredUsers.size
                    )
                )
            }

            /**
             * Get user by ID
             * @path id [Int] User ID
             * @response 200 [ApiUser] User details
             * @response 404 User not found
             */
            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")

                val foundUser = apiUsers.find { it.id == id }
                    ?: return@get call.respond(HttpStatusCode.NotFound, "User #$id not found")

                call.respond(foundUser)
            }

            /**
             * Get user profile details
             * @path id [Int] User ID
             * @response 200 [ApiUserProfile] User profile
             * @response 404 User not found
             */
            get("/{id}/profile") {
                val userId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")

                val foundUser = apiUsers.find { it.id == userId }
                    ?: return@get call.respond(HttpStatusCode.NotFound, "User #$userId not found")

                call.respond(
                    ApiUserProfile(
                        userId = userId,
                        bio = "Software developer with passion for Kotlin",
                        avatarUrl = "https://example.com/avatars/$userId.jpg",
                        socialLinks = mapOf(
                            "github" to "https://github.com/user$userId",
                            "twitter" to "https://twitter.com/user$userId"
                        ),
                        preferences = ApiUserPreferences(
                            theme = "dark",
                            emailNotifications = true,
                            language = "en"
                        )
                    )
                )
            }

            /**
             * Get articles written by user
             * @path id [Int] User ID
             * @query status [String] Article status filter
             *   enum: [draft, published, archived]
             * @query page [Int] Page number
             *   minimum: 1
             *   default: 1
             * @query size [Int] Page size
             *   minimum: 1
             *   maximum: 50
             *   default: 10
             * @response 200 [ArticleListResponse] List of user's articles
             * @response 404 User not found
             */
            get("/{id}/articles") {
                val userId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")

                val status = call.request.queryParameters["status"]
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 10

                val foundUser = apiUsers.find { it.id == userId }
                    ?: return@get call.respond(HttpStatusCode.NotFound, "User #$userId not found")

                val userArticles = articles.filter {
                    it.authorId == userId && (status == null || it.category == status)
                }

                call.respond(
                    ArticleListResponse(
                        articles = userArticles.take(size),
                        totalCount = userArticles.size,
                        page = page,
                        pageSize = size
                    )
                )
            }

            /**
             * Search users
             * @query query [String] Search term
             * @query limit [Int] Number of results
             *   minimum: 1
             *   maximum: 100
             *   default: 10
             * @query offset [Int] Result offset
             *   minimum: 0
             *   default: 0
             * @query userRole [String] Filter by role
             *   enum: [admin, editor, author, user]
             * @query userStatus [String] Filter by status
             *   enum: [active, inactive, banned, pending]
             * @response 200 [ApiUserSearchResponse] Search results
             */
            get("/search") {
                val query = call.request.queryParameters["query"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing query parameter")

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                val userRole = call.request.queryParameters["userRole"]
                val userStatus = call.request.queryParameters["userStatus"]

                val filteredUsers = apiUsers.filter { user ->
                    (user.username.contains(query, ignoreCase = true) ||
                            user.email.contains(query, ignoreCase = true) ||
                            user.fullName.contains(query, ignoreCase = true)) &&
                            (userRole == null || user.userRole == userRole) &&
                            (userStatus == null || user.userStatus == userStatus)
                }

                val paginatedUsers = filteredUsers
                    .drop(offset)
                    .take(limit)

                call.respond(
                    ApiUserSearchResponse(
                        query = query,
                        users = paginatedUsers,
                        totalCount = filteredUsers.size
                    )
                )
            }

            /**
             * Check if username is available
             * @path username [String] Username to check
             * @response 200 [UsernameValidationResponse] Validation result
             */
            get("/validate/{username}") {
                val username = call.parameters["username"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing username parameter")

                val exists = apiUsers.any { it.username == username }

                call.respond(
                    UsernameValidationResponse(
                        username = username,
                        available = !exists,
                        message = if (exists) "Username already taken" else null
                    )
                )
            }
        }

        route("/posts") {
            /**
             * Get all posts
             * @query status [String] Filter by status
             *   enum: [draft, published, archived]
             * @query featured [Boolean] Filter featured posts
             * @response 200 [PostListResponse] List of posts
             */
            get {
                val status = call.request.queryParameters["status"]
                val featured = call.request.queryParameters["featured"]?.toBoolean()

                val filteredPosts = posts.filter { post ->
                    (status == null || post.status == status) &&
                            (featured == null || post.status == "published")
                }

                call.respond(
                    PostListResponse(
                        posts = filteredPosts,
                        totalCount = filteredPosts.size,
                        page = 1,
                        pageSize = filteredPosts.size
                    )
                )
            }

            /**
             * Create a new post
             * @body Post Post data
             * @response 201 Post created successfully
             * @response 400 Invalid post data
             */
            post {
                val newPost = call.receive<Post>()
                call.respondText("Created post: ${newPost.title}", status = HttpStatusCode.Created)
            }

            /**
             * Get post by ID
             * @path id [Int] Post ID
             * @response 200 [Post] Post details
             * @response 404 Post not found
             */
            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid post ID")

                val foundPost = posts.find { it.id == id }
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Post #$id not found")

                call.respond(foundPost)
            }

            /**
             * Update a post
             * @path id [Int] Post ID
             * @body Post Updated post data
             * @response 200 Post updated successfully
             * @response 404 Post not found
             * @response 400 Invalid post data
             */
            put("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid post ID")

                val foundPost = posts.find { it.id == id }
                    ?: return@put call.respond(HttpStatusCode.NotFound, "Post #$id not found")

                val updatedPost = call.receive<Post>()
                call.respondText("Updated post $id: ${updatedPost.title}")
            }

            /**
             * Get comments for a post
             * @path id [Int] Post ID
             * @query page [Int] Page number
             *   minimum: 1
             *   default: 1
             * @query size [Int] Page size
             *   minimum: 1
             *   maximum: 50
             *   default: 10
             * @response 200 [CommentListResponse] List of comments
             * @response 404 Post not found
             */
            get("/{id}/comments") {
                val postId = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid post ID")

                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 10

                val foundPost = posts.find { it.id == postId }
                    ?: return@get call.respond(HttpStatusCode.NotFound, "Post #$postId not found")

                val postComments = comments.filter { it.postId == postId }

                call.respond(
                    CommentListResponse(
                        comments = postComments.take(size),
                        totalCount = postComments.size,
                        page = page,
                        pageSize = size
                    )
                )
            }
        }

        route("/resources") {
            /**
             * Get a collection of different resource types
             * @response 200 [ResourceCollection] Collection of resources
             */
            get {
                val resources = listOf(
                    ResourceA("1", "Resource A", 42),
                    ResourceB("2", "Resource B", listOf("tag1", "tag2")),
                    ResourceC("3", "Resource C", mapOf("key1" to "value1", "key2" to "value2"))
                )

                call.respond(ResourceCollection(resources))
            }

            /**
             * Get a specific resource by ID
             * @path id [String] Resource ID
             * @response 200 [ResourceDetail] Resource details
             * @response 404 Resource not found
             */
            get("/{id}") {
                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing resource ID")

                val resource = when (id) {
                    "1" -> ResourceA("1", "Resource A", 42)
                    "2" -> ResourceB("2", "Resource B", listOf("tag1", "tag2"))
                    "3" -> ResourceC("3", "Resource C", mapOf("key1" to "value1", "key2" to "value2"))
                    else -> null
                }

                if (resource == null) {
                    call.respond(HttpStatusCode.NotFound, "Resource #$id not found")
                } else {
                    call.respond(ResourceDetail(resource))
                }
            }
        }
    }
}

@Serializable
data class Article(
    val id: Int,
    val title: String,
    val content: String,
    val authorId: Int,
    val publishDate: String,
    val category: String,
    val tags: List<String>,
    val featured: Boolean = false
)

@Serializable
data class ArticleListResponse(
    val articles: List<Article>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class Post(
    val id: Int,
    val title: String,
    val content: String,
    val authorId: Int,
    val status: String,
    val publishDate: String?,
    val updateDate: String?,
    val viewCount: Int = 0
)

@Serializable
data class PostListResponse(
    val posts: List<Post>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class Comment(
    val id: Int,
    val postId: Int,
    val parentCommentId: Int? = null,
    val authorId: Int,
    val content: String,
    val createdAt: String,
    val updatedAt: String? = null,
    val likes: Int = 0
)

@Serializable
data class CommentListResponse(
    val comments: List<Comment>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class ApiUser(
    val id: Int,
    val username: String,
    val email: String,
    val fullName: String,
    val userRole: String,
    val userStatus: String,
    val joinDate: String
)

@Serializable
data class ApiUserListResponse(
    val users: List<ApiUser>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class ApiUserProfile(
    val userId: Int,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val socialLinks: Map<String, String> = emptyMap(),
    val preferences: ApiUserPreferences
)

@Serializable
data class ApiUserPreferences(
    val theme: String,
    val emailNotifications: Boolean = true,
    val language: String = "en"
)

@Serializable
data class SearchResultResponse(
    val query: String,
    val results: List<SearchResult>,
    val totalCount: Int,
    val executionTimeMs: Long
)

@Serializable
data class SearchResult(
    val type: String,
    val id: Int,
    val title: String,
    val snippet: String,
    val relevance: Double
)

@Serializable
data class ApiUserSearchResponse(
    val query: String,
    val users: List<ApiUser>,
    val totalCount: Int
)

@Serializable
data class UsernameValidationResponse(
    val username: String,
    val available: Boolean,
    val message: String? = null
)

@Serializable
data class ReactionStats(
    val likes: Int,
    val dislikes: Int,
    val hearts: Int,
    val views: Int
)

@Serializable
data class RevisionListResponse(
    val revisions: List<Revision>,
    val currentVersion: Int
)

@Serializable
data class Revision(
    val version: Int,
    val timestamp: String,
    val editorId: Int,
    val editorName: String,
    val changeDescription: String? = null
)

@Serializable
data class AttachmentListResponse(
    val attachments: List<Attachment>,
    val count: Int
)

@Serializable
data class Attachment(
    val id: Int,
    val postId: Int,
    val name: String,
    val type: String,
    val size: Long,
    val url: String,
    val uploadedAt: String
)

@Serializable
data class ResourceCollection(
    val resources: List<Resource>
)

@Serializable
data class ResourceDetail(
    val resource: Resource
)

@Serializable
data class ComplexParamsResponse(
    val requiredString: String,
    val optionalString: String?,
    val intValue: Int,
    val doubleValue: Double,
    val boolValue: Boolean,
    val enumValue: String,
    val arrayValues: List<String>,
    val requiredHeader: String,
    val optionalHeader: String?
)

private val posts = mutableListOf(
    Post(1, "First Post", "Content for the first post", 1, "published", "2023-01-01", null, 120),
    Post(2, "Second Post", "Content for the second post", 2, "published", "2023-01-15", "2023-01-16", 85),
    Post(3, "Draft Post", "Draft content not yet published", 1, "draft", null, null, 0)
)

private val articles = mutableListOf(
    Article(
        1,
        "Introduction to Kotlin",
        "Kotlin is a modern programming language...",
        1,
        "2023-01-10",
        "Programming",
        listOf("kotlin", "programming"),
        true
    ),
    Article(
        2,
        "Ktor Framework",
        "Ktor is an asynchronous framework...",
        2,
        "2023-02-15",
        "Programming",
        listOf("kotlin", "ktor", "web"),
        false
    ),
    Article(
        3,
        "OpenAPI with Ktor",
        "Generate OpenAPI specifications...",
        1,
        "2023-03-20",
        "API",
        listOf("openapi", "ktor", "api"),
        true
    )
)

private val comments = mutableListOf(
    Comment(1, 1, null, 2, "Great post!", "2023-01-02", null, 5),
    Comment(2, 1, null, 3, "Very informative", "2023-01-03", null, 3),
    Comment(3, 2, null, 1, "Thanks for sharing", "2023-01-16", null, 2),
    Comment(4, 1, 1, 3, "I agree!", "2023-01-04", null, 1)
)

private val apiUsers = mutableListOf(
    ApiUser(1, "john_doe", "john@example.com", "John Doe", "author", "active", "2022-11-01"),
    ApiUser(2, "jane_smith", "jane@example.com", "Jane Smith", "editor", "active", "2022-12-15"),
    ApiUser(3, "admin_user", "admin@example.com", "Admin User", "admin", "active", "2022-10-01")
)