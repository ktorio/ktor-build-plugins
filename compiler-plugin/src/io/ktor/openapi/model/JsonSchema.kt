package io.ktor.openapi.model

import io.ktor.compiler.utils.*
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.resolveType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

@Serializable
data class JsonSchema(
    val type: JsonType? = null,
    val properties: Map<String, JsonSchema>? = null,
    @SerialName($$"$ref")
    val ref: String? = null,
    val items: JsonSchema? = null,
    val required: Boolean? = null,
    val additionalProperties: JsonSchema? = null,
    val format: String? = null,
) {
    companion object {
        val String = JsonSchema(type = JsonType.string)
        val Binary = JsonSchema(type = JsonType.string, format = "binary")
        val StringObject = JsonObject(mapOf("type" to JsonPrimitive("string")))

        context(context: RouteStack)
        fun ConeKotlinType.findSchemaDefinitions(): Sequence<Pair<String, JsonSchema>> {
            if (this !is ConeClassLikeType)
                return emptySequence()

            val classId = lookupTag.classId
            return when (classId.toJsonType()) {
                JsonType.array, JsonType.`object` ->
                    typeArguments.asSequence().flatMap { typeProjection ->
                        typeProjection.resolveType()?.findSchemaDefinitions() ?: emptySequence()
                    }
                null -> sequenceOf(classId.shortClassName.asString() to schemaDefinitionForType(this))
                else -> emptySequence()
            }
        }

        context(context: RouteStack)
        fun ConeKotlinType.asJsonSchema(fullSchema: Boolean = true): JsonSchema {
            if (this !is ConeClassLikeType) {
                return resolveType()?.asJsonSchema(fullSchema) ?: JsonSchema()
            }

            return when(val jsonType = lookupTag.classId.toJsonType()) {
                JsonType.array -> JsonSchema(
                    type = JsonType.array,
                    items = typeArguments.first().type?.asJsonSchema(fullSchema)
                )
                JsonType.`object` -> {
                    when(lookupTag.classId) {
                        StandardClassIds.Map, StandardClassIds.MutableMap ->
                            JsonSchema(
                                type = JsonType.`object`,
                                additionalProperties = typeArguments.last().type?.asJsonSchema(fullSchema)
                            )
                        else -> schemaDefinitionForType(this)
                    }
                }
                null -> {
                    if (fullSchema) schemaDefinitionForType(this)
                    else JsonSchema(ref = "#/components/schemas/${this.lookupTag.classId.shortClassName.asString()}")
                }
                else -> JsonSchema(jsonType)
            }
        }

        fun JsonSchema.asReference(name: String): JsonSchema =
            when (type) {
                JsonType.`object` -> JsonSchema(ref = "#/components/schemas/$name")
                JsonType.array -> copy(items = items?.asReference(name))
                else -> this
            }

        context(context: RouteStack)
        private fun schemaDefinitionForType(coneType: ConeClassLikeType): JsonSchema = JsonSchema(
            type = JsonType.`object`,
            properties = getAllPropertiesFromType(coneType)
                .associate {
                    it.name.asString() to it.resolvedReturnType.asJsonSchema()
                }
        )
    }
}

@Serializable
@Suppress("EnumEntryName")
enum class JsonType {
    string,
    number,
    integer,
    boolean,
    array,
    `object`,
    any
}

fun findJsonPrimitiveType(name: String): JsonType? {
    val classId = ClassId(
        StandardClassIds.BASE_KOTLIN_PACKAGE,
        Name.identifier(name)
    )
    return classId.toJsonType()
}

fun ClassId.toJsonType(): JsonType? =
    when(this) {
        // Integer types
        StandardClassIds.Int,
        StandardClassIds.UInt,
        StandardClassIds.Short,
        StandardClassIds.UShort,
        StandardClassIds.Byte,
        StandardClassIds.UByte,
        StandardClassIds.Long,
        StandardClassIds.ULong ->
            JsonType.integer

        // Number types
        StandardClassIds.Float,
        StandardClassIds.Double,
        StandardClassIds.Number ->
            JsonType.number

        // Boolean type
        StandardClassIds.Boolean ->
            JsonType.boolean

        // String types
        StandardClassIds.String,
        StandardClassIds.Char ->
            JsonType.string

        // Array types
        StandardClassIds.Array,
        StandardClassIds.List,
        StandardClassIds.Collection,
        StandardClassIds.Set,
        StandardClassIds.MutableList,
        StandardClassIds.MutableCollection,
        StandardClassIds.MutableSet,
        StandardClassIds.Iterable ->
            JsonType.array

        // Map type
        StandardClassIds.Map,
        StandardClassIds.MutableMap ->
            JsonType.`object`

        // Any, Nothing, Unit types
        StandardClassIds.Any,
        StandardClassIds.Nothing,
        StandardClassIds.Unit ->
            JsonType.any

        // For all other classes, treat as custom object schema
        else -> null
    }