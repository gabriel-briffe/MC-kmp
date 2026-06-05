package org.mountaincircles.app.modules.circles.layer.ui.composables

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature.Companion.getStringProperty
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.modules.circles.layer.ui.CirclesLayerManager
import org.mountaincircles.app.io.getGlobalFileManager

/**
 * Click area layer for airfields (50% opaque red circles for visibility)
 * This will be the clickable layer once we implement click handling
 */
@Composable
fun CirclesClickAreaLayer(module: CirclesModule, layerManager: CirclesLayerManager) {
    // ✅ Selective reactivity - observe state but don't conditionally render
    val circlesSubmenuOpen = module.circlesSubmenuOpen?.collectAsState()?.value ?: false
    val isAvailable by module.hasDataToRender.collectAsState()
    val activeConfig by module.activeConfig.collectAsState()
    val circlesVisibility by module.circlesVisibility.collectAsState()
    val airfieldsVisibility by module.airfieldsVisibility.collectAsState()
    val airfieldIconsMinZoom by module.airfieldIconsMinZoom.collectAsState()
    val airfieldClickSize by module.airfieldClickSize.collectAsState()
    val circlesMinZoom by module.circlesMinZoom.collectAsState()

    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Circles click area layer recomposing - circlesSubmenuOpen: $circlesSubmenuOpen")

    // Always render if available and has active config, but control clickability via submenu state
    if (isAvailable && activeConfig != null) {
        Logger.log("CIRCLES_CLICK", LogLevel.DEBUG, "CirclesClickAreaLayer: Rendering click layer (submenu open: $circlesSubmenuOpen)")
        val config = activeConfig!!

        // Use the actual file structure: circles/packId/configId/
        val fileManager = getGlobalFileManager()
        val basePath = "${fileManager.getAppDataDirectory()}/circles/${config.packId}/${config.configId}"
        val pointsFileName = "airfields.geojson"
        val geoJsonPath = "$basePath/$pointsFileName"

        // ✅ URI APPROACH: Use file path directly instead of loading content
        val fileUri = "file://$geoJsonPath"
        Logger.log("CIRCLES", LogLevel.DEBUG, "Using URI for click areas data: $fileUri")

        val geoJsonSource = rememberGeoJsonSource(
            data = GeoJsonData.Uri(fileUri)
        )

        // Create semi-transparent red circle layer for click areas (30px radius, 50% opaque)
        // This layer handles clicks using MapLibre's built-in feature querying
        CircleLayer(
            id = "click-areas-${config.packId}-${config.configId}",
            source = geoJsonSource,
            visible = circlesVisibility && airfieldsVisibility,  // Always control visibility this way
            minZoom = circlesMinZoom,
            radius = const(airfieldClickSize.dp), // Configurable click area size
            color = const(androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.3f)), // Semi-transparent red for feedback
            opacity = const(layerManager.clickLayerOpacityValue), // Dynamic opacity for feedback
            onClick = { features ->
                // Only handle clicks when submenu is open
                if (circlesSubmenuOpen) {
                    Logger.log("CIRCLES_CLICK", LogLevel.DEBUG, "CircleLayer onClick: Received ${features.size} features (submenu open)")

                    if (features.isNotEmpty()) {
                        val feature = features.first()
                        val airfieldName = feature.getStringProperty("name")

                        if (!airfieldName.isNullOrBlank()) {
                            Logger.log("CIRCLES_CLICK", LogLevel.INFO, "CircleLayer onClick: Clicked on airfield '$airfieldName'")
                            layerManager.toggleAirfieldLayer(airfieldName)
                            ClickResult.Consume
                        } else {
                            Logger.log("CIRCLES_CLICK", LogLevel.DEBUG, "CircleLayer onClick: Feature has no valid name property")
                            ClickResult.Pass
                        }
                    } else {
                        Logger.log("CIRCLES_CLICK", LogLevel.DEBUG, "CircleLayer onClick: No features in click area")
                        ClickResult.Pass
                    }
                } else {
                    Logger.log("CIRCLES_CLICK", LogLevel.DEBUG, "CircleLayer onClick: Ignoring click - submenu closed")
                    ClickResult.Pass
                }
            }
        )

        Logger.log("CIRCLES", LogLevel.DEBUG, "Created click area layer: click-areas-${config.packId}-${config.configId} (submenu: $circlesSubmenuOpen)")
    } else {
        Logger.log("CIRCLES_CLICK", LogLevel.DEBUG, "CirclesClickAreaLayer: Not rendering - conditions not met")
    }
}
