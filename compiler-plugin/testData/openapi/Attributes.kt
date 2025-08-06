// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.server.application.Application
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeString

fun Application.routingWithAttributes(reportService: ReportService) {
    routing {
        route("/api") {
            /**
             * Get report by ID.
             * @path id [Long] Report ID.
             *   minimum: 1
             *   maximum: 999999999999
             *   required: true
             * @response 200 application/json [Map]<[String], [Number]> Report details.
             */
            get("/report/{id}") {
                val report = reportService.getReport(call.parameters["id"]!!.toLong())
                call.respondBytesWriter {
                    writeByte('{'.code.toByte())
                    var i = 0
                    for ((k, v) in report) {
                        if (i++ > 0)
                            writeByte(','.code.toByte())
                        writeString("\"$k\":$v")
                    }
                    writeByte('}'.code.toByte())
                }
            }
        }
    }
}

interface ReportService {
    fun getReport(id: Long): Map<String, Number>
}