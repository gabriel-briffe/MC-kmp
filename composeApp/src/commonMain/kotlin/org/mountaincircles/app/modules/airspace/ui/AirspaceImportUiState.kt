package org.mountaincircles.app.modules.airspace.ui

/**
 * UI state for the Airspace import section.
 * This data class holds all the necessary information to render the Airspace import UI.
 */
data class AirspaceImportUiState(
    val selectedCountries: List<String> = emptyList(),
    val hasData: Boolean = false,
    val importedAt: Long? = null,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val isDownloading: Boolean = false
)
