package io.ktor.openapi.ir

import io.ktor.openapi.routing.RouteField
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.expressions.IrCall

/**
 * Any call with extension receivers that would likely be found in a routing handler
 * are processed for potential OpenAPI information that can be applied to the annotation
 * chained call.
 */
fun interface IrCallHandlerInterpreter {
    companion object {
        fun of(vararg interpreters: IrCallHandlerInterpreter) =
            IrCallHandlerInterpreter { call ->
                interpreters.firstNotNullOfOrNull { it.interpret(call) }
            }
    }

    context(context: CodeGenContext)
    fun interpret(call: IrCall): List<RouteField>?
}