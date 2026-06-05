package org.mountaincircles.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Generic submenu styling system for consistent UI across all module submenus
 *
 * Centralizes colors, typography, and dimensions used in submenus like Wave, Skysight, etc.
 * Provides reusable styling functions that can be shared across different module submenus.
 */
object SubmenuStyles {

    // Colors matching Wave theme but generalized
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
        // Main labels: time (12:00), altitude (4200m), "Date"/"FC"
        fun mainLabel(fontSize: Float = 13.0f) = androidx.compose.ui.text.TextStyle(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = Colors.textPrimary,
            lineHeight = (fontSize * 1.2f).sp // 20% larger for comfortable spacing
        )

        // Sub labels: date (21/08), pressure (600hPa), forecast date
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
        val height = 52.dp
        val cardElevation = 0.dp
        val cornerRadius = 4.dp
        val iconSize = 40.dp
        val iconInnerSize = 20.dp
        val compactIconSize = 32.dp  // For submenus with many controls
        val compactIconInnerSize = 16.dp
        val buttonPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        val compactPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp)
    }

    // Spacing values for tight layout
    object Spacing {
        val none = 0.dp
        val tiny = 1.dp
        val small = 2.dp
        val medium = 4.dp
        val large = 8.dp
    }

    // Common card colors for submenu buttons
    object CardColors {
        val timeButton = Colors.primary
        val altitudeDisplay = Colors.surface
        val forecastButton = Colors.success
        val visibilityToggle = Color.Transparent
    }
}