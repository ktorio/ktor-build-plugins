package io.ktor.openapi.routing

import org.jetbrains.kotlin.fir.FirSession

object RouteCollector {
    fun collectRoutes(graph: RouteCallGraph): List<ResolvedRoute> {
        // Process each route node to generate ResolvedRoutes
        return graph.routes.flatMap { route ->
            if (route.method == null) return@flatMap emptyList()
            
            // Find all paths from roots to this route
            val allPaths = graph.findAllPathsToRoots(route)
            
            // Process each path and merge fields
            with(graph.session) {
                allPaths.mapNotNull { path ->
                    resolve(
                        parents = path,
                        children = graph.getChildren(route)
                    )
                }
            }
        }
    }

    context(session: FirSession)
    fun resolve(
        parents: Iterable<RouteNode> = emptyList(),
        children: Iterable<RouteNode> = emptyList(),
    ): ResolvedRoute? {
        var stack = RouteStack(session)
        var fields = emptyList<RouteField>()
        for (ancestor in parents) {
            fields = ancestor.fields(stack).merge(fields)
            stack += ancestor
        }

        for (child in children) {
            fields = fields.merge(child.fields(stack))
        }

        val path = fields.path ?: return null
        val method = fields.method ?: return null
        if (RouteField.Ignore in fields) return null

        return ResolvedRoute(
            path = path,
            method = method,
            fields = fields,
        )
    }
}