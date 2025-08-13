package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.*
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTE_CLASS
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_PACKAGE
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.fullyExpandedClassId
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.references.toResolvedFunctionSymbol
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.text

class CustomFunctionInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        val receiverFqName = isCustomRouteFunction(expression, context)
        if (receiverFqName != "$ROUTING_PACKAGE.$ROUTE_CLASS") return RoutingReferenceResult.None

        val invocation = expression.getLocation() ?: return RoutingReferenceResult.None
        val body = expression.calleeReference.getDeclarationLocation() ?: return RoutingReferenceResult.None

        val routeNode = RouteNode.Function(
            filePath = context.containingFilePath,
            fir = expression,
            declaration = body,
            fields = {
                invocation.parseKDoc() + body.parseKDoc()
            }
        )

        return RoutingReferenceResult.Match(routeNode)
    }

    /**
     * Resolved the source file and text range of a reference.
     */
    context(context: CheckerContext)
    private fun FirNamedReference.getDeclarationLocation(): SourceTextRange? {
        val resolvedFunctionSymbol = resolved?.toResolvedFunctionSymbol() ?: return null
        val containingFile = context.session.firProvider.getFirCallableContainerFile(resolvedFunctionSymbol) ?: return null
        val filePath = containingFile.sourceFile?.path ?: return null
        val fileText = containingFile.source?.text ?: return null
        val range = resolvedFunctionSymbol.source?.range ?: return null

        return SourceTextRange(filePath, fileText, range)
    }

    private fun isCustomRouteFunction(
        expression: FirFunctionCall,
        context: CheckerContext
    ): String? = expression.extensionReceiver?.resolvedType
        ?.fullyExpandedClassId(context.session)
        ?.asFqNameString()
}
