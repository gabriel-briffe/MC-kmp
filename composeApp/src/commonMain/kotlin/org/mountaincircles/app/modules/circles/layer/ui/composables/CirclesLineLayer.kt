package org.mountaincircles.app.modules.circles.layer.ui.composables

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.expressions.dsl.const
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.modules.circles.layer.ui.CirclesLayerManager
import org.mountaincircles.app.io.getGlobalFileManager

/**
 * Line layer for elevation contours (black lines)
 */
@Composable
fun CirclesLineLayer(module: CirclesModule, layerManager: CirclesLayerManager) {
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Circles line layer recomposing")

    // ✅ Selective reactivity - observe only line-relevant flows
    val isAvailable by module.hasDataToRender.collectAsState()
    val activeConfig by module.activeConfig.collectAsState()
    val circlesVisibility by module.circlesVisibility.collectAsState()
    val circlesMinZoom by module.circlesMinZoom.collectAsState()
    val circlesLineWidth by module.circlesLineWidth.collectAsState() // ← Only this triggers line layer recomposition

    Logger.log("CIRCLES_LAYERS", LogLevel.DEBUG, "Line layer visibility check: isAvailable=$isAvailable, activeConfig=${activeConfig?.configId}")

            // Only render if available and has active config (layers stay alive, visibility controlled by layer property)
    if (isAvailable && activeConfig != null) {
        val config = activeConfig!!
        Logger.log("CIRCLES_LAYERS", LogLevel.INFO, "Rendering circles line layer for config: ${config.configId}")

        // Use key for stable layer identity to prevent unnecessary recreations
        key(config.packId, config.configId) {
            // Get the layer manager instance to access dynamic data
            val layerManager = remember { module.layerManagerInstance }

            // URI APPROACH: Check for dynamic URI first, fallback to static file
            val dynamicUri by remember(layerManager?.currentLinesUri) {
                derivedStateOf { layerManager?.currentLinesUri }
            }
            val fileUri by remember(dynamicUri, config) {
                derivedStateOf {
                    if (dynamicUri != null) {
                        dynamicUri
                    } else {
                        // Use the actual file structure: circles/packId/configId/
                        val fileManager = getGlobalFileManager()
                        val basePath = "${fileManager.getAppDataDirectory()}/circles/${config.packId}/${config.configId}"
                        val metadata = config.metadata
                        val prefix = metadata?.prefix ?: config.configId.split("-").dropLast(1).joinToString("-")
                        val policy = metadata?.policy ?: config.packId
                        val mainFileName = "aa_${policy}_${prefix}.geojson"
                        val staticUri = "file://$basePath/$mainFileName"
                        staticUri
                    }
                }
            }

            val geoJsonSource = rememberGeoJsonSource(
                data = GeoJsonData.Uri(fileUri!!)
            )

            // Create line layer (black, configurable width)
            LineLayer(
                id = "lines-${config.packId}-${config.configId}",
                source = geoJsonSource,
                visible = circlesVisibility,  // ← Control visibility without destroying layer!
                minZoom = circlesMinZoom,
                color = const(androidx.compose.ui.graphics.Color.Black),
                width = const(circlesLineWidth.dp)
            )
        }
    } else {
        Logger.log("CIRCLES_LAYERS", LogLevel.DEBUG, "Circles line layer NOT rendering: isAvailable=$isAvailable, activeConfig=${activeConfig?.configId}")
    }
}
