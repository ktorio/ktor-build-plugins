package io.ktor.openapi.routing

import io.ktor.compiler.utils.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionHolder
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirExpression
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
    val scopeSession get() = evaluator.scopeSession

    operator fun plus(node: RouteNode): RouteStack =
        RouteStack(session, stack + node)
}

context(context: RouteStack, checker: CheckerContext, reporter: DiagnosticReporter)
fun FirExpression.evaluate(): FirEvaluatorResult? {
    val result = runCatching {
        context.evaluator.evaluate(this)
    }

    // TODO report warning
    if (result.isFailure || result.getOrNull() !is FirEvaluatorResult.Evaluated) {}

    return result.getOrNull()
}

context(context: RouteStack)
fun ConeTypeProjection.resolveType() =
    context.evaluator.resolveTypeProjection(this)