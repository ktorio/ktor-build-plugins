package io.ktor.openapi.ir.interpreters

import io.ktor.openapi.ir.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isString

val ParameterInterpreter = IrCallHandlerInterpreter { call: IrCall ->
    if (!call.symbol.owner.name.asString().startsWith("get")) return@IrCallHandlerInterpreter null
    val receiver = call.functionReceiver ?: return@IrCallHandlerInterpreter null
    if (receiver.type.classFqName?.asString() != "io.ktor.util.StringValues") return@IrCallHandlerInterpreter null
    val getParameter = call.symbol.owner.parameters.firstOrNull {
        it.kind == IrParameterKind.Regular && it.type.isString()
    } ?: return@IrCallHandlerInterpreter null
    val key = call.arguments[getParameter.indexInParameters]
        ?: return@IrCallHandlerInterpreter null

    val receiverName = call.arguments.filterIsInstance<IrCall>()
        .firstOrNull()?.symbol?.owner?.name?.asString()
    when(receiverName) {
        "<get-headers>" -> listOf(RouteField.Parameter(ParamIn.HEADER, LocalReference.Expression(key)))
        "<get-pathVariables>",
        "<get-pathParameters>" -> listOf(RouteField.Parameter(ParamIn.PATH, LocalReference.Expression(key)))
        "<get-queryParameters>" -> listOf(RouteField.Parameter(ParamIn.QUERY, LocalReference.Expression(key)))

        // ambiguous scenario, we avoid defining `in` for now
        else -> listOf(RouteField.Parameter(name = LocalReference.Expression(key)))
    }
}