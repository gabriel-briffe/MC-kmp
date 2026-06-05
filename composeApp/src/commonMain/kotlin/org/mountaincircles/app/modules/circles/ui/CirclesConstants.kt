package org.mountaincircles.app.modules.circles.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centralized constants for Circles UI components
 * Extracted to improve maintainability and consistency
 */
object CirclesConstants {

    // Layout Spacing
    val PACK_ITEM_SPACING = 8.dp
    val MAIN_LAYOUT_PADDING = 16.dp
    val MAIN_LAYOUT_SPACING = 16.dp
    val STATUS_PADDING_VERTICAL = 8.dp
    val CARD_PADDING = 16.dp
    val SUBTITLE_TOP_PADDING = 4.dp
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

    // Semantic Colors
    val ACTIVE_PACK_ICON_COLOR = Color.Green
    val AVAILABLE_PACK_ICON_COLOR = Color.Blue
    val PROGRESS_INDICATOR_COLOR = Color.Blue
    val ERROR_TEXT_COLOR = Color.Red

    // Sizes
    val PROGRESS_INDICATOR_SIZE = 24.dp
    val PROGRESS_STROKE_WIDTH = 2.dp
    val ICON_SIZE = 24.dp
    val DOWNLOAD_ARROW_FONT_SIZE = 18.sp

    // Typography
    val TITLE_FONT_SIZE = 16.sp
    val SUBTITLE_FONT_SIZE = 10.sp
    val ERROR_FONT_SIZE = 14.sp
    val EXPLANATORY_FONT_SIZE = 15.sp
    val EXPLANATORY_LINE_HEIGHT = 20.sp

    // Error Display
    val ERROR_CONTAINER_ALPHA = 0.2f

    // UI Text
    object Text {
        const val PACKS_AVAILABLE = "%d packs available"
        const val DOWNLOADING_CANCEL = "Downloading..."
        const val ACTIVE_PACK = "Active pack - Long press to delete"
        const val TAP_TO_SELECT = "Tap to select - Long press to delete"
        const val TAP_TO_DOWNLOAD = "Tap to download"
        const val DOWNLOAD_ARROW = "↓"
        const val IMPORT_ZIP = "Import Circles Pack (ZIP)"
        const val CLEAR_ALL_PACKS = "Clear All Packs"

        // Explanatory text from original implementation
        const val EXPLANATORY = "Alps 20-100-250 stands for:\nglide ratio 20\n100m above the passes\n250m circuit height\n\ncircles can be displayed as combined over the computed area, or around a single airfield. To switch between single and combined, click at the airfield location while the circle submenu is open and circles are visible."
    }

    // Content Descriptions
    object ContentDescriptions {
        const val AVAILABLE_PACK = "Available Pack"
    }
}
