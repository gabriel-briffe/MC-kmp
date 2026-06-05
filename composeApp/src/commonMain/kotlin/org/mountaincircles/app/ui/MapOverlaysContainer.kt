package org.mountaincircles.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.mountaincircles.app.ui.overlay.MapOverlayRegistry
import androidx.compose.runtime.derivedStateOf
import org.mountaincircles.app.state.GlobalState

/**
 * Container for rendering map overlays from modules
 * Phase 3: Extracted overlay rendering to separate concerns
 * Renders above map but below UI components
 */
@Composable
fun MapOverlaysContainer(
    uiComponentsState: UIComponentsState,
    globalState: GlobalState
) {
    // Get available modules from UI state
    val availableModules by uiComponentsState.modulesAvailableForUI.collectAsState()

    // Render registered overlay widgets from modules (like sidebar widgets)
    // Get active overlays from registered modules (same pattern as sidebar)
    val activeOverlays by remember(availableModules) {
        derivedStateOf {
            availableModules.mapNotNull { module ->
                MapOverlayRegistry.getOverlay(module.moduleId)?.let { provider ->
                    provider to module
                }
            }
        }
    }

    // Debug: Log when overlays change
    LaunchedEffect(activeOverlays) {
        if (activeOverlays.isNotEmpty()) {
            println("🛡️ MapOverlaysContainer: ${activeOverlays.size} active overlays")
        }
    }

    if (activeOverlays.isNotEmpty()) {
        // Group overlays by position
        val overlaysByPosition = activeOverlays.groupBy { it.first.position }

        // Render each position group
        overlaysByPosition.forEach { (position, overlays) ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = position.toAlignment()
            ) {
                // Stack overlays by priority (highest on top)
                overlays
                    .sortedBy { it.first.priority }
                    .forEach { (provider, module) ->
                        Box {
                            provider.OverlayContent(module, globalState)
                        }
                    }
            }
        }
    }
}
