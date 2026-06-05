package org.mountaincircles.app.modules.circles.layer.ui.composables

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.expressions.dsl.*
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.modules.circles.logic.data.AirfieldLabelsData
import org.mountaincircles.app.io.getGlobalFileManager

/**
 * Airfield labels layer using SymbolLayer with Open Sans Regular font
 */
@Composable
fun CirclesAirfieldLabelsLayer(module: CirclesModule) {
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Circles airfield labels layer recomposing")

    // ✅ Selective reactivity - observe only airfield labels-relevant flows
    val isAvailable by module.hasDataToRender.collectAsState()
    val activeConfig by module.activeConfig.collectAsState()
    val airfieldsVisibility by module.airfieldsVisibility.collectAsState()
    val airfieldLabelsMinZoom by module.airfieldLabelsMinZoom.collectAsState()
    val airfieldLabelSize by module.airfieldLabelSize.collectAsState()
    val labelOffset by module.labelOffset.collectAsState() // ← Only these trigger airfield labels recomposition

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
        Logger.log("CIRCLES", LogLevel.DEBUG, "Using URI for airfield labels data: $fileUri")

        // Always create layer - let MapLibre handle file existence
        val geoJsonSource = rememberGeoJsonSource(
            data = GeoJsonData.Uri(fileUri)
        )

        // Create symbol layer for airfield labels
        SymbolLayer(
            id = "airfield-labels-${config.packId}-${config.configId}",
            source = geoJsonSource,
            visible = airfieldsVisibility,  // ← Control visibility without destroying layer!
            textField = feature["name"].asString(),
            textFont = const(listOf("Open Sans Regular")),
            textSize = const(airfieldLabelSize.sp),
            textColor = const(androidx.compose.ui.graphics.Color.Black),
            textHaloColor = const(androidx.compose.ui.graphics.Color.White),
            textHaloWidth = const(2.0f.dp),
            minZoom = airfieldLabelsMinZoom,
            textOffset = offset((labelOffset/16.0f).em, (labelOffset/16.0f).em) // Convert pixels to em
        )

        Logger.log("CIRCLES", LogLevel.DEBUG, "Created airfield labels layer: airfield-labels-${config.packId}-${config.configId}")
    }
}
