package io.ktor.compiler.utils

import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.text

/**
 * Handle named arguments and position arguments.
 */
fun FirFunctionCall.getArgument(name: String, index: Int = 0): FirExpression? =
    arguments.firstOrNull { arg ->
        arg is FirNamedArgumentExpression && arg.name.asString() == name
    } ?: arguments.getOrNull(index)

@OptIn(PrivateConstantEvaluatorAPI::class)
context(context: CheckerContext)
fun FirExpression.resolveToString(): String? =
    FirExpressionEvaluator.evaluateExpression(this, context.session).asString()

fun FirEvaluatorResult?.asString(): String? =
    ((this as? FirEvaluatorResult.Evaluated)?.result as? FirLiteralExpression)?.value?.toString()

context(context: CheckerContext)
fun FirFunctionCall.getArgumentAsString(name: String, index: Int = 0): String? =
    getArgument(name, index)?.resolveToString()

// TODO only supports string literals atm, should handle constants and possibly trace up the stack for variables
//fun FirExpression.resolveToString(): String? =
//    when(this) {
//        is FirLiteralExpression -> this.value?.toString()
//        else -> null
//    }

val KtSourceElement.range: IntRange get() =
    startOffset..endOffset

context(context: CheckerContext)
fun FirExpression.getLocation(): SourceTextRange? {
    return SourceTextRange(
        context.containingFilePath ?: return null,
        context.containingFile?.source?.text ?: return null,
        source?.range ?: return null,
    )
}