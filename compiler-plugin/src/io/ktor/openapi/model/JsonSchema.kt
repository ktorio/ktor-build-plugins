package io.ktor.openapi.model

import io.ktor.compiler.utils.*
import io.ktor.openapi.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
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
) {
    companion object {
        val String = JsonObject(mapOf("type" to JsonPrimitive("string")))

        context(context: RouteStack)
        fun findSchemaDefinitions(coneType: ConeKotlinType): Sequence<Pair<String, JsonSchema>> {
            if (coneType !is ConeClassLikeType)
                return emptySequence()

            val classId = coneType.lookupTag.classId
            return when (findStandardJsonType(classId)) {
                JsonType.array, JsonType.`object` ->
                    coneType.typeArguments.asSequence().flatMap { typeArg ->
                        // TODO handle generics
                        resolveTypeArg(typeArg)?.let {
                            findSchemaDefinitions(it)
                        } ?: emptySequence()
                    }
                null -> sequenceOf(classId.shortClassName.asString() to schemaDefinitionForType(coneType))
                else -> emptySequence()
            }
        }

        context(context: RouteStack)
        private fun resolveTypeArg(arg: ConeTypeProjection): ConeKotlinType? {
            return arg.resolveType()
        }

        context(context: RouteStack)
        fun schemaFromConeType(coneType: ConeKotlinType, expand: Boolean = true): JsonSchema {
            if (coneType !is ConeClassLikeType) {
                return coneType.resolveType()?.let { resolvedType ->
                    schemaFromConeType(resolvedType, expand)
                } ?: JsonSchema(type = JsonType.any)
            }

            return when(val jsonType = findStandardJsonType(coneType.lookupTag.classId)) {
                JsonType.array -> JsonSchema(
                    type = JsonType.array,
                    items = coneType.typeArguments.first().type?.let {
                        schemaFromConeType(it, expand)
                    }
                )
                null -> {
                    if (expand) schemaDefinitionForType(coneType)
                    else JsonSchema(ref = "#/components/schemas/${coneType.lookupTag.classId.shortClassName.asString()}")
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
                    it.name.asString() to schemaFromConeType(it.resolvedReturnType)
                }
        )
    }
}

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
    return findStandardJsonType(classId)
}

fun findStandardJsonType(classId: ClassId): JsonType? =
    when(classId) {
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
        StandardClassIds.Double ->
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