package io.ktor.openapi.ir.generators

import io.ktor.openapi.Logger
import io.ktor.openapi.ir.*
import io.ktor.openapi.ir.CallAnnotateTransformer.Companion.OPENAPI_PACKAGE
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val ResponsesGenerator = IrAnnotateExpressionGenerator<RouteField> { fields ->
    try {
        val responseHeaders = fields.filterIsInstance<RouteField.ResponseHeader>()
        val defaultCode = LocalReference.of(200)
        val responsesByCode = fields.filterIsInstance<RouteField.Response>().groupBy {
            it.code ?: defaultCode
        }

        +callFunctionWithScope("responses") {
            for ((code, responses) in responsesByCode) {
                try {
                    val statusCode = code.evaluate()
                    +when (statusCode.type.classOrNull?.owner?.name?.asString()) {
                        "HttpStatusCode" -> callFunctionWithScope("invoke", code.evaluate()) {
                            generateResponsesForStatusCode(responses, responseHeaders)
                        }

                        "Int" -> callFunctionWithScope("response", code.evaluate()) {
                            generateResponsesForStatusCode(responses, responseHeaders)
                        }

                        else -> error {
                            "Expected Int or HttpStatusCode for status code, but got ${statusCode.type}"
                        }
                    }
                } catch (e: Throwable) {
                    contextOf<Logger>().log("Failed to generate response for $code", e)
                }
            }
        }
    } catch (e: Throwable) {
        contextOf<Logger>().log("Failed to generate responses", e)
    }
}

context(context: LambdaBuilderContext)
private fun generateResponsesForStatusCode(
    responses: List<RouteField.Response>,
    responseHeaders: List<RouteField.ResponseHeader>,
) {
    // TODO assign headers to appropriate responses
    //      this will require some complexity in the code inference
    if (responseHeaders.isNotEmpty()) {
        +callFunctionWithScope("headers") {
            for (header in responseHeaders) {
                +callFunctionWithScope("header", header.name.evaluate()) {
                    assignProperty("description", header.description)
                    when(header.typeReference) {
                        null -> contentTextPlain()
                        else -> assignSchemaProperty(header.typeReference, header.schemaAttributes)
                    }
                }
            }
        }
    }
    for (response in responses) {
        generateMediaTypeContent(response)
        generateExtensionProperties(response)
    }
}