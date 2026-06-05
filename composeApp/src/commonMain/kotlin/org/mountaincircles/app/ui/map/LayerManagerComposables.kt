package org.mountaincircles.app.ui.map

import androidx.compose.runtime.*
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.GlobalState
import androidx.compose.runtime.derivedStateOf

/**
 * Standalone composable function for rendering all layers
 * Extracted from LayerManager class to fix Compose runtime issues
 */
@Composable
fun RenderLayersComposable(layerManager: LayerManager, layers: List<LayerDescriptor>) {
    val renderLayers by remember(layers) {
        derivedStateOf {
            layers.filter { it.shouldRender() }.sortedBy { it.renderPriority }
        }
    }

    Logger.log("LAYER_MANAGER", LogLevel.INFO,
        "🎨 RENDERING ${renderLayers.size} layers: [${renderLayers.joinToString(",") { it.id }}]")

    for (layer in renderLayers) {
        key(layer.id) {
            val stableRegistration = layerManager.getStableLayerRegistration(layer.id)
            if (stableRegistration != null && stableRegistration.stableComposable != null) {
                stableRegistration.stableComposable(stableRegistration.stableValues)
            } else {
                val composable = layerManager.getLayerComposable(layer.id)
                if (composable != null) {
                    composable()
                } else {
                    Logger.log("LAYER_MANAGER", LogLevel.WARN,
                        "No composable found for layer: ${layer.id}")
                }
            }
        }
    }
}

/**
 * Unified LayerManager composable that handles all module layers
 *
 * All modules (Geolocation, Maps, Wave, Circles) are now migrated to use the LayerManager system,
 * providing consistent layer priority management, lifecycle handling, and click interaction.
 */
@Composable
fun LayerManagerComposables(globalState: GlobalState) {
    val layerManager = remember { LayerManager.instance }
    val mapReady by globalState.mapReady.collectAsState()
    val layers by layerManager.layers.collectAsState()

    // Only log when layers actually change to avoid spam
    val layerIds = remember(layers) { layers.map { it.id } }
    Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
        "LayerManagerComposables: mapReady=$mapReady, layers=${layers.size} [${layerIds.joinToString(",")}]")

    if (mapReady) {
        RenderLayersComposable(layerManager, layers)
    }
}