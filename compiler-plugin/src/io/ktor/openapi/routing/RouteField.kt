package io.ktor.openapi.routing

import io.ktor.openapi.model.JsonSchema
import io.ktor.openapi.model.JsonType
import kotlinx.serialization.json.JsonElement
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

/**
 * Sealed class representing different KDoc parameters for OpenAPI documentation.
 */
sealed interface RouteField {

    fun merge(other: RouteField): RouteField? =
        if (this::class == other::class) this else null

    sealed interface SchemaHolder : RouteField {
        val schema: SchemaReference?
        val attributes: Map<String, JsonElement>
    }

    sealed interface Content : SchemaHolder {
        val contentType: String?
        val description: String?
    }

    sealed interface Parameter : SchemaHolder {
        val name: String
        val description: String?
        val `in`: String
    }

    /**
     * Built into the OpenAPI JSON structure, so ignored when building the path item.
     */
    sealed interface Transient : RouteField

    data class Method(val method: String) : Transient {
        override fun merge(other: RouteField): RouteField? =
            if (other is Method && method == other.method) this else null
    }

    data class Path(val path: String) : Transient {
        override fun merge(other: RouteField): RouteField? =
            if (other is Path)
                Path("${other.path}/${path.removePrefix("/")}")
            else null
    }

    data class Schema(
        val name: String,
        val schema: JsonSchema
    ) : Transient {
        override fun merge(other: RouteField): RouteField? =
            if (other is Schema && other.name == name) this else null
    }

    /**
     * Associates the endpoint with a tag for grouping.
     *
     * Format: `@tag TagName`
     */
    data class Tag(val name: String) : RouteField {
        override fun merge(other: RouteField): RouteField? =
            if (other is Tag && name == other.name) this else null
    }

    /**
     * Describes a path parameter.
     *
     * Format: `@path name description`
     */
    data class PathParam(
        override val name: String,
        override val schema: SchemaReference? = null,
        override val description: String? = null,
        override val attributes: Map<String, JsonElement> = emptyMap(),
    ) : Parameter {
        override val `in`: String = "path"
        override fun merge(other: RouteField): RouteField? =
            if (other is PathParam && name == other.name) copy(
                schema = schema ?: other.schema,
                description = description ?: other.description,
                attributes = attributes + other.attributes,
            ) else null
    }

    /**
     * Describes a query parameter.
     *
     * Format: `@query name description`
     */
    data class QueryParam(
        override val name: String,
        override val schema: SchemaReference? = null,
        override val description: String? = null,
        override val attributes: Map<String, JsonElement> = emptyMap(),
    ) : Parameter {
        override val `in`: String = "query"
        override fun merge(other: RouteField): RouteField? =
            if (other is QueryParam && name == other.name) copy(
                schema = schema ?: other.schema,
                description = description ?: other.description,
                attributes = attributes + other.attributes,
            ) else null
    }

    /**
     * Describes a header parameter.
     *
     * Format: `@header name description`
     */
    data class Header(
        override val name: String,
        override val schema: SchemaReference? = null,
        override val description: String? = null,
        override val attributes: Map<String, JsonElement> = emptyMap(),
    ) : Parameter {
        override val `in`: String = "header"
        override fun merge(other: RouteField): RouteField? =
            if (other is Header && name == other.name) copy(
                schema = schema ?: other.schema,
                description = description ?: other.description,
            ) else null
    }

    /**
     * Describes a cookie parameter.
     *
     * Format: `@cookie name description`
     */
    data class Cookie(
        override val name: String,
        override val schema: SchemaReference? = null,
        override val description: String? = null,
        override val attributes: Map<String, JsonElement> = emptyMap(),
    ) : Parameter {
        override val `in`: String = "cookie"
        override fun merge(other: RouteField): RouteField? =
            if (other is Cookie && name == other.name) copy(
                schema = schema ?: other.schema,
                description = description ?: other.description,
                attributes = attributes + other.attributes,
            ) else null
    }

    /**
     * Documents the request body type.
     *
     * Format: `@body [Type] description`
     */
    data class Body(
        override val contentType: String? = null,
        override val schema: SchemaReference? = null,
        override val description: String? = null,
        override val attributes: Map<String, JsonElement> = emptyMap(),
    ) : Content {
        override fun merge(other: RouteField): RouteField? =
            if (other is Body) copy(
                contentType = contentType ?: other.contentType,
                schema = schema ?: other.schema,
                description = description ?: other.description,
                attributes = attributes + other.attributes,
            ) else null
    }

    /**
     * Documents a response code with optional type and description.
     *
     * Format: `@response code [Type] description`
     */
    data class Response(
        val code: String,
        override val contentType: String? = null,
        override val schema: SchemaReference? = null,
        override val description: String? = null,
        override val attributes: Map<String, JsonElement> = emptyMap(),
    ) : Content {
        override fun merge(other: RouteField): RouteField? =
            if (other is Response && code == other.code)
                copy(
                    contentType = contentType ?: other.contentType,
                    schema = schema ?: other.schema,
                    description = description ?: other.description,
                    attributes = attributes + other.attributes,
                )
            else null
    }

    /**
     * Marks an endpoint as deprecated.
     *
     * Format: `@deprecated reason`
     */
    data class Deprecated(val reason: String) : RouteField

    /**
     * Provides a detailed endpoint description.
     *
     * Format: `@description text`
     */
    data class Description(val text: String) : RouteField


    /**
     * Provides a summary of the endpoint.
     * Format: `@summary text`
     */
    data class Summary(val text: String) : RouteField

    /**
     * Documents security requirements.
     *
     * Format: `@security scheme`
     */
    data class Security(val scheme: String) : RouteField {
        override fun merge(other: RouteField): RouteField? =
            if (other is Security && scheme == other.scheme) this else null
    }
}

typealias RouteFieldList = List<RouteField>

val RouteFieldList.path: String?
    get() = firstIsInstanceOrNull<RouteField.Path>()?.path

val RouteFieldList.method: String?
    get() = firstIsInstanceOrNull<RouteField.Method>()?.method

/**
 * Merges two lists of route fields into a new list.
 */
fun RouteFieldList.merge(other: RouteFieldList) = buildList {
    val otherMutable = other.toMutableList()
    for (field in this@merge) {
        val match = otherMutable.indices.firstNotNullOfOrNull { i ->
            field.merge(otherMutable[i])?.also {
                otherMutable.removeAt(i)
                add(it)
            }
        }
        // if no compatible field was found, add this field to the result
        if (match == null) add(field)
    }
    // add all remaining unmerged fields to the result
    // TODO ignore all summary fields because we currently have some false positives
    addAll(otherMutable.filterNot { it is RouteField.Summary })
}

sealed interface SchemaReference {

    fun asSchema(): JsonSchema

    data class Resolved(
        val schema: JsonSchema
    ) : SchemaReference {
        override fun asSchema(): JsonSchema = schema
    }

    sealed interface Link: SchemaReference {
        val name: String

        data class Simple(override val name: String, val jsonType: JsonType) : Link {
            override fun asSchema(): JsonSchema =
                JsonSchema(jsonType)
        }
        data class Reference(override val name: String) : Link {
            override fun asSchema(): JsonSchema =
                JsonSchema(ref = "#/components/schemas/$name")
        }
        data class Array(val element: Link) : Link by element {
            override fun asSchema(): JsonSchema =
                JsonSchema(
                    type = JsonType.array,
                    items = element.asSchema()
                )
        }
        data class Optional(val delegate: Link) : Link by delegate {
            override fun asSchema(): JsonSchema =
                delegate.asSchema()
                    .copy(required = false)
        }
    }
}

fun SchemaReference.hasReference(): Boolean = getReference() != null

fun SchemaReference.getReference(): String? = when(this) {
    is SchemaReference.Resolved -> schema.ref?.substringAfterLast('/')
    is SchemaReference.Link.Array -> element.getReference()
    is SchemaReference.Link.Optional -> delegate.getReference()
    is SchemaReference.Link.Reference -> name
    is SchemaReference.Link.Simple -> null
}