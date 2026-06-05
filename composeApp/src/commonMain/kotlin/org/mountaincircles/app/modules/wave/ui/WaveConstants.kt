package org.mountaincircles.app.modules.wave.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.modules.wave.import.logic.WaveImportType

/**
 * Centralized constants for Wave UI components
 * Extracted to improve maintainability and consistency
 */
object WaveConstants {

    // Forecast Types
    val FORECAST_TYPES = listOf(WaveImportType.TODAY, WaveImportType.TOMORROW, WaveImportType.YESTERDAY_FOR_TODAY)

    // Wave data structure constants (must match WaveImporter)
    val HOUR_LIST = listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
    val ISOBARE_LIST = listOf(500, 600, 700, 800, 900, 1000)

    // Layout Spacing
    val GRID_ITEM_SPACING = 8.dp
    val MAIN_LAYOUT_PADDING = 16.dp
    val MAIN_LAYOUT_SPACING = 16.dp
    val STATUS_PADDING_VERTICAL = 8.dp
    val EXPLANATORY_TEXT_SPACING = 8.dp
    val EXPLANATORY_TEXT_TOP_SPACING = 16.dp

    // Colors
    val DISABLED_CONTAINER_ALPHA = 0.2f
    val DISABLED_CONTENT_ALPHA = 0.5f
    val DISABLED_SUBTITLE_ALPHA = 0.7f
    val AVAILABLE_CONTAINER_ACTIVE_ALPHA = 0.3f
    val AVAILABLE_CONTAINER_IDLE_ALPHA = 0.15f

    val DISABLED_CONTAINER_COLOR = Color.Gray.copy(alpha = DISABLED_CONTAINER_ALPHA)
    val DISABLED_CONTENT_COLOR = Color.Gray.copy(alpha = DISABLED_CONTENT_ALPHA)
    val AVAILABLE_ACTIVE_CONTAINER_COLOR = Color.Green.copy(alpha = AVAILABLE_CONTAINER_ACTIVE_ALPHA)
    val AVAILABLE_IDLE_CONTAINER_COLOR = Color.Green.copy(alpha = AVAILABLE_CONTAINER_IDLE_ALPHA)

    val ACTIVE_BORDER_COLOR = Color.Cyan
    val TRANSPARENT_BORDER = Color.Transparent

    // Sizes
    val PROGRESS_INDICATOR_SIZE = 24.dp
    val PROGRESS_STROKE_WIDTH = 2.dp
    val ICON_SIZE = 24.dp
    val DOWNLOAD_ARROW_FONT_SIZE = 18.sp

    // Border
    val ACTIVE_BORDER_WIDTH = 2.dp
    val INACTIVE_BORDER_WIDTH = 0.dp

    // Typography
    val TITLE_FONT_SIZE = 16.sp
    val SUBTITLE_FONT_SIZE = 10.sp
    val ERROR_FONT_SIZE = 14.sp
    val EXPLANATORY_FONT_SIZE = 15.sp
    val EXPLANATORY_LINE_HEIGHT = 20.sp

    // Padding
    val CARD_PADDING = 12.dp
    val SUBTITLE_TOP_PADDING = 2.dp

    // Error Display
    val ERROR_CONTAINER_ALPHA = 0.2f

    // Forecast Titles
    object Titles {
        const val TODAY = "Today's Forecast"
        const val TOMORROW = "Tomorrow's Forecast"
        const val YESTERDAY_FOR_TODAY = "Yesterday's Forecast for Today"
    }

    // UI Text
    object Text {
        const val FILES_IN_MEMORY = "%d files in memory"
        const val DOWNLOADING_CANCEL = "Downloading... Tap to cancel"
        const val DOWNLOADING = "Downloading..."
        const val FILES_LOADED = "%d files loaded - Tap to re-download"
        const val TAP_TO_DOWNLOAD = "Tap to download"
        const val CLEAR_ALL_FILES = "Clear All Files"
        const val DOWNLOAD_ARROW = "↓"

        // Explanatory text
        const val TIME_UTC = "Time is UTC\ndate below time is the date for which the forecast is valid"
        const val FC_BUTTON = "FC button stands for ForeCast\nDate below FC is the date at which the forecast was computed\n(that is how you make sure you are looking at today's forecast from this morning, or todays forecast from yesterday)"
        const val TIME_BUTTON = "click on the time button to go to the closest hour for the currently selected altitude and currently selected forecast date"
        const val METEO_DATA = "forecast data comes from Meteo France Arome's model, \"vertical velocity\".\nYellow to dark red from +1m/s to +4m/s air mass.\nTurquoise to dark blue from -1m/s to -4m/s air mass"
    }

    // Content Descriptions
    object ContentDescriptions {
        const val AVAILABLE_FORECAST = "Available Forecast"
    }
}
