package org.mountaincircles.app.modules.circles.layer.ui.composables

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.expressions.dsl.*
import org.maplibre.compose.expressions.value.SymbolPlacement
import org.maplibre.compose.expressions.value.TextRotationAlignment
import org.maplibre.compose.expressions.value.SymbolOverlap
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.modules.circles.logic.data.LabelsData
import org.mountaincircles.app.modules.circles.layer.ui.CirclesLayerManager

/**
 * Circle line labels layer for ELEV values using SymbolLayer with Open Sans Regular font
 */
@Composable
fun CirclesLineLabelsLayer(module: CirclesModule, layerManager: CirclesLayerManager) {
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Circles line labels layer recomposing")

    // ✅ Selective reactivity - observe only line labels-relevant flows
    val isAvailable by module.hasDataToRender.collectAsState()
    val activeConfig by module.activeConfig.collectAsState()
    val circlesVisibility by module.circlesVisibility.collectAsState()
    val circleLabelsMinZoom by module.circleLabelsMinZoom.collectAsState()
    val circlesLabelSize by module.circlesLabelSize.collectAsState()
    val circlesLabelSpacing by module.circlesLabelSpacing.collectAsState() // ← Only these trigger line labels recomposition

    // Only render if available and has active config (layers stay alive, visibility controlled by layer property)
    if (isAvailable && activeConfig != null) {
        val config = activeConfig!!

        // Get the layer manager instance to access dynamic data
        val layerManager = remember { module.layerManagerInstance }

        // ✅ URI APPROACH: Check for dynamic URI first, fallback to static file
        val dynamicUri by remember(layerManager?.currentLabelsUri) {
            derivedStateOf { layerManager?.currentLabelsUri }
        }
        val fileUri by remember(dynamicUri, config) {
            derivedStateOf {
                if (dynamicUri != null) {
                    Logger.log("CIRCLES", LogLevel.DEBUG, "Using DYNAMIC URI for labels data: $dynamicUri")
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
                    Logger.log("CIRCLES", LogLevel.DEBUG, "Using STATIC URI for labels data: $staticUri")
                    staticUri
                }
            }
        }

        val geoJsonSource = rememberGeoJsonSource(
            data = GeoJsonData.Uri(fileUri!!)
        )

        // Create symbol layer for circle line labels with line placement
        SymbolLayer(
                id = "line-labels-${config.packId}-${config.configId}",
            source = geoJsonSource,
            visible = circlesVisibility,  // ← Control visibility without destroying layer!
            minZoom = circleLabelsMinZoom,

            // Line placement with multiple labels
            placement = const(SymbolPlacement.Line),
            spacing = const(circlesLabelSpacing.dp),  // Dynamic spacing from settings

            // Text content - ELEV values
            textField = feature["ELEV"].asString(),

            // Text appearance - using existing settings
            textFont = const(listOf("Open Sans Regular")),
            textSize = const(circlesLabelSize.sp),
            textColor = const(androidx.compose.ui.graphics.Color.Black),
            textHaloColor = const(androidx.compose.ui.graphics.Color.White),
            textHaloWidth = const(2.0f.dp),

            // Text positioning and rotation
            textRotationAlignment = const(TextRotationAlignment.Map),  // Follow line direction
            textKeepUpright = const(true),  // Keep text upright for readability
            textOffset = offset(0.em, 0.em),  // No offset from line

            // Collision handling - no overlap between labels of this layer
            textPadding = const(4.dp),
            textAllowOverlap = const(false),  // No overlap with other text in same layer
            textIgnorePlacement = const(true),  // Allow other elements to overlap this text
            textOverlap = const(SymbolOverlap.Never)
        )

        Logger.log("CIRCLES", LogLevel.DEBUG, "Created circle line labels layer: line-labels-${config.packId}-${config.configId} using URI: $fileUri")
    }
}
