package io.ktor.openapi.ir.interpreters

import io.ktor.openapi.ir.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.ir.expressions.IrCall

val RequestHeaderExtensionInterpreter = IrCallHandlerInterpreter { call: IrCall ->
    if (call.symbol.owner.name.asString() != "header") return@IrCallHandlerInterpreter null
    if (!call.receiverIsType("io.ktor.server.routing.RoutingRequest")) return@IrCallHandlerInterpreter null
    val key = call.arguments.firstOrNull() ?: return@IrCallHandlerInterpreter null
    listOf(RouteField.Parameter(ParamIn.HEADER, LocalReference.Expression(key)))
}