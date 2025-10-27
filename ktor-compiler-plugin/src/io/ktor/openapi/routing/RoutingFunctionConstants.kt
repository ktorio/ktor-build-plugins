package io.ktor.openapi.routing

object RoutingFunctionConstants {
    const val ROUTING_PACKAGE = "io.ktor.server.routing"
    const val ROUTE = "route"
    const val GET = "get"
    const val POST = "post"
    const val PUT = "put"
    const val DELETE = "delete"
    const val HEAD = "head"
    const val OPTIONS = "options"
    const val PATCH = "patch"

    val HTTP_METHODS = setOf(GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH)
    val ROUTING_FUNCTION_NAMES = HTTP_METHODS + ROUTE
}