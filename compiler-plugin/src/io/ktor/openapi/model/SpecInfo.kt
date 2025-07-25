package io.ktor.openapi.model

import kotlinx.serialization.Serializable

/**
 * Data class containing all OpenAPI specification metadata.
 */
@Serializable
data class SpecInfo(
    /**
     * The title of the API.
     */
    val title: String,
    
    /**
     * The version of the OpenAPI Document.
     */
    val version: String,
    
    /**
     * A short summary of the API.
     */
    val summary: String? = null,
    
    /**
     * A description of the API. CommonMark syntax MAY be used for rich text representation.
     */
    val description: String? = null,
    
    /**
     * A URI for the Terms of Service for the API. This MUST be in the form of a URI.
     */
    val termsOfService: String? = null,
    
    /**
     * The contact information for the exposed API.
     */
    val contact: String? = null,
    
    /**
     * The license information for the exposed API.
     */
    val license: String? = null
)