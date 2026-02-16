package io.ktor.openapi.ir.inference

import io.ktor.openapi.ir.*
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isString

val ParameterInference = IrCallHandlerInference { call: IrCall ->
    if (!call.symbol.owner.name.asString().startsWith("get")) return@IrCallHandlerInference null
    val receiver = call.functionReceiver ?: return@IrCallHandlerInference null
    if (receiver.type.classFqName?.asString() != "io.ktor.util.StringValues") return@IrCallHandlerInference null
    val getParameter = call.symbol.owner.parameters.firstOrNull {
        it.kind == IrParameterKind.Regular && it.type.isString()
    } ?: return@IrCallHandlerInference null

    val key = call.arguments[getParameter.indexInParameters]
        ?.let { LocalReference.of(it) }
        ?: return@IrCallHandlerInference null

    val receiverArgCall = call.arguments[receiver.indexInParameters] as? IrCall

    // we can sometimes infer what kind of parameter it is from the receiver
    // this is not the case for the usual `call.parameters` reference,
    // or when we're not using a property getter
    when(receiverArgCall?.symbol?.owner?.name?.asString()) {
        "<get-headers>" -> listOf(RouteField.Parameter(ParamIn.HEADER, key))
        "<get-pathVariables>",
        "<get-pathParameters>" -> listOf(RouteField.Parameter(ParamIn.PATH, key))
        "<get-queryParameters>" -> listOf(RouteField.Parameter(ParamIn.QUERY, key))

        // ambiguous scenario, we avoid defining `in` for now
        // this can usually be inferred later at runtime
        else -> listOf(RouteField.Parameter(name = key))
    }
}
