package io.ktor.openapi.routing

data class ResolvedRoute(
    val path: String,
    val method: String,
    val fields: RouteFieldList = emptyList(),
)