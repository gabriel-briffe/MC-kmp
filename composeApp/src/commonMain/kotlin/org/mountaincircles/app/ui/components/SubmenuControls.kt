package org.mountaincircles.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Sealed class representing different types of submenu controls
 */
sealed class SubmenuControl {

    /**
     * Icon button control
     */
    data class IconButton(
        val icon: () -> Painter,
        val contentDescription: String,
        val onClick: () -> Unit,
        val isActive: Boolean = false,
        val enabled: Boolean = true
    ) : SubmenuControl()

    /**
     * Toggle control with icon
     */
    data class Toggle(
        val icon: () -> Painter,
        val contentDescription: String,
        val checked: Boolean,
        val onValueChange: (Boolean) -> Unit,
        val label: String = "",
        val enabled: Boolean = true
    ) : SubmenuControl()

    /**
     * Display-only label with optional icon
     */
    data class Label(
        val text: String,
        val icon: (() -> Painter)? = null,
        val contentDescription: String = ""
    ) : SubmenuControl()

    /**
     * Progress indicator
     */
    data class Progress(
        val progress: Float,
        val label: String = "",
        val contentDescription: String = "Progress"
    ) : SubmenuControl()

    /**
     * Dropdown/selection control
     */
    data class Dropdown(
        val icon: () -> Painter,
        val contentDescription: String,
        val options: List<String>,
        val selectedOption: String,
        val onSelectionChange: (String) -> Unit,
        val enabled: Boolean = true
    ) : SubmenuControl()

    /**
     * Slider control
     */
    data class Slider(
        val value: Float,
        val valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
        val onValueChange: (Float) -> Unit,
        val label: String = "",
        val enabled: Boolean = true
    ) : SubmenuControl()

    /**
     * Text field control
     */
    data class TextField(
        val value: String,
        val onValueChange: (String) -> Unit,
        val label: String,
        val enabled: Boolean = true
    ) : SubmenuControl()

    /**
     * Group container for multiple controls
     */
    data class Group(
        val title: String = "",
        val controls: List<SubmenuControl>
    ) : SubmenuControl()
}

/**
 * Represents a submenu section for multi-section layouts
 */
data class SubmenuSection(
    val title: String,
    val icon: (@Composable () -> Unit)? = null,
    val controls: List<SubmenuControl>,
    val isExpanded: Boolean = true,
    val onToggle: (() -> Unit)? = null
)

/**
 * Enhanced submenu theme configuration for advanced features
 */
data class EnhancedSubmenuTheme(
    val backgroundColor: androidx.compose.ui.graphics.Color,
    val textColor: androidx.compose.ui.graphics.Color,
    val borderColor: androidx.compose.ui.graphics.Color,
    val activeColor: androidx.compose.ui.graphics.Color,
    val activeTextColor: androidx.compose.ui.graphics.Color,
    val border: androidx.compose.ui.graphics.Color? = null
) {
    companion object {
        val Default = EnhancedSubmenuTheme(
            backgroundColor = androidx.compose.ui.graphics.Color(0xFF2A2A2A),
            textColor = androidx.compose.ui.graphics.Color.White,
            borderColor = androidx.compose.ui.graphics.Color.Gray,
            activeColor = androidx.compose.ui.graphics.Color.Cyan,
            activeTextColor = androidx.compose.ui.graphics.Color.Black
        )

        val Dark = EnhancedSubmenuTheme(
            backgroundColor = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
            textColor = androidx.compose.ui.graphics.Color.White,
            borderColor = androidx.compose.ui.graphics.Color.DarkGray,
            activeColor = androidx.compose.ui.graphics.Color(0xFF00FFFF),
            activeTextColor = androidx.compose.ui.graphics.Color.Black
        )

        val Floating = EnhancedSubmenuTheme(
            backgroundColor = androidx.compose.ui.graphics.Color(0xFF333333),
            textColor = androidx.compose.ui.graphics.Color.White,
            borderColor = androidx.compose.ui.graphics.Color(0xFF555555),
            activeColor = androidx.compose.ui.graphics.Color(0xFF00CCFF),
            activeTextColor = androidx.compose.ui.graphics.Color.Black,
            border = androidx.compose.ui.graphics.Color(0xFF777777)
        )

        val BottomSheet = EnhancedSubmenuTheme(
            backgroundColor = androidx.compose.ui.graphics.Color(0xFF252525),
            textColor = androidx.compose.ui.graphics.Color.White,
            borderColor = androidx.compose.ui.graphics.Color(0xFF444444),
            activeColor = androidx.compose.ui.graphics.Color(0xFF66CCFF),
            activeTextColor = androidx.compose.ui.graphics.Color.Black
        )

        val Light = EnhancedSubmenuTheme(
            backgroundColor = androidx.compose.ui.graphics.Color.White,
            textColor = androidx.compose.ui.graphics.Color.Black,
            borderColor = androidx.compose.ui.graphics.Color.LightGray,
            activeColor = androidx.compose.ui.graphics.Color(0xFF007BFF),
            activeTextColor = androidx.compose.ui.graphics.Color.White
        )
    }
}
