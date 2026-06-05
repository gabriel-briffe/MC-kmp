package org.mountaincircles.app.modules.skysight.layer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.ui.map.LayerRegistrationHelper
import org.mountaincircles.app.ui.map.LayerZIndex
import org.mountaincircles.app.ui.components.bytesToImageBitmap
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.maplibre.compose.expressions.dsl.*
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.ImageSource
import org.maplibre.compose.sources.rememberImageSource
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.util.PositionQuad
import org.maplibre.spatialk.geojson.Position
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration

/**
 * Skysight module LayerManager implementation - minimal empty implementation
 */
class SkysightLayerManager(private val module: SkysightModule) {

    // Track registered layer IDs for BaseStatefulModule adapter
    private val layerIds = mutableListOf<String>()
    val layerIdsPublic: List<String> get() = layerIds

    /**
     * Initialize and register all skysight layers with the LayerManager
     */
    fun initializeLayers() {
        Logger.log("SKYSIGHT_LAYERS", LogLevel.INFO,
            "Initializing skysight layers with LayerManager")

        // Register label layer (SymbolLayer for text labels)
        val skysightLabelsLayerId = LayerRegistrationHelper.registerLayer(
            moduleId = "skysight",
            layerName = "skysight_labels",
            zIndex = 10 * LayerZIndex.getZIndex("skysight") + 1, // Labels render above tiles
            layerType = org.mountaincircles.app.ui.map.LayerDescriptor.LayerType.OVERLAY,
            isInteractive = false,
            description = "SkySight data text labels",
            tags = setOf("skysight", "data", "labels", "text"),
            composable = {
                val isVisible by module.isVisible.collectAsState()
                val isLabelsVisible by module.isLabelsVisible.collectAsState()
                val viewportData by module.viewportDataFlow.collectAsState()
                val labelSize by module.labelSize.collectAsState()
                val forecastMinZoom by module.forecastMinZoom.collectAsState()

                // Early return if unified visibility is off OR labels not visible
                if (!isVisible || !isLabelsVisible) {
                    return@registerLayer
                }

                if (viewportData != null && viewportData!!.features.isNotEmpty()) {
                    val geoJsonSource = org.maplibre.compose.sources.rememberGeoJsonSource(
                        data = org.maplibre.compose.sources.GeoJsonData.Features(viewportData!!)
                    )
                    SymbolLayer(
                        id = "skysight_labels",
                        source = geoJsonSource,
                        minZoom = forecastMinZoom,
                        textField = feature["value"].asString(),
                        textFont = const(listOf("Open Sans Regular")),
                        textSize = const(labelSize.sp),
                        textColor = const(Color.Black),
                        textHaloColor = const(Color.White),
                        textHaloWidth = const(1.0f.dp),
                        textAnchor = const(SymbolAnchor.Center),
                        textAllowOverlap = const(false),
                        textIgnorePlacement = const(false)
                    )
                }
            }
        )
        layerIds.add(skysightLabelsLayerId)

        // Satellite and rain tiles are registered individually by RealTimeTilingController
        // No main layers needed - tiles register themselves

        Logger.log("SKYSIGHT_LAYERS", LogLevel.INFO, "Skysight layers registered successfully")
    }

}


