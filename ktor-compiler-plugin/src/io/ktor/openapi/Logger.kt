package io.ktor.openapi

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

fun interface Logger {
    companion object {
        fun wrap(messageCollector: MessageCollector): Logger = Logger { message, cause, location ->
            messageCollector.report(
                severity = CompilerMessageSeverity.LOGGING,
                message = message,
                location = location,
            )
        }
    }

    fun log(message: String) = log(message, cause = null, location = null)
    fun log(message: String, cause: Throwable?) = log(message, cause, location = null)
    fun log(message: String, cause: Throwable?, location: CompilerMessageLocation?)
}