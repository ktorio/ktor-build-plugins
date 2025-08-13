package io.ktor.openapi.routing

import io.ktor.compiler.utils.*
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.resolve.SessionHolder
import org.jetbrains.kotlin.fir.types.ConeTypeProjection

data class RouteStack(
    override val session: FirSession,
    val stack: List<RouteNode> = emptyList(),
): SessionHolder {
    val evaluator: FirScopedEvaluator by lazy {
        with(session) {
            createCallStackEvaluator(stack.map { it.fir })
        }
    }
    override val scopeSession get() =
        evaluator.scopeSession

    operator fun plus(node: RouteNode): RouteStack =
        RouteStack(session, stack + node)
}

context(context: RouteStack)
fun FirExpression.evaluate() =
    context.evaluator.evaluate(this)

context(context: RouteStack)
fun ConeTypeProjection.resolveType() =
    context.evaluator.resolveTypeProjection(this)