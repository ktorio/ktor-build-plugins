package io.ktor.compiler.utils

import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirNamedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.arguments

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