package org.mountaincircles.app.modules.maps.ui

import org.mountaincircles.app.modules.maps.logic.data.MapSource

/**
 * UI state for the Maps import section
 * Defines all state needed by the Maps import composable
 */
data class MapsImportUiState(
    val availableMaps: List<MapSource> = emptyList(),
    val installedMaps: Set<String> = emptySet(),
    val downloadingMaps: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: MapsError? = null
)
