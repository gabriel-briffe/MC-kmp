package org.mountaincircles.app.modules.airports.logic.data

/**
 * Simple progress information for airports import operations
 * Just tracks file completion (1/2, 2/2) without byte-level details
 */
data class AirportsProgress(
    val current: Int, // Current file number (1-based)
    val total: Int,   // Total files
    val status: String, // Status message
    val percent: Int = -1 // Overall completion percentage
)