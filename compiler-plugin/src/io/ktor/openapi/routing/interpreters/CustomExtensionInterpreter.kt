package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.*
import io.ktor.openapi.routing.RouteField
import io.ktor.openapi.routing.RoutingCallInterpreter
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTE_CLASS
import io.ktor.openapi.routing.RoutingFunctionConstants.ROUTING_PACKAGE
import io.ktor.openapi.routing.RoutingReference
import io.ktor.openapi.routing.SourceCoordinates
import io.ktor.openapi.routing.SourceFile
import io.ktor.openapi.routing.SourceRange
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

class CustomExtensionInterpreter : RoutingCallInterpreter {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): io.ktor.openapi.routing.RoutingReferenceResult {
        val receiverFqName = expression.extensionReceiver?.resolvedType
            ?.fullyExpandedClassId(context.session)
            ?.asFqNameString()
        if (receiverFqName != "$ROUTING_PACKAGE.$ROUTE_CLASS") return io.ktor.openapi.routing.RoutingReferenceResult.None

        val sourceFile = context.getSourceFile() ?: return io.ktor.openapi.routing.RoutingReferenceResult.None
        val invocation = SourceRange(sourceFile, expression.source?.range ?: return io.ktor.openapi.routing.RoutingReferenceResult.None)
        val functionName = expression.calleeReference.name.asString()

        val body = expression.calleeReference.getCoordinates() ?: return io.ktor.openapi.routing.RoutingReferenceResult.None
        val routeFields = invocation.parseKDoc() + body.parseKDoc()
        val coordinates = SourceCoordinates(invocation, body)

        val routingReference = RoutingReference.ExtensionFunction(
            functionName,
            routeFields,
            coordinates
        )

        return io.ktor.openapi.routing.RoutingReferenceResult.Match(routingReference, emptyMap())
    }

    /**
     * Resolved the source file and text range of a reference.
     */
    context(context: CheckerContext)
    private fun FirNamedReference.getCoordinates(): SourceRange? {
        val resolvedFunctionSymbol = resolved?.toResolvedFunctionSymbol() ?: return null
        val containingFile = context.session.firProvider.getFirCallableContainerFile(resolvedFunctionSymbol) ?: return null
        val filePath = containingFile.sourceFile?.path ?: return null
        val fileText = containingFile.source?.text ?: return null
        val range = resolvedFunctionSymbol.source?.range ?: return null

        return SourceRange(
            file = SourceFile(
                path = filePath,
                text = fileText,
            ),
            range = range,
        )
    }
}
