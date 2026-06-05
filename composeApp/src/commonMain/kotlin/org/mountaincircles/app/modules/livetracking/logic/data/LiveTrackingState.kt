package org.mountaincircles.app.modules.livetracking.logic.data

import org.mountaincircles.app.modules.ModuleState
import kotlinx.serialization.Serializable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import kotlinx.serialization.json.JsonObject

/**
 * Visibility modes for LiveTracking aircraft display
 */
enum class LiveTrackingVisibilityMode {
    ALL_VISIBLE,    // Show all aircraft
    FRIENDS_ONLY,   // Show only friendlisted aircraft
    ALL_HIDDEN      // Hide all aircraft
}

/**
 * State for the Live Tracking module
 */
data class LiveTrackingState(
    override val isInitialized: Boolean = false,
    override val hasError: Boolean = false,
    override val errorMessage: String? = null,
    override val hasDataToRender: Boolean? = null,
    val visibilityMode: LiveTrackingVisibilityMode = LiveTrackingVisibilityMode.ALL_VISIBLE,  // Live tracking hidden by default
    val aircraftFeatures: Map<String, Feature<Point, JsonObject>> = emptyMap(),  // Map of deviceId -> Feature for aircraft positions
    val lastPolledBoundaries: PolledBoundaries? = null,  // Last polled geographic boundaries
    val lastUpdateTimestamp: Long = 0L,  // Timestamp of last aircraft update (milliseconds)
    val showPopup: Boolean = false,  // Controls aircraft popup overlay visibility
    val popupDeviceId: String? = null,  // Device ID of aircraft to show in popup (null = no popup)
    val friendlist: List<FriendEntry> = emptyList()  // Friend entries with device IDs and custom names
) : ModuleState()

/**
 * Geographic boundaries of the last aircraft polling request
 */
data class PolledBoundaries(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

/**
 * Layer data for LiveTracking composables
 */
data class LiveTrackingLayerData(
    val visibilityMode: LiveTrackingVisibilityMode
)

/**
 * Friend entry with device ID and custom name
 */
@Serializable
data class FriendEntry(
    val deviceId: String,
    val customName: String,
    val registration: String = "",
    val registrationShort: String = ""
)

/**
 * Aircraft feature data for popup display
 */
data class AircraftFeatureData(
    val deviceId: String,  // Device ID for identification (required)
    val properties: Map<String, String>  // All aircraft properties as key-value pairs (including registration, registrationShort, etc.)
)
