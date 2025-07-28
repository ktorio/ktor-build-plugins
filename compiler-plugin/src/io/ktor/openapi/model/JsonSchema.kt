package io.ktor.openapi.model

import io.ktor.compiler.utils.getAllPropertiesFromType
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.StandardClassIds

@Serializable
data class JsonSchema(
    val type: String,
    val properties: Map<String, JsonSchema>? = null,
) {
    companion object {
        fun CheckerContext.schemaFromConeType(coneType: ConeKotlinType): JsonSchema {
            if (coneType !is ConeClassLikeType)
                return JsonSchema("any")

            return when(coneType.lookupTag.classId) {
                // Integer types
                StandardClassIds.Int,
                StandardClassIds.UInt,
                StandardClassIds.Short,
                StandardClassIds.UShort,
                StandardClassIds.Byte,
                StandardClassIds.UByte,
                StandardClassIds.Long,
                StandardClassIds.ULong ->
                    JsonSchema("integer")

                // Number types
                StandardClassIds.Float,
                StandardClassIds.Double ->
                    JsonSchema("number")

                // Boolean type
                StandardClassIds.Boolean ->
                    JsonSchema("boolean")

                // String types
                StandardClassIds.String,
                StandardClassIds.Char ->
                    JsonSchema("string")

                // Array types
                StandardClassIds.Array,
                StandardClassIds.List,
                StandardClassIds.Collection,
                StandardClassIds.Set,
                StandardClassIds.Iterable ->
                    JsonSchema("array") // TODO element types

                // Map type
                StandardClassIds.Map ->
                    JsonSchema("object")

                // Any, Nothing, Unit types
                StandardClassIds.Any,
                StandardClassIds.Nothing,
                StandardClassIds.Unit ->
                    JsonSchema("any")

                // For all other classes, treat as objects with properties

                else -> JsonSchema(
                    type = "object",
                    properties = getAllPropertiesFromType(coneType)
                        .associate {
                            it.name.asString() to schemaFromConeType(it.resolvedReturnType)
                        }
                )
            }
        }
    }
}