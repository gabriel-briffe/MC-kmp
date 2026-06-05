package org.mountaincircles.app.modules.airports.logic.data

import androidx.compose.ui.graphics.Color
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceColors
import org.mountaincircles.app.utils.argbColor

/**
 * Centralized color configuration for airports module.
 * Color-codes airports by type, similar to airspace classification.
 */
object AirportColors {

    // ============================================================================
    // AIRPORT BASE COLORS
    // ============================================================================
    object Base {
        // Reuse airspace colors for consistency
        val AIRSPACE_RED = AirspaceColors.Base.DARK_RED        // Same as Class A / Prohibited
        val AIRSPACE_ORANGE = AirspaceColors.Base.MUTED_ORANGE  // Same as Danger areas

        // Airport-specific colors
        val BLUE = Color(0xFF2196F3)        // Material Blue
        val ORANGE = Color(0xFFFF9800)     // Material Orange
        val PINK = Color(0xFFE91E63)       // Material Pink
        val GREY = Color.Gray              // Disabled airports
        val BLACK = Color.Black
    }

    // ============================================================================
    // AIRPORT TYPE COLORS
    // ============================================================================
    object AirportType {
        // High-priority / Controlled airports
        val INTERNATIONAL_AIRPORT = Base.AIRSPACE_RED    // Same as Class A
        val MILITARY_AERODROME = Base.AIRSPACE_RED       // Same as Prohibited

        // Standard airports
        val AIRPORT_CIVIL_MILITARY = Base.BLUE
        val AIRPORT_IFR = Base.BLUE
        val AIRFIELD_CIVIL = Base.BLUE
        val GLIDER_SITE = Base.BLUE

        // Specialized airports
        val ULTRALIGHT_SITE = Base.ORANGE
        val ALTIPORT = Base.PINK

        // Disabled airports (grey)
        val DISABLED = Base.GREY

        // Default (unknown airport types)
        val OTHER = Base.BLACK
    }

    // ============================================================================
    // HELPER FUNCTIONS
    // ============================================================================

    /**
     * Get color for airport type - centralized function used across all airport components
     */
    fun getAirportTypeColor(type: String): Color {
        return when (type) {
            "disabled" -> AirportType.DISABLED  // Disabled airports are grey
            "International Airport" -> AirportType.INTERNATIONAL_AIRPORT
            "Military Aerodrome" -> AirportType.MILITARY_AERODROME
            "Airport (civil/military)" -> AirportType.AIRPORT_CIVIL_MILITARY
            "Airport resp. Airfield IFR" -> AirportType.AIRPORT_IFR
            "Airfield Civil" -> AirportType.AIRFIELD_CIVIL
            "Glider Site" -> AirportType.GLIDER_SITE
            "Ultra Light Flying Site" -> AirportType.ULTRALIGHT_SITE
            "Altiport" -> AirportType.ALTIPORT
            else -> AirportType.OTHER  // Black for unknown types
        }
    }

    /**
     * Get android.graphics.Color version for MapLibre compatibility
     */
    fun getAirportTypeColorAndroid(type: String): Int {
        val color = getAirportTypeColor(type)
        return argbColor(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
    }
}
