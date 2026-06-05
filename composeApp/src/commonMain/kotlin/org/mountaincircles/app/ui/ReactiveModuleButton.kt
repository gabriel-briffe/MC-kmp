package org.mountaincircles.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.*
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.state.NavigationState

/**
 * A generic reactive module button that works with modules implementing both ModuleBase and ModuleUI
 *
 * This component eliminates the need for UI components to depend on global state
 * managers, creating direct reactive connections between modules and their UI.
 *
 * The module provides its own icon, action, and content description through the ModuleUI interface.
 * Supports different button types: SUBMENU (default) and TOGGLE.
 */
@Composable
fun ReactiveModuleButton(
    moduleBase: ModuleBase,
    onClick: () -> Unit,
    globalState: GlobalState?,
    navigationState: NavigationState?,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    // Subscribe directly to the module's UI state
    val uiState by moduleBase.uiState.collectAsState()

    // Always render to reserve space, but make invisible when no data available
    val isButtonVisible = moduleBase.isTopMenuButtonVisible
    val shouldShowButton = uiState.shouldShow && isButtonVisible

    // Use the action provided by the module, or fallback to the default onClick (only for active buttons)
    val actualOnClick = if (shouldShowButton) {
        moduleBase.getButtonAction() ?: onClick
    } else {
        {} // No-op for invisible buttons
    }

    // Get the long click action from the module
    val actualOnLongClick = if (shouldShowButton) {
        moduleBase.getButtonLongClickAction() ?: {}
    } else {
        {} // No-op for invisible buttons
    }

    Box(
        modifier = modifier
            .size(24.dp)
            .combinedClickable(
                enabled = shouldShowButton,
                onClick = actualOnClick,
                onLongClick = actualOnLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (shouldShowButton) {
            // Render active button
            val icon = moduleBase.getButtonIcon() ?: moduleBase.getIcon() ?: getFallbackIcon(uiState)
            Logger.log("UI", LogLevel.DEBUG, "ReactiveModuleButton: Active button for ${moduleBase.moduleId} - is button visible: $isButtonVisible")

            if (icon != null) {
                // Use Box to center icons with optional cropping
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .then(if (moduleBase.shouldCropTopMenuIcon()) Modifier.graphicsLayer { clip = true } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = icon,
                        contentDescription = "",
                        contentScale = if (moduleBase.shouldCropTopMenuIcon()) ContentScale.FillHeight else ContentScale.Fit,
                        modifier = if (moduleBase.shouldCropTopMenuIcon())
                            Modifier.height(24.dp) // Full height, natural width with clipping
                        else
                            Modifier.fillMaxSize(), // Fill the entire 24dp area
                        colorFilter = if (isHighlighted) {
                            androidx.compose.ui.graphics.ColorFilter.tint(Color.Cyan)
                        } else {
                            null // No color filter - preserve original icon colors
                        }
                    )
                }
            } else {
                // Fallback: simple colored circle indicator
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(24.dp)
                ) {
                    drawCircle(
                        color = when {
                            uiState.hasDisplayableContent -> Color.White
                            uiState.isLoading -> Color.Yellow
                            uiState.errorMessage != null -> Color.Red
                            else -> Color.Gray
                        },
                        radius = 12.dp.toPx()
                    )
                }
            }
        } else {
            // Render invisible placeholder to maintain layout
            val reason = when {
                !isButtonVisible -> "button not visible"
                !uiState.shouldShow -> "module not ready"
                else -> "unknown reason"
            }
            Logger.log("UI", LogLevel.DEBUG, "ReactiveModuleButton: Invisible placeholder for ${moduleBase.moduleId} ($reason) - is button visible: $isButtonVisible")
            Spacer(modifier = Modifier.size(24.dp)) // Invisible placeholder maintains layout
        }
    }
}

// Helper function to get fallback icon based on UI state
@Composable
private fun getFallbackIcon(uiState: ModuleUIState): androidx.compose.ui.graphics.painter.Painter? {
    return when {
        uiState.hasDisplayableContent -> null // Will render as circle
        uiState.isLoading -> null // Will render as circle
        uiState.errorMessage != null -> null // Will render as circle
        else -> null // Will render as circle
    }
}
