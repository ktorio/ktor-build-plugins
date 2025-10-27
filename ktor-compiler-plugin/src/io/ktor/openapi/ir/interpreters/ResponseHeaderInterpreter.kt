package io.ktor.openapi.ir.interpreters

import io.ktor.openapi.ir.IrCallHandlerInterpreter
import io.ktor.openapi.ir.receiverIsType
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall

val AppendResponseHeaderInterpreter = IrCallHandlerInterpreter { call: IrCall ->
    if (call.symbol.owner.name.asString() != "append") return@IrCallHandlerInterpreter null
    if (!call.receiverIsType("io.ktor.server.response.ResponseHeaders")) return@IrCallHandlerInterpreter null
    val keyParameter = call.symbol.owner.parameters.firstOrNull { it.kind == IrParameterKind.Regular } ?: return@IrCallHandlerInterpreter null
    val key = call.arguments[keyParameter.indexInParameters] ?: return@IrCallHandlerInterpreter null

    listOf(RouteField.ResponseHeader(LocalReference.Expression(key)))
}

val ResponseHeaderExtensionInterpreter = IrCallHandlerInterpreter { call: IrCall ->
    if (call.symbol.owner.name.asString() != "header") return@IrCallHandlerInterpreter null
    if (!call.receiverIsType("io.ktor.server.response.ApplicationResponse")) return@IrCallHandlerInterpreter null
    val keyParameter = call.symbol.owner.parameters.firstOrNull { it.kind == IrParameterKind.Regular } ?: return@IrCallHandlerInterpreter null
    val key = call.arguments[keyParameter.indexInParameters] ?: return@IrCallHandlerInterpreter null

    listOf(RouteField.ResponseHeader(LocalReference.Expression(key)))
}