package io.ktor.openapi.routing

import org.jetbrains.kotlin.fir.FirSession

/**
 * Represents a graph of routes where a node can have multiple parents.
 */
class RouteGraph(val session: FirSession) {
    private val parentToChildren = mutableMapOf<RouteNode, MutableSet<RouteNode>>()
    private val childToParents = mutableMapOf<RouteNode, MutableSet<RouteNode>>()
    internal val routes = mutableListOf<RouteNode.Route>()
    internal val functions = mutableListOf<RouteNode.Function>()

    fun isEmpty() = routes.isEmpty()

    fun add(node: RouteNode) {
        for (i in routes.indices.reversed()) {
            val parent = routes[i]
            if (node in parent) {
                addEdge(parent, node)
                break
            } else if (parent.filePath != node.filePath || parent.isTopLevel()) {
                break
            }
        }
        when (node) {
            is RouteNode.Function -> functions.add(node)
            is RouteNode.Route -> routes.add(node)
            is RouteNode.CallFeature -> {}
        }
    }

    /**
     * Now that the function nodes are all accounted for, we can look for their children to
     * populate the edges.
     */
    fun build(): RouteGraph {
        for (function in functions) {
            for (other in functions) {
                if (other in function && other.isTopLevel())
                    addEdge(other, function)
            }
            for (route in routes) {
                if (route in function && route.isTopLevel())
                    addEdge(function, route)
            }
        }
        return this
    }

    fun RouteNode.isTopLevel(): Boolean =
        childToParents[this].orEmpty().none { it is RouteNode.Route }

    /**
     * Adds a parent-child relationship between nodes.
     */
    private fun addEdge(parent: RouteNode, child: RouteNode) {
        parentToChildren.getOrPut(parent) { mutableSetOf() }.add(child)
        childToParents.getOrPut(child) { mutableSetOf() }.add(parent)
    }

    /**
     * Checks if a node has any parents.
     */
    private fun hasParents(node: RouteNode): Boolean =
        childToParents.containsKey(node) && childToParents[node]?.isNotEmpty() == true

    /**
     * Gets all parents of a node.
     */
    private fun getParents(node: RouteNode): Set<RouteNode> =
        childToParents[node] ?: emptySet()

    /**
     * Gets all children of a node.
     */
    fun getChildren(node: RouteNode): Set<RouteNode> =
        parentToChildren[node] ?: emptySet()

    /**
     * Finds all paths from a node to root nodes (nodes with no parents).
     * Returns a list of paths, where each path is a list of nodes from the node to a root.
     */
    fun findAllPathsToRoots(node: RouteNode): List<List<RouteNode>> {
        if (!hasParents(node)) return listOf(listOf(node))

        val result = mutableListOf<List<RouteNode>>()
        val parents = getParents(node)

        for (parent in parents) {
            val parentPaths = findAllPathsToRoots(parent)
            for (path in parentPaths) {
                result.add(path + node)
            }
        }

        return result
    }
}