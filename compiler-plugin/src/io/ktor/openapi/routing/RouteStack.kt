package io.ktor.openapi.routing

import io.ktor.compiler.KtorCompilerErrors
import io.ktor.compiler.utils.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.resolve.SessionHolder
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import kotlin.math.exp

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

context(context: RouteStack, checker: CheckerContext, reporter: DiagnosticReporter)
fun FirExpression.evaluate(): FirEvaluatorResult? {
    val result = runCatching {
        context.evaluator.evaluate(this)
    }

    if (result.isFailure || result.getOrNull() !is FirEvaluatorResult.Evaluated) {
        reporter.reportOn(
            this.source,
            KtorCompilerErrors.FAILED_INFERENCE,
            "Ktor could not infer the value of this",
        )
    }

    return result.getOrNull()
}

context(context: RouteStack)
fun ConeTypeProjection.resolveType() =
    context.evaluator.resolveTypeProjection(this)