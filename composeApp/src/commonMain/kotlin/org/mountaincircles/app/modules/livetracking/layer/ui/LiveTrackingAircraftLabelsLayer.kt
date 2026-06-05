package org.mountaincircles.app.modules.livetracking.layer.ui

import androidx.compose.runtime.*
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.livetracking.LiveTrackingModule
import org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingVisibilityMode
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.expressions.dsl.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import org.maplibre.spatialk.geojson.FeatureCollection

/**
 * Live Tracking Aircraft Labels Layer - displays aircraft registration labels
 *
 * Shows registration short text labels positioned beside aircraft icons.
 */
@Composable
fun LiveTrackingAircraftLabelsLayer(module: LiveTrackingModule) {
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Live tracking aircraft labels layer composable called")

    // Get aircraft features from the module
    val aircraftFeatures by module.aircraftFeaturesFlow.collectAsState()
    val layerData by module.layerDataFlow.collectAsState() // Used for visibility
    val friendlist by module.friendlistFlow.collectAsState() // For friendlist access

    if (layerData.visibilityMode == LiveTrackingVisibilityMode.ALL_HIDDEN) {
        Logger.log("LIVETRACKING_LAYERS", LogLevel.DEBUG, "Live tracking labels layer hidden, skipping render")
        return
    }

    // Only access settings after visibility check
    val settings by module.liveTrackingSettings
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Aircraft features: ${aircraftFeatures.size} features, Label size: ${settings.labelSize}, Offset: ${settings.labelOffset}")

    if (aircraftFeatures.isNotEmpty()) {
        Logger.log("LIVETRACKING_LAYERS", LogLevel.INFO, "🎯 RENDERING LABELS: ${aircraftFeatures.size} features available")

        // Debug: Log first feature properties
        aircraftFeatures.values.firstOrNull()?.let { firstFeature ->
            Logger.log("LIVETRACKING_LAYERS", LogLevel.INFO, "📋 First feature properties: ${firstFeature.properties}")

            // Extract JSON content properly (like findAircraftData does)
            val regShort = when (val prop = firstFeature.properties["registrationShort"]) {
                is kotlinx.serialization.json.JsonPrimitive -> if (prop.isString) prop.content else prop.toString()
                else -> prop?.toString() ?: "null"
            }
            val alt = when (val prop = firstFeature.properties["altitude"]) {
                is kotlinx.serialization.json.JsonPrimitive -> if (prop.isString) prop.content else prop.toString()
                else -> prop?.toString() ?: "null"
            }

            Logger.log("LIVETRACKING_LAYERS", LogLevel.INFO, "🏷️  registrationShort: '$regShort'")
            Logger.log("LIVETRACKING_LAYERS", LogLevel.INFO, "📊 altitude: '$alt'")
        }

        // Create GeoJSON source from aircraft features
        val source = rememberGeoJsonSource(data = GeoJsonData.Features(FeatureCollection(aircraftFeatures.values.toList())))

        // Create MapLibre filter for friendlist when in FRIENDS_ONLY mode
        val friendDeviceIds = remember(friendlist) {
            friendlist.map { it.deviceId }
        }
        val filterExpression = remember(layerData.visibilityMode, friendDeviceIds) {
            when (layerData.visibilityMode) {
                LiveTrackingVisibilityMode.ALL_VISIBLE -> nil() // No filter, show all
                LiveTrackingVisibilityMode.FRIENDS_ONLY -> {
                    if (friendDeviceIds.isNotEmpty()) {
                        // Filter to only show aircraft whose deviceId is in the friendlist
                        const(friendDeviceIds).contains(feature["deviceId"].asString())
                    } else {
                        const(false) // If no friendlist, show nothing (filter that always evaluates to false)
                    }
                }
                LiveTrackingVisibilityMode.ALL_HIDDEN -> nil() // Won't reach here due to early return
            }
        }

        Logger.log("LIVETRACKING_LAYERS", LogLevel.INFO, "🏗️  Creating SymbolLayer for labels with filter: ${filterExpression != null}")

        // Display aircraft registration labels with MapLibre filtering
        org.maplibre.compose.layers.SymbolLayer(
            id = "livetracking_aircraft_labels",
            source = source,
            filter = filterExpression,
            minZoom = settings.labelMinZoom,
            textField = format(
                span(feature["displayName"].asString()),span(" "),span(feature["altitude"].asString()),span("m"),
                span("\n"),
                span(feature["timeAgo"].asString())
            ),
            textFont = const(listOf("Open Sans Regular")),
            textSize = const(settings.labelSize.sp),
            textColor = const(Color.Black), // Black text
            textHaloColor = const(Color.White), // White halo
            textHaloWidth = const(2.0f.dp), // 2dp halo width
            textOffset = offset(0.em, settings.labelOffset.em), // Position text below the aircraft
            textAllowOverlap = const(false),
            textIgnorePlacement = const(false)
        )

        // Log when labels are rendered
        LaunchedEffect(aircraftFeatures) {
            Logger.log("LIVETRACKING_LAYERS", LogLevel.INFO, "Rendered ${aircraftFeatures.size} aircraft label positions")
        }
    } else {
        Logger.log("LIVETRACKING_LAYERS", LogLevel.DEBUG, "No aircraft features available for label rendering")
    }
}
