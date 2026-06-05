package org.mountaincircles.app.modules.wave.ui

import org.mountaincircles.app.modules.wave.import.logic.WaveImportType

enum class WindRegion {
    NORTH, SOUTH, MIDDLE_EAST, MIDDLE_WEST
}

data class WaveImportUiState(
    val availableForecasts: List<WaveImportType> = emptyList(),
    val downloadedForecasts: Set<WaveImportType> = emptySet(),
    val entriesCount: Int = 0,
    val forecastFileCounts: Map<WaveImportType, Int> = emptyMap(), // Total files (legacy)
    val forecastWaveFileCounts: Map<WaveImportType, Int> = emptyMap(), // VV files only
    val forecastWindFileCounts: Map<WaveImportType, Int> = emptyMap(), // U+V files only (total)
    val forecastWindFileCountsByRegion: Map<WaveImportType, Map<WindRegion, Int>> = emptyMap(), // U+V files by region
    val includeWindFiles: Boolean = false, // Whether to download wind files along with wave files
    val selectedWindRegions: Set<WindRegion> = emptySet(), // Which wind regions to download
    val error: WaveError? = null,
    val activeDownload: WaveImportType? = null
)
