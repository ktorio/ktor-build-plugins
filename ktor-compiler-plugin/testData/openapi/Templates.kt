// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.server.application.Application
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeString

const val ROOT = "/api/v1"
const val REPORT = "report"
const val ID = "id"
const val VIEW = "view"

fun Application.installTemplates() {
    val reportService = ReportService()

    routing {
        route(ROOT) {
            /**
             * Get report by ID.
             * @path report-id [Long] Report ID.
             *   minimum: 1
             *   maximum: 999999999999
             *   required: true
             * @response 200 application/json :[Number] Report details.
             */
            get("${EntityTypes.REPORTS}/{$REPORT-$ID}") {
                val view = call.request.headers.get("x-$VIEW")
                val report = reportService.getReport(
                    call.parameters["$REPORT-$ID"]!!.toLong()
                ).let { report ->
                    when(view) {
                        "NO-MULLIGANS" -> report.filterKeys { it != "mulligans" }
                        else -> report
                    }
                }
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

object EntityTypes {
    const val REPORTS = "reports"
}

class ReportService {
    fun getReport(id: Long): Map<String, Number> = emptyMap()
}

/* GENERATED_FIR_TAGS: const, funWithExtensionReceiver, functionDeclaration, interfaceDeclaration, objectDeclaration,
propertyDeclaration */
