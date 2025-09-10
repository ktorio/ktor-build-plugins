package io.ktor.openapi.routing

enum class ContentType(val value: String) {
    JSON("application/json"),
    XML("application/xml"),
    YAML("application/yaml"),
    PROTOBUF("application/x-protobuf"),
    OTHER("application/octet-stream")
}