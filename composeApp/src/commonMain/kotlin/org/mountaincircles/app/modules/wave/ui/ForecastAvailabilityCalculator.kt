package org.mountaincircles.app.modules.wave.ui

import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.modules.wave.import.logic.WaveImportType

/**
 * Calculator for forecast availability and file counts
 * Extracts common logic from ViewModel to reduce duplication and improve testability
 */
class ForecastAvailabilityCalculator(private val waveModule: WaveModule) {

    /**
     * Get list of available forecast types
     */
    fun getAvailableForecasts(): List<WaveImportType> = WaveImportType.values()
        .filter { waveModule.isWaveForecastAvailable(it) }

    /**
     * Get map of forecast types to total file counts (legacy)
     */
    fun getFileCounts(): Map<WaveImportType, Int> = WaveImportType.values()
        .associateWith { waveModule.getWaveForecastFileCount(it) }

    /**
     * Get map of forecast types to wave file counts (VV files only)
     */
    fun getWaveFileCounts(): Map<WaveImportType, Int> = WaveImportType.values()
        .associateWith { waveModule.getWaveForecastWaveFileCount(it) }

    /**
     * Get map of forecast types to wind file counts (U+V files only)
     */
    fun getWindFileCounts(): Map<WaveImportType, Int> = WaveImportType.values()
        .associateWith { waveModule.getWaveForecastWindFileCount(it) }

    /**
     * Get map of forecast types to wind file counts by region
     */
    fun getWindFileCountsByRegion(): Map<WaveImportType, Map<WindRegion, Int>> = WaveImportType.values()
        .associateWith { waveModule.getWaveForecastWindFileCountByRegion(it) }

    /**
     * Check if a specific forecast type is available
     */
    fun isForecastAvailable(forecastType: WaveImportType): Boolean =
        waveModule.isWaveForecastAvailable(forecastType)

    /**
     * Get file count for a specific forecast type
     */
    fun getFileCount(forecastType: WaveImportType): Int =
        waveModule.getWaveForecastFileCount(forecastType)
}
