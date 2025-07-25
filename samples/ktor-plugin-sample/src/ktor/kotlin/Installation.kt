import io.ktor.server.application.*
import io.ktor.server.websocket.*
import java.time.Duration

fun Application.`ktor-gradle-plugin-sample`() {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}