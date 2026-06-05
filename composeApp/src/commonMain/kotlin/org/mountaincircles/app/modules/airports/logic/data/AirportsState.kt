package org.mountaincircles.app.modules.airports.logic.data

import org.mountaincircles.app.modules.ModuleState

/**
 * Minimal state for the airports module
 * Will be expanded as we implement the logic
 */
data class AirportsState(
    override val isInitialized: Boolean = false,
    override val hasError: Boolean = false,
    override val errorMessage: String? = null,
    override val hasDataToRender: Boolean? = null,
    val isDownloading: Boolean = false,
    val selectedCountries: List<String> = AirportSources.defaultSelectedCountries, // Default to France
    val importedAt: Long? = null, // When data was last imported
    val currentProgress: AirportsProgress? = null, // Current download progress
    val availableTypes: Set<String> = emptySet(), // Types actually present in current data
    val currentVisibleTypes: Set<String> = emptySet(), // Types visible to user (empty = none visible)
    // Settings parameters
    val airportsVisibility: Boolean = true, // Controls airport points and labels visibility
    val airportIconSize: Float = 6.0f, // Airport icon size in dp
    val airportLabelSize: Float = 12.0f, // Airport labels text size in sp
    val airportIconsMinZoom: Float = 6.0f, // Min zoom for airport icons
    val airportLabelsMinZoom: Float = 8.0f, // Min zoom for airport labels
    val showPopup: Boolean = false, // Controls airport popup overlay visibility
    val popupFeatures: List<AirportFeatureData> = emptyList(), // Features to display in popup
    val disabledAirportIds: Set<String> = emptySet() // IDs of disabled airports
) : ModuleState()
