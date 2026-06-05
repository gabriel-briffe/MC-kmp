package org.mountaincircles.app.modules.airspace.logic.data

import androidx.compose.ui.graphics.Color
import org.mountaincircles.app.utils.argbColor

/**
 * Centralized color configuration for airspace module.
 * Eliminates duplicate color definitions across multiple files.
 */
object AirspaceColors {

    // ============================================================================
    // AIRSPACE BASE COLORS
    // ============================================================================
    object Base {
        // Dark colors (less dark than before, but still darker than regular colors)
        val DARK_RED = Color(0xFFB22222)        // Firebrick red (lighter than dark red)
        val DARK_GREEN = Color(0xFF228B22)      // Forest green (lighter than dark green)
        val DARK_BLUE = Color(0xFF4169E1)       // Royal blue (lighter than dark blue)

        // Custom colors
        val APPLE_GREEN = Color(0xFF32CD32)     // Lime green (warm palette complement)
        val DARK_PURPLE = Color(0xFF6A5ACD)     // Slate blue (lighter than dark purple)

        // Muted colors (warm palette)
        val MUTED_YELLOW = Color(0xFFFFD700)    // Gold yellow
        val MUTED_ORANGE = Color(0xFFFFA500)    // Orange
    }

    // ============================================================================
    // AIRSPACE TYPE COLORS
    // ============================================================================
    object AirspaceType {
        // ICAO Classes
        val CLASS_A = Base.DARK_RED
        val CLASS_C = Base.DARK_BLUE
        val CLASS_D = Base.DARK_BLUE
        val CLASS_E = Base.APPLE_GREEN
        val CLASS_G = Base.APPLE_GREEN

        // Special Use Airspace
        val PROHIBITED = Base.DARK_RED
        val DANGER = Base.MUTED_ORANGE
        val RESTRICTED = Base.DARK_RED
        val OVERFLIGHT_RESTRICTION = Base.DARK_RED
        val TRA = Base.DARK_RED  // Temporary Reserved Airspace
        val MTA = Base.DARK_RED  // Military Training Area
        val UNCLASSIFIED = Color.Black

        // Airspace Information
        val FIR = Base.DARK_GREEN
        val FIS = Base.DARK_GREEN

        // Special Activity Zones
        val ACTIVITY = Base.DARK_PURPLE
        val RMZ = Base.DARK_PURPLE
        val TMZ = Base.DARK_PURPLE

        // Other types
        val GLIDING_SECTOR = Base.MUTED_YELLOW

        // Default fallback
        val UNKNOWN = Color.Black
    }

    // ============================================================================
    // UI ELEMENT COLORS
    // ============================================================================
    object UI {
        val TEXT_COLOR = Color.White
        val CARD_BACKGROUND = Color.White
        val CARD_BACKGROUND_ALPHA = 0.1f
        val SWIPE_HINT = Color.Gray
        val SWIPE_HINT_ALPHA = 0.7f
        val FREQUENCY = Color.Cyan
        val LIMIT = Color.LightGray
        val SELECTION_BORDER = Color.Cyan
    }

    // ============================================================================
    // CROSS-SECTION SPECIFIC COLORS
    // ============================================================================
    object CrossSection {
        val BAR_SELECTED_ALPHA = 0.5f
        val LABEL_BACKGROUND = Color.Black
        val LABEL_TEXT = Color.White.copy(alpha = 0.5f) // White with 50% opacity
    }

    // ============================================================================
    // HIGHLIGHT COLORS
    // ============================================================================
    object Highlight {
        val FILL_COLOR = Color(64, 224, 208, 128) // Turquoise with 50% opacity for airspace highlights
    }

    // ============================================================================
    // HELPER FUNCTIONS
    // ============================================================================

    /**
     * Get color for airspace type - centralized function used across all airspace components
     */
    fun getAirspaceTypeColor(type: String): Color {
        return when (type) {
            // ICAO Classes
            "A" -> AirspaceType.CLASS_A
            "C" -> AirspaceType.CLASS_C
            "D" -> AirspaceType.CLASS_D
            "E" -> AirspaceType.CLASS_E
            "G" -> AirspaceType.CLASS_G

            // Special Use Airspace
            "PROHIBITED" -> AirspaceType.PROHIBITED
            "DANGER" -> AirspaceType.DANGER
            "RESTRICTED" -> AirspaceType.RESTRICTED
            "MTA" -> AirspaceType.MTA
            "OVERFLIGHT_RESTRICTION" -> AirspaceType.OVERFLIGHT_RESTRICTION
            "TRA" -> AirspaceType.TRA
            "UNCLASSIFIED" -> AirspaceType.UNCLASSIFIED

            // Airspace Information
            "FIR" -> AirspaceType.FIR
            "FIS" -> AirspaceType.FIS

            // Special Activity Zones
            "ACTIVITY" -> AirspaceType.ACTIVITY
            "RMZ" -> AirspaceType.RMZ
            "TMZ" -> AirspaceType.TMZ

            // Other types
            "GLIDING_SECTOR" -> AirspaceType.GLIDING_SECTOR

            // Default fallback
            else -> AirspaceType.UNKNOWN
        }
    }

    /**
     * Get android.graphics.Color version for MapLibre compatibility
     */
    fun getAirspaceTypeColorAndroid(type: String): Int {
        val color = getAirspaceTypeColor(type)
        return argbColor(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
    }
}
