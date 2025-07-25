package io.ktor.samples.plugin

import io.ktor.plugins.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import java.time.Duration

@KtorPluginInstall("ktor-gradle-plugin-sample")
fun Application.install() = {
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}