package org.mountaincircles.app.modules.circles.layer.ui.composables

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.expressions.dsl.const
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.io.getGlobalFileManager

/**
 * Point layer for airfields (dark red circles with white stroke)
 */
@Composable
fun CirclesPointLayer(module: CirclesModule) {
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Circles point layer recomposing")

    // ✅ Selective reactivity - observe only point-relevant flows
    val isAvailable by module.hasDataToRender.collectAsState()
    val activeConfig by module.activeConfig.collectAsState()
    val airfieldsVisibility by module.airfieldsVisibility.collectAsState()
    val airfieldIconsMinZoom by module.airfieldIconsMinZoom.collectAsState()
    val airfieldIconSize by module.airfieldIconSize.collectAsState() // ← Only these trigger point layer recomposition

    // Only render if available and has active config (layers stay alive, visibility controlled by layer property)
    if (isAvailable && activeConfig != null) {
        val config = activeConfig!!

        // Use the actual file structure: circles/packId/configId/
        val fileManager = getGlobalFileManager()
        val basePath = "${fileManager.getAppDataDirectory()}/circles/${config.packId}/${config.configId}"
        val pointsFileName = "airfields.geojson"
        val geoJsonPath = "$basePath/$pointsFileName"

        // ✅ URI APPROACH: Use file path directly instead of loading content
        val fileUri = "file://$geoJsonPath"
        Logger.log("CIRCLES", LogLevel.DEBUG, "Using URI for points data: $fileUri")

        val geoJsonSource = rememberGeoJsonSource(
            data = GeoJsonData.Uri(fileUri)
        )

        // Create circle layer for airfields (dark red with white stroke)
        CircleLayer(
            id = "points-${config.packId}-${config.configId}",
            source = geoJsonSource,
            visible = airfieldsVisibility,  // ← Control visibility without destroying layer!
            minZoom = airfieldIconsMinZoom,
            radius = const(airfieldIconSize.dp),
            color = const(androidx.compose.ui.graphics.Color(0xFF8B0000)), // Dark red
            strokeColor = const(androidx.compose.ui.graphics.Color.White),
            strokeWidth = const(2.0f.dp)
        )

        Logger.log("CIRCLES", LogLevel.DEBUG, "Created point layer: points-${config.packId}-${config.configId}")
    }
}
