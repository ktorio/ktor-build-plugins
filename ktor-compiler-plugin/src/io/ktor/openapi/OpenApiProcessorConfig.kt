package io.ktor.openapi

import io.ktor.openapi.model.SpecInfo

data class OpenApiProcessorConfig(
    val enabled: Boolean,
    val codeInference: Boolean,
    val debug: Boolean,
    val onlyCommented: Boolean = false,
    val info: SpecInfo = SpecInfo("Open API Document", "1.0.0"),
)