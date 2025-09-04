// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.receive
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

// Simple resources for articles
@Resource("/articles")
class Articles {
    @Resource("/{id}")
    class Id(val parent: Articles, val id: Int) {
        @Resource("/comments")
        class Comments(val parent: Id)
    }

    @Resource("/featured")
    class Featured(val parent: Articles)
}

// Resources with query parameters
@Resource("/users")
class Users {
    @Resource("/search")
    class Search(
        val parent: Users,
        val query: String,
        val limit: Int = 10,
        val offset: Int = 0
    )
}

// Resources with body content
@Resource("/posts")
class Posts {
    @Resource("/{id}")
    class Id(val parent: Posts, val id: Int) {
        @Resource("/comments")
        class Comments(val parent: Id)
    }
}

@Serializable
data class Post(val id: Int, val title: String, val content: String, val authorId: Int)

@Serializable
data class Comment(val text: String, val authorId: Int)

private val posts = mutableListOf(
    Post(1, "Post 1", "Content 1", 1),
    Post(2, "Post 2", "Content 2", 2),
    Post(3, "Post 3", "Content 3", 3)
)

private val comments = mutableListOf(
    Comment("Comment 1.1", 1),
    Comment("Comment 1.2", 1),
    Comment("Comment 2.1", 2)
)

/**
 * This example demonstrates how type-safe routing with @Resource annotations
 * can be used in Ktor applications and processed by the OpenAPI generator.
 */
fun Application.resources() {
    install(ContentNegotiation) {
        json()
    }

    install(Resources)

    routing {
        // Basic resource routes
        get<Articles> { articles ->
            call.respond(posts)
        }

        get<Articles.Id> { article ->
            val post = posts.find { it.id == article.id }
                ?: return@get call.respondText("Article #${article.id} not found", status = HttpStatusCode.NotFound)
            call.respond(post)
        }

        get<Articles.Id.Comments> { comments ->
            call.respond(comments)
        }

        get<Articles.Featured> { featured ->
            call.respond(posts)
        }

        // Resources with query parameters
        get<Users.Search> { search ->
            call.respondText("Searching for users matching '${search.query}', limit: ${search.limit}, offset: ${search.offset}")
        }

        // Resources with request body
        post<Posts> {
            val post = call.receive<Post>()
            call.respondText("Created post: ${post.title}")
        }

        put<Posts.Id> { postId ->
            val updatedPost = call.receive<Post>()
            call.respondText("Updated post ${postId.id}: ${updatedPost.title}")
        }

        post<Posts.Id.Comments> { comments ->
            call.respondText("Added comment to post ${comments.parent.id}")
        }
    }
}