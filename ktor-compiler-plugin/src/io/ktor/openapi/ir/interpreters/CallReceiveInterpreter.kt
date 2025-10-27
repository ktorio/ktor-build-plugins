package io.ktor.openapi.ir.interpreters

import io.ktor.openapi.ir.IrCallHandlerInterpreter
import io.ktor.openapi.ir.isApplicationCall
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.TypeReference.Companion.asReference
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.kotlinFqName

val CallReceiveInterpreter = IrCallHandlerInterpreter { call: IrCall ->
    if (!call.isApplicationCall()) return@IrCallHandlerInterpreter null

    val packageFqName = call.symbol.owner.parent.kotlinFqName.asString()
    if (packageFqName != "io.ktor.server.request") return@IrCallHandlerInterpreter null

    val functionName = call.symbol.owner.name.asString()
    if (!functionName.startsWith("receive")) return@IrCallHandlerInterpreter null

    val requestContentType = getContentTypeArgument(call)
        ?: buildContentTypeReference(call.symbol, contentTypeApplication, contentTypeUnknown)
    val requestBodyType = call.typeArguments.firstOrNull()?.asReference()
        ?: return@IrCallHandlerInterpreter null

    listOf(
        RouteField.Body(
            contentType = requestContentType,
            typeReference = requestBodyType
        )
    )
}