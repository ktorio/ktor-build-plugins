package io.ktor.compiler.utils

import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.evaluateAs
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.references.symbol
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.text

fun FirFunctionCall.getArgument(name: String, index: Int = 0): FirExpression? =
    arguments.firstOrNull { arg ->
        arg is FirNamedArgumentExpression && arg.name.asString() == name
    } ?: arguments.getOrNull(index)

/**
 * Handle named arguments and position arguments.
 */
context(context: CheckerContext)
fun FirFunctionCall.getArgument(name: String, index: Int = 0, type: String? = null): FirExpression? {
    val args = if (arguments.lastOrNull() is FirAnonymousFunctionExpression) arguments.dropLast(1) else arguments
    val candidate = args.firstOrNull { it is FirNamedArgumentExpression && it.name.asString() == name }
        ?: args.getOrNull(index)
    return candidate?.takeIf {
        type == null ||
                it.resolvedType.toClassSymbol(context.session)?.classId?.shortClassName?.asString() == type
    }
}

fun FirEvaluatorResult?.asString(): String? =
    ((this as? FirEvaluatorResult.Evaluated)?.result as? FirLiteralExpression)?.value?.toString()

fun FirEvaluatorResult?.asBoolean(): Boolean? =
    ((this as? FirEvaluatorResult.Evaluated)?.result as? FirLiteralExpression)?.value as? Boolean

fun FirEvaluatorResult?.sourceText(): String? =
    ((this as? FirEvaluatorResult.Evaluated)?.result as? FirExpression)?.source?.text?.toString()

context(context: CheckerContext)
fun FirFunctionCall.getArgumentAsStringConstant(name: String, index: Int = 0): String? =
    getArgument(name, index)?.resolveToString()

/**
 * Resolves the expression to a string value without the assistance of the route call stack.
 */
context(context: CheckerContext)
fun FirExpression.resolveToString(): String =
    evaluateAs<FirLiteralExpression>(context.session)?.value.toString()

val KtSourceElement.range: IntRange get() =
    startOffset..endOffset

context(context: CheckerContext)
fun FirExpression.getLocation(): SourceTextRange? {
    return SourceTextRange(
        context.containingFilePath ?: return null,
        context.containingFileSymbol?.source?.text ?: return null,
        source?.range ?: return null,
    )
}

fun FirFunctionCall.getFunctionName(): String =
    calleeReference.name.asString()

fun FirFunctionCall.isInPackage(packageName: String) =
    calleeReference.symbol?.packageFqName()?.asString() == packageName