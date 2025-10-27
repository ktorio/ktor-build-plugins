package io.ktor.openapi

import io.ktor.openapi.model.SpecInfo

data class OpenApiProcessorConfig(
    val enabled: Boolean,
    val info: SpecInfo = SpecInfo("Open API Document", "1.0.0"),
)