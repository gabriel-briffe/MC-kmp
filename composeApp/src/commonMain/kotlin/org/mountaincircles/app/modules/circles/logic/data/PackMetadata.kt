package org.mountaincircles.app.modules.circles.logic.data

import kotlinx.serialization.Serializable

/**
 * Metadata from a circles pack configuration
 */
@Serializable
data class PackMetadata(
    val policy: String,
    val config: String,
    val prefix: String,
    val expected: List<String> = emptyList(),
    val ld_ratio: String? = null,
    val elevation_interval: Int? = null,
    val description: String? = null
)
