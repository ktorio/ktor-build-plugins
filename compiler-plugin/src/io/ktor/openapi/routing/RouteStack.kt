package io.ktor.openapi.routing

import io.ktor.compiler.utils.*
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirExpression

data class RouteStack(
    val session: FirSession,
    val stack: List<RouteNode> = emptyList(),
) {
    operator fun plus(node: RouteNode): RouteStack =
        RouteStack(session, stack + node)

    fun FirExpression.evaluate() =
        with(session) {
            createCallStackEvaluator(stack.map { it.fir })
        }.evaluate(this)
}