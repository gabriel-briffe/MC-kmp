package org.mountaincircles.app.modules.wave

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centralized theming for the Wave module
 * 
 * Provides consistent styling across all wave UI components
 */
object WaveTheme {
    
    // Colors
    object Colors {
        val background = Color.Black.copy(alpha = 0.85f)
        val surface = Color.Gray.copy(alpha = 0.3f)
        val primary = Color.Blue.copy(alpha = 0.3f)
        val success = Color.Green.copy(alpha = 0.3f)
        val textPrimary = Color.White
        val textSecondary = Color.Gray
        val textAccent = Color.Cyan
        val iconEnabled = Color.White
        val iconDisabled = Color.Gray
    }
    
    // Typography - Dynamic font sizes based on settings
    object Typography {
        // Main labels: time (12:00), FC, altitude (4200m)
        fun mainLabel(fontSize: Float = 13.0f) = androidx.compose.ui.text.TextStyle(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = Colors.textPrimary,
            lineHeight = (fontSize * 1.2f).sp // 20% larger for comfortable spacing
        )
        
        // Sub labels: date (21/08), NOW, pressure (600hPa), forecast date
        fun subLabel(fontSize: Float = 10.0f) = androidx.compose.ui.text.TextStyle(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            color = Colors.textSecondary,
            lineHeight = (fontSize * 1.2f).sp // 20% larger for comfortable spacing
        )
        
        // Navigation arrows (separate from text labels)
        fun navArrow(fontSize: Float = 13.0f) = androidx.compose.ui.text.TextStyle(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold
        )
    }
    
    // Spacing and dimensions
    object Dimensions {
        val submenuHeight = 52.dp
        val buttonPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        val compactPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp)
        val iconSize = 24.dp
        val buttonMinWidth = 48.dp
        val cardElevation = 0.dp
        val cornerRadius = 4.dp
    }
    
    // Spacing values for tight layout
    object Spacing {
        val none = 0.dp
        val tiny = 1.dp
        val small = 2.dp
        val medium = 4.dp
        val large = 8.dp
    }
}

/**
 * Pressure to altitude conversion for display
 * Based on standard atmosphere model
 */
object AltitudeConverter {
    // Standard pressure levels and their approximate altitudes
    private val pressureToAltitude = mapOf(
        500 to 5500,   // ~18,045 ft
        600 to 4200,   // ~13,780 ft
        700 to 3000,   // ~9,843 ft
        800 to 1950,   // ~6,397 ft
        900 to 1000,   // ~3,281 ft
        1000 to 100    // ~984 ft (near sea level)
    )
    
    /**
     * Get approximate altitude in meters for a given pressure level
     */
    fun getAltitudeForPressure(pressureHpa: Int): Int {
        return pressureToAltitude[pressureHpa] ?: 0
    }
    
    /**
     * Format altitude for display (e.g., "4200m")
     */
    fun formatAltitude(pressureHpa: Int): String {
        return "${getAltitudeForPressure(pressureHpa)}m"
    }
    
    /**
     * Format pressure for display (e.g., "600hPa")
     */
    fun formatPressure(pressureHpa: Int): String {
        return "${pressureHpa}hPa"
    }
}
