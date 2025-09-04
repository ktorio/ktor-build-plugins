package io.ktor.openapi.routing.interpreters

import io.ktor.openapi.routing.*
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirEvaluatorResult
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.text

/**
 * Attempt to evaluate the header expression as a string literal, or try to lookup a common constant.
 *
 * Note, this ugly lookup will be removed in the next iteration.
 */
context(context: RouteStack, checker: CheckerContext, reporter: DiagnosticReporter)
fun FirExpression.evaluateHeader(): String? =
    when(val evaluationResult = (evaluate() as? FirEvaluatorResult.Evaluated)?.result) {
        is FirLiteralExpression -> evaluationResult.value?.toString()
        else -> {
            when(source.text.toString().substringAfter('.')) {
                "Accept" -> "Accept"
                "AcceptCharset" -> "Accept-Charset"
                "AcceptEncoding" -> "Accept-Encoding"
                "AcceptLanguage" -> "Accept-Language"
                "AcceptRanges" -> "Accept-Ranges"
                "Age" -> "Age"
                "Allow" -> "Allow"

                // Application-Layer Protocol Negotiation, HTTP/2
                "ALPN" -> "ALPN"
                "AuthenticationInfo" -> "Authentication-Info"
                "Authorization" -> "Authorization"
                "CacheControl" -> "Cache-Control"
                "Connection" -> "Connection"
                "ContentDisposition" -> "Content-Disposition"
                "ContentEncoding" -> "Content-Encoding"
                "ContentLanguage" -> "Content-Language"
                "ContentLength" -> "Content-Length"
                "ContentLocation" -> "Content-Location"
                "ContentRange" -> "Content-Range"
                "ContentType" -> "Content-Type"
                "Cookie" -> "Cookie"

                // WebDAV Search
                "DASL" -> "DASL"
                "Date" -> "Date"

                // WebDAV
                "DAV" -> "DAV"
                "Depth" -> "Depth"

                "Destination" -> "Destination"
                "ETag" -> "ETag"
                "Expect" -> "Expect"
                "Expires" -> "Expires"
                "From" -> "From"
                "Forwarded" -> "Forwarded"
                "Host" -> "Host"
                "HTTP2Settings" -> "HTTP2-Settings"
                "If" -> "If"
                "IfMatch" -> "If-Match"
                "IfModifiedSince" -> "If-Modified-Since"
                "IfNoneMatch" -> "If-None-Match"
                "IfRange" -> "If-Range"
                "IfScheduleTagMatch" -> "If-Schedule-Tag-Match"
                "IfUnmodifiedSince" -> "If-Unmodified-Since"
                "LastModified" -> "Last-Modified"
                "Location" -> "Location"
                "LockToken" -> "Lock-Token"
                "Link" -> "Link"
                "MaxForwards" -> "Max-Forwards"
                "MIMEVersion" -> "MIME-Version"
                "OrderingType" -> "Ordering-Type"
                "Origin" -> "Origin"
                "Overwrite" -> "Overwrite"
                "Position" -> "Position"
                "Pragma" -> "Pragma"
                "Prefer" -> "Prefer"
                "PreferenceApplied" -> "Preference-Applied"
                "ProxyAuthenticate" -> "Proxy-Authenticate"
                "ProxyAuthenticationInfo" -> "Proxy-Authentication-Info"
                "ProxyAuthorization" -> "Proxy-Authorization"
                "PublicKeyPins" -> "Public-Key-Pins"
                "PublicKeyPinsReportOnly" -> "Public-Key-Pins-Report-Only"
                "Range" -> "Range"
                "Referrer" -> "Referer"
                "RetryAfter" -> "Retry-After"
                "ScheduleReply" -> "Schedule-Reply"
                "ScheduleTag" -> "Schedule-Tag"
                "SecWebSocketAccept" -> "Sec-WebSocket-Accept"
                "SecWebSocketExtensions" -> "Sec-WebSocket-Extensions"
                "SecWebSocketKey" -> "Sec-WebSocket-Key"
                "SecWebSocketProtocol" -> "Sec-WebSocket-Protocol"
                "SecWebSocketVersion" -> "Sec-WebSocket-Version"
                "Server" -> "Server"
                "SetCookie" -> "Set-Cookie"

                // Atom Publishing
                "SLUG" -> "SLUG"
                "StrictTransportSecurity" -> "Strict-Transport-Security"
                "TE" -> "TE"
                "Timeout" -> "Timeout"
                "Trailer" -> "Trailer"
                "TransferEncoding" -> "Transfer-Encoding"
                "Upgrade" -> "Upgrade"
                "UserAgent" -> "User-Agent"
                "Vary" -> "Vary"
                "Via" -> "Via"
                "Warning" -> "Warning"
                "WWWAuthenticate" -> "WWW-Authenticate"

                // CORS
                "AccessControlAllowOrigin" -> "Access-Control-Allow-Origin"
                "AccessControlAllowMethods" -> "Access-Control-Allow-Methods"
                "AccessControlAllowCredentials" -> "Access-Control-Allow-Credentials"
                "AccessControlAllowHeaders" -> "Access-Control-Allow-Headers"
                "AccessControlRequestMethod" -> "Access-Control-Request-Method"
                "AccessControlRequestHeaders" -> "Access-Control-Request-Headers"
                "AccessControlExposeHeaders" -> "Access-Control-Expose-Headers"
                "AccessControlMaxAge" -> "Access-Control-Max-Age"

                // Unofficial de-facto headers
                "XHttpMethodOverride" -> "X-Http-Method-Override"
                "XForwardedHost" -> "X-Forwarded-Host"
                "XForwardedServer" -> "X-Forwarded-Server"
                "XForwardedProto" -> "X-Forwarded-Proto"
                "XForwardedFor" -> "X-Forwarded-For"
                "XForwardedPort" -> "X-Forwarded-Port"
                "XRequestId" -> "X-Request-ID"
                "XCorrelationId" -> "X-Correlation-ID"
                "XTotalCount" -> "X-Total-Count"

                else -> null
            }
        }
    }