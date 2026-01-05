package io.ktor.openapi.model

import io.ktor.compiler.utils.*
import io.ktor.openapi.routing.*
import io.ktor.openapi.routing.resolveType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
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
        val ContextualClassId = ClassId(
            FqName("kotlinx.serialization"),
            Name.identifier("Contextual")
        )

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
                null -> sequenceOf(classId.shortClassName.asString() to schemaDefinitionForType(this, setOf(classId)))
                else -> emptySequence()
            }
        }

        context(context: RouteStack)
        private fun ConeKotlinType.resolveToClassLike(visited: Set<String> = emptySet()): ConeClassLikeType? {
            if (this is ConeClassLikeType) return this

            val key = toString()
            if (key in visited) return null

            val next = when (this) {
                is ConeFlexibleType -> lowerBound
                is ConeDefinitelyNotNullType -> original
                is ConeIntersectionType -> intersectedTypes.firstOrNull()
                else -> resolveType()
            }

            return next?.resolveToClassLike(visited + key)
        }

        context(context: RouteStack)
        fun ConeKotlinType.asJsonSchema(fullSchema: Boolean = true, visited: Set<ClassId> = emptySet()): JsonSchema {
            val unwrapped = resolveToClassLike() ?: return JsonSchema()

            val classId = unwrapped.lookupTag.classId
            if (classId in visited) {
                return JsonSchema(ref = "#/components/schemas/${classId.shortClassName.asString()}")
            }

            return when(val jsonType = classId.toJsonType()) {
                JsonType.array -> JsonSchema(
                    type = JsonType.array,
                    items = unwrapped.typeArguments.first().type?.asJsonSchema(fullSchema, visited)
                )
                JsonType.`object` -> {
                    when(classId) {
                        StandardClassIds.Map, StandardClassIds.MutableMap ->
                            JsonSchema(
                                type = JsonType.`object`,
                                additionalProperties = unwrapped.typeArguments.last().type?.asJsonSchema(fullSchema, visited)
                            )
                        else -> schemaDefinitionForType(unwrapped, visited + classId)
                    }
                }
                null -> {
                    if (fullSchema) schemaDefinitionForType(unwrapped, visited + classId)
                    else JsonSchema(ref = "#/components/schemas/${classId.shortClassName.asString()}")
                }
                JsonType.any -> JsonSchema()
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
        private fun schemaDefinitionForType(coneType: ConeClassLikeType, visited: Set<ClassId>): JsonSchema {
            return JsonSchema(
                type = JsonType.`object`,
                properties = getAllPropertiesFromType(coneType)
                    .associate {
                        val propertyName = it.name.asString()
                        val propertySchema = if (it.getAnnotationByClassId(ContextualClassId, context.session) != null) {
                            // For @Contextual properties, generate schema based on the underlying type
                            // but don't recursively process it to avoid infinite recursion
                            it.resolvedReturnType.asContextualJsonSchema()
                        } else {
                            it.resolvedReturnType.asJsonSchema(visited = visited)
                        }
                        propertyName to propertySchema
                    }
            )
        }

        context(context: RouteStack)
        private fun ConeKotlinType.asContextualJsonSchema(): JsonSchema {
            val unwrapped = resolveToClassLike() ?: return JsonSchema()
            val classId = unwrapped.lookupTag.classId

            // Known primitive-like types
            val jsonType = classId.toJsonType()
            when (jsonType) {
                JsonType.array -> return JsonSchema(type = JsonType.array, items = JsonSchema())
                JsonType.`object` -> {
                    // Treat maps/objects as open object with arbitrary values
                    if (classId == StandardClassIds.Map || classId == StandardClassIds.MutableMap) {
                        return JsonSchema(type = JsonType.`object`, additionalProperties = JsonSchema())
                    }
                    // Fall through for custom objects
                }

                null -> { /* continue */ }

                else -> return JsonSchema(type = jsonType)
            }

            // Contextual well-known types
            val fqName = classId.asFqNameString()
            return when (fqName) {
                "kotlin.time.Instant",
                "java.time.LocalDate" -> JsonSchema(type = JsonType.string, format = "date")
                "java.time.LocalTime", "java.time.OffsetTime" -> JsonSchema(type = JsonType.string, format = "time")
                "java.time.LocalDateTime",
                "java.time.OffsetDateTime",
                "java.time.ZonedDateTime",
                "java.time.Instant" -> JsonSchema(type = JsonType.string, format = "date-time")

                "kotlin.time.Duration",
                "java.time.Duration" -> JsonSchema(type = JsonType.string, format = "duration")

                "kotlinx.datetime.LocalDate" -> JsonSchema(type = JsonType.string, format = "date")
                "kotlinx.datetime.LocalTime" -> JsonSchema(type = JsonType.string, format = "time")
                "kotlinx.datetime.LocalDateTime",
                "kotlinx.datetime.Instant" -> JsonSchema(type = JsonType.string, format = "date-time")

                "kotlin.uuid.Uuid",
                "java.util.UUID" -> JsonSchema(type = JsonType.string, format = "uuid")

                // Unknown contextual: safest is "any"
                else -> JsonSchema()
            }
        }
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