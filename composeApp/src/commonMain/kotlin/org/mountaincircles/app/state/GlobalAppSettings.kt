package org.mountaincircles.app.state

import kotlinx.serialization.Serializable

/**
 * Global application settings that are persisted across app sessions.
 * Contains north-lock preference and geolocation settings.
 */
@Serializable
data class GlobalAppSettings(
    val northLockEnabled: Boolean = true,
    val circleRadiusKm: Float = 0f,
    val vectorLengthKm: Float = 0f,
    val circleWidthDp: Float = 4f,  // Circle line width in dp
    val vectorWidthDp: Float = 3f,   // Vector line width in dp
    val osmBasemapEnabled: Boolean = true,
    val terrainBasemapEnabled: Boolean = true,
) {
    companion object {
        val DEFAULT = GlobalAppSettings()
    }
}
