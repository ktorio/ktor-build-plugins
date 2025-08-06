package io.ktor.compiler.utils

import io.ktor.openapi.routing.SourceFile
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.text

/**
 * Handle named arguments and position arguments.
 */
fun FirFunctionCall.getArgument(name: String, index: Int = 0): FirExpression? =
    arguments.firstOrNull { arg ->
        arg is FirNamedArgumentExpression && arg.name.asString() == name
    } ?: arguments.getOrNull(index)

fun FirFunctionCall.getArgumentAsString(name: String, index: Int = 0): String? =
    getArgument(name, index)?.resolveToString()

// TODO only supports string literals atm, should handle constants and possibly trace up the stack for variables
fun FirExpression.resolveToString(): String? =
    when(this) {
        is FirLiteralExpression -> this.value?.toString()
        else -> null
    }

val KtSourceElement.range: IntRange get() =
    startOffset..endOffset

fun CheckerContext.getSourceFile(): SourceFile? {
    return SourceFile(
        containingFilePath ?: return null,
        containingFile?.source?.text ?: return null,
    )
}