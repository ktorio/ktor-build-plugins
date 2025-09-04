package io.ktor.openapi.routing.interpreters

import io.ktor.compiler.utils.*
import io.ktor.openapi.model.*
import io.ktor.openapi.model.JsonSchema.Companion.asJsonSchema
import io.ktor.openapi.model.JsonSchema.Companion.findSchemaDefinitions
import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.text

class CallRespondInterpreter : RoutingCallInterpreter {
    companion object {
        const val HTTP_STATUS_CODE = "HttpStatusCode"
        const val CONTENT_TYPE = "ContentType"
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall): RoutingReferenceResult {
        if (!isCallRespond(expression)) return RoutingReferenceResult.None

        val routeNode = RouteNode.CallFeature(
            filePath = context.containingFilePath,
            fir = expression,
            fields = {
                buildList {
                    val coneType = findResponseBodyArgument(expression)
                    val schema = coneType?.asJsonSchema(fullSchema = false)?.let(SchemaReference::Resolved)
                    val statusCode = getStatusArgument(expression) ?: getStatusFromFunction(expression)
                    val contentType = getContentTypeArgument(expression) ?: getContentTypeFromFunction(expression)
                    add(RouteField.Response(
                        code = statusCode,
                        contentType = contentType,
                        schema = schema ?: contentType?.let(::getSchemaFromContentType)
                    ))
                    coneType?.findSchemaDefinitions()?.forEach { (name, schema) ->
                        add(RouteField.Schema(name, schema))
                    }
                }
            },
        )

        return RoutingReferenceResult.Match(routeNode)
    }

    private fun isCallRespond(call: FirFunctionCall): Boolean =
        call.getFunctionName().startsWith("respond") &&
                call.extensionReceiver?.source?.text == "call" &&
                call.isInPackage("io.ktor.server.response")

    context(context: CheckerContext)
    private fun findResponseBodyArgument(expression: FirFunctionCall): ConeKotlinType? {
        if (expression.getFunctionName() != "respond") return null
        return expression.arguments.firstOrNull {
            it.resolvedType.classId?.shortClassName?.asString() !in setOf(
                "HttpStatusCode",
                "ContentType",
                "Boolean",
            )
        }?.resolvedType
    }

    private fun getStatusArgument(expression: FirFunctionCall): String? =
        expression.arguments.firstOrNull {
            it.resolvedType.classId?.shortClassName?.asString() == HTTP_STATUS_CODE
        }?.toCode()

    private fun getStatusFromFunction(expression: FirFunctionCall): String =
        when(expression.getFunctionName()) {
            "respondRedirect" -> "302"
            else -> "200"
        }

    private fun getContentTypeArgument(expression: FirFunctionCall): String? =
        expression.arguments.firstOrNull {
            it.resolvedType.classId?.shortClassName?.asString() == CONTENT_TYPE
        }?.toContentTypeString()

    private fun getContentTypeFromFunction(expression: FirFunctionCall): String? =
        when(expression.getFunctionName()) {
            "respondText" -> "text/plain"
            "respondBytes" -> "application/octet-stream"
            "respondHtml" -> "text/html"
            else -> null
        }

    private fun getSchemaFromContentType(contentType: String): SchemaReference? =
        if (contentType.startsWith("text"))
            SchemaReference.Resolved(JsonSchema.String)
        else null

    private fun FirExpression.toCode(): String? {
        val expr = this as? FirPropertyAccessExpression ?: return null
        return when (expr.calleeReference.name.asString()) {
            // 1xx
            "Continue" -> "100"
            "SwitchingProtocols" -> "101"
            "Processing" -> "102"

            // 2xx
            "OK" -> "200"
            "Created" -> "201"
            "Accepted" -> "202"
            "NonAuthoritativeInformation" -> "203"
            "NoContent" -> "204"
            "ResetContent" -> "205"
            "PartialContent" -> "206"
            "MultiStatus" -> "207"

            // 3xx
            "MultipleChoices" -> "300"
            "MovedPermanently" -> "301"
            "Found" -> "302"
            "SeeOther" -> "303"
            "NotModified" -> "304"
            "UseProxy" -> "305"
            "SwitchProxy" -> "306"
            "TemporaryRedirect" -> "307"
            "PermanentRedirect" -> "308"

            // 4xx
            "BadRequest" -> "400"
            "Unauthorized" -> "401"
            "PaymentRequired" -> "402"
            "Forbidden" -> "403"
            "NotFound" -> "404"
            "MethodNotAllowed" -> "405"
            "NotAcceptable" -> "406"
            "ProxyAuthenticationRequired" -> "407"
            "RequestTimeout" -> "408"
            "Conflict" -> "409"
            "Gone" -> "410"
            "LengthRequired" -> "411"
            "PreconditionFailed" -> "412"
            "PayloadTooLarge" -> "413"
            "RequestURITooLong" -> "414"
            "UnsupportedMediaType" -> "415"
            "RequestedRangeNotSatisfiable" -> "416"
            "ExpectationFailed" -> "417"
            "UnprocessableEntity" -> "422"
            "Locked" -> "423"
            "FailedDependency" -> "424"
            "TooEarly" -> "425"
            "UpgradeRequired" -> "426"
            "TooManyRequests" -> "429"
            "RequestHeaderFieldTooLarge" -> "431"

            // 5xx
            "InternalServerError" -> "500"
            "NotImplemented" -> "501"
            "BadGateway" -> "502"
            "ServiceUnavailable" -> "503"
            "GatewayTimeout" -> "504"
            "VersionNotSupported" -> "505"
            "VariantAlsoNegotiates" -> "506"
            "InsufficientStorage" -> "507"

            else -> "200"
        }
    }

    /**
     * Very simplified attempt at getting content type string from reference.
     *
     * Note: this will not work for many cases and will need to be replaced with runtime substitution.
     */
    private fun FirExpression.toContentTypeString(): String? {
        val expr = this as? FirPropertyAccessExpression ?: return null
        val text = expr.source.text ?: return null
        return text.removePrefix("$CONTENT_TYPE.").toString()
            .replace('.', '/')
            .lowercase()
    }
}
