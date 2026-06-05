package org.mountaincircles.app.modules.airspace.logic.data

import org.mountaincircles.app.modules.ModuleState
import org.mountaincircles.app.ui.map.MapClickEvent

/**
 * Minimal state for the airspace module
 * Will be expanded as we implement the logic
 */
data class AirspaceState(
    override val isInitialized: Boolean = false,
    override val hasError: Boolean = false,
    override val errorMessage: String? = null,
    override val hasDataToRender: Boolean? = null,
    val isDownloading: Boolean = false,
    val currentProgress: AirspaceProgress? = null,
    val selectedCountries: List<String> = AirspaceSources.defaultSelectedCountries,
    val availableTypes: Set<String> = emptySet(), // Types actually present in current data
    val currentVisibleTypes: Set<String> = emptySet(), // Types currently visible to user (empty = none visible)
    val isVisible: Boolean = false, // Controls airspace layer visibility
    val showPopup: Boolean = false, // Controls airspace popup overlay visibility
    val popupFeatures: List<AirspaceFeatureData> = emptyList(), // Features to display in popup
    val importedAt: Long? = null, // Timestamp when airspace data was last imported
) : ModuleState()

/**
 * Feature data for airspace popup display
 */
data class AirspaceFeatureData(
    val id: String, // Feature ID from properties.AI
    val name: String,
    val type: String,
    val upperLimit: String,
    val lowerLimit: String,
    val frequency: String = "", // Optional frequency information
    val allProperties: Map<String, String> = emptyMap() // All feature properties
)

/**
 * Progress information for airspace import operations
 */
data class AirspaceProgress(
    val current: Int, // Current file number (1-based)
    val total: Int,   // Total files
    val status: String, // Status message
    val percent: Int = -1 // Overall completion percentage
)
