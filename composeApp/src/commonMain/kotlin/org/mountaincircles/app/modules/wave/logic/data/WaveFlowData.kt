package org.mountaincircles.app.modules.wave.logic.data

import org.mountaincircles.app.modules.wave.logic.data.WaveSelection
import org.mountaincircles.app.modules.wave.logic.data.WaveProgress

/**
 * Data classes for WaveModule selective reactive flows
 * Extracted from main module for better organization
 */

/**
 * Data class for raster display information
 */
data class RasterData(
    val isVisible: Boolean,
    val opacity: Float,
    val selection: WaveSelection,
    val entriesCount: Int,
    val hasEntries: Boolean
)

/**
 * Data class for navigation capabilities
 */
data class NavigationData(
    val canPrevHour: Boolean,
    val canNextHour: Boolean,
    val canPressureUp: Boolean,
    val canPressureDown: Boolean
)

/**
 * Data class for download progress information
 */
data class ProgressData(
    val isDownloading: Boolean,
    val currentProgress: WaveProgress?
)

/**
 * Data class for time, pressure, and forecast date display
 * Groups related display data that changes together
 */
data class TimeAndPressureDisplayData(
    val hour: Int,
    val targetDate: String,
    val forecastDate: String,
    val pressure: Int
)

/**
 * Data class for layer visibility state
 * Separated from other controls for optimal recomposition
 */
data class LayerVisibilityData(
    val isVisible: Boolean
)

/**
 * Data class for font settings
 * Separated from display data for optimal recomposition
 */
data class FontSettingsData(
    val mainLabelFontSize: Float,
    val subLabelFontSize: Float
)
