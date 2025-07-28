package io.ktor.openapi

import io.ktor.openapi.model.SpecInfo

data class OpenApiProcessorConfig(
    val enabled: Boolean,
    val outputFile: String,
    val mainClass: String? = null,
    val info: SpecInfo = SpecInfo("Open API Document", "1.0.0"),
)