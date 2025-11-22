package io.ktor.openapi.ir

import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.visitors.IrVisitor

class CallHandlerAnalyzer(
    val callInterpreter: IrCallHandlerInference,
    val context: CodeGenContext,
): IrVisitor<Unit, MutableList<RouteField>>(), CodeGenContext by context {
    fun analyze(element: IrElement): List<RouteField> =
        buildList { element.accept(this@CallHandlerAnalyzer, this) }

    override fun visitElement(element: IrElement, data: MutableList<RouteField>) =
        element.acceptChildren(this, data)

    override fun visitCall(expression: IrCall, data: MutableList<RouteField>) {
        try {
            callInterpreter.findRouteDetails(expression)?.let {
                data.addAll(it)
            }
        } catch (e: Throwable) {
            context.log("Failed to analyze $expression", e)
        }
        super.visitCall(expression, data)
    }
}