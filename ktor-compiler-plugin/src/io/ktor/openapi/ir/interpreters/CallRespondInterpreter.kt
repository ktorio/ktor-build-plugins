package io.ktor.openapi.ir.interpreters

import io.ktor.openapi.ir.CodeGenContext
import io.ktor.openapi.ir.IrCallHandlerInterpreter
import io.ktor.openapi.ir.isApplicationCall
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.TypeReference.Companion.asReference
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.kotlinFqName

private const val HTTP_STATUS_CODE = "HttpStatusCode"

val CallRespondInterpreter = IrCallHandlerInterpreter { call: IrCall ->
    if (!call.isApplicationCall()) return@IrCallHandlerInterpreter null

    val functionName = call.symbol.owner.name.asString()

    // Check if this is a respond* function on call receiver in io.ktor.server.response package
    if (!functionName.startsWith("respond")) return@IrCallHandlerInterpreter null

    val packageFqName = call.symbol.owner.parent.kotlinFqName.asString()
    if (packageFqName != "io.ktor.server.response") return@IrCallHandlerInterpreter null

    // Extract response information
    val responseBodyType = findResponseBodyArgument(call)?.asReference()
    val statusCode = getStatusArgument(call) ?: getStatusFromFunction(functionName)
    val contentType = getContentTypeArgument(call) ?: getContentTypeFromFunction(call)

    listOf(
        RouteField.Response(
            code = statusCode,
            contentType = contentType,
            typeReference = responseBodyType
        )
    )
}

private fun findResponseBodyArgument(call: IrCall): IrType? {
    return call.typeArguments.firstOrNull()?.takeIf {
        it.classOrNull?.owner?.name?.asString() != HTTP_STATUS_CODE
    }
}

private fun getStatusArgument(call: IrCall): LocalReference? {
    return call.arguments.firstOrNull {
        it?.type?.classOrNull?.owner?.name?.asString() == HTTP_STATUS_CODE
    }?.let(LocalReference::of)
}

private fun getStatusFromFunction(functionName: String): LocalReference =
    when(functionName) {
        "respondRedirect" -> LocalReference.IntValue(302)
        else -> LocalReference.IntValue(200)
    }

context(context: CodeGenContext)
private fun getContentTypeFromFunction(call: IrCall): LocalReference? {
    val functionName = call.symbol.owner.name.asString()
    return when (functionName) {
        "respondText" -> contentTypeText to contentTypePlaintext
        "respondBytes" -> contentTypeApplication to contentTypeOctetStream
        "respondHtml" -> contentTypeText to contentTypeHtml
        else -> null
    }?.let { (classId, callableId) ->
        buildContentTypeReference(call.symbol, classId, callableId)
    }
}