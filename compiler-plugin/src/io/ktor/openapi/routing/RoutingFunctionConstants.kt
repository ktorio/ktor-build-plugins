package io.ktor.openapi.routing

object RoutingFunctionConstants {
    const val ROUTING_PACKAGE = "io.ktor.server.routing"
    const val ROUTE_CLASS = "Route"
    const val ROUTE = "route"
    const val GET = "get"
    const val POST = "post"
    const val PUT = "put"
    const val DELETE = "delete"
    const val HEAD = "head"
    const val OPTIONS = "options"
    const val PATCH = "patch"
    const val INSTALL = "install"
    const val CONTENT_NEGOTIATION = "ContentNegotiation"
    const val AUTHENTICATION = "Authentication"

    val HTTP_METHODS = setOf(GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH)
    val ROUTING_FUNCTION_NAMES = HTTP_METHODS + ROUTE
    val JSON_LIKE_CALLS = listOf("json", "jackson", "gson")
}