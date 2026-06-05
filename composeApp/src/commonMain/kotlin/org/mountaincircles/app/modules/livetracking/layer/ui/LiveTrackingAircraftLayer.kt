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
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.util.ClickResult
import org.mountaincircles.app.ui.AppIcons
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.spatialk.geojson.FeatureCollection

/**
 * Live Tracking Aircraft Layer - placeholder for future aircraft display
 *
 * Currently just logs when active. Will be expanded to show:
 * - Semi-transparent blue coverage area
 * - Live aircraft positions
 * - Aircraft trails
 * - Real-time updates from OGN API
 */
@Composable
fun LiveTrackingAircraftIconsLayer(module: LiveTrackingModule) {
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Live tracking aircraft layer composable called")

    // Get aircraft features from the module
    val aircraftFeatures by module.aircraftFeaturesFlow.collectAsState()
    val layerData by module.layerDataFlow.collectAsState() // Used for visibility and filtering
    val friendlist by module.friendlistFlow.collectAsState() // For friendlist access

    if (layerData.visibilityMode == LiveTrackingVisibilityMode.ALL_HIDDEN) {
        Logger.log("LIVETRACKING_LAYERS", LogLevel.DEBUG, "Live tracking icons layer hidden, skipping render")
        return
    }

    // Only access settings after visibility check
    val settings by module.liveTrackingSettings
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Aircraft features: ${aircraftFeatures.size} features, Icon size: ${settings.iconSize}")

    if (aircraftFeatures.isNotEmpty()) {
        Logger.log("LIVETRACKING_LAYERS", LogLevel.DEBUG, "Rendering aircraft features (${aircraftFeatures.size} features)")

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

        // Display aircraft as glider icons
        // Friends always show with heart icon, others with glider (regardless of visibility mode)
        val friendlistIds = remember(friendlist) {
            friendlist.map { it.deviceId }
        }

        org.maplibre.compose.layers.SymbolLayer(
            id = "livetracking_aircraft_positions",
            source = source,
            filter = filterExpression,
            minZoom = settings.iconMinZoom,
            iconImage = if (friendlistIds.isNotEmpty()) {
                // Check if deviceId is in friendlist
                switch(
                    condition(
                        test = const(friendlistIds).contains(feature["deviceId"].asString()),
                        output = image(AppIcons.GliderWithHeart())  // Friend aircraft
                    ),
                    fallback = image(AppIcons.Glider())  // Non-friend aircraft
                )
            } else {
                // No friends, all aircraft show glider
                image(AppIcons.Glider())
            },
            iconSize = interpolate(
                exponential(2.0f),
                zoom(),
                0.0 to const(0.05f),                    // Zoom 0: 0.5 (very small)
                settings.labelMinZoom to const(settings.iconSize / 10)  // Zoom at labelMinZoom: full size
            ),
            iconRotate = feature["track"].asNumber(), // Rotate by aircraft track direction
            iconAllowOverlap = const(true),
            iconIgnorePlacement = const(true),
            iconAnchor = const(SymbolAnchor.Center),
            onClick = { features ->
                Logger.log("LIVETRACKING_CLICK", LogLevel.INFO, "LiveTracking icon clicked - ${features.size} features")

                features.forEachIndexed { index, feature ->
                    Logger.log("LIVETRACKING_CLICK", LogLevel.INFO, "Feature $index properties: ${feature.properties}")
                    Logger.log("LIVETRACKING_CLICK", LogLevel.INFO, "Feature $index geometry: ${feature.geometry}")
                }

                // Convert MapLibre features to AircraftFeatureData for popup
                val aircraftFeatures = features.mapNotNull { feature ->
                    try {
                        val properties = feature.properties
                        Logger.log("LIVETRACKING_CLICK", LogLevel.DEBUG, "Processing feature with properties: $properties")

                        if (properties == null) {
                            Logger.log("LIVETRACKING_CLICK", LogLevel.DEBUG, "Feature properties is null, skipping")
                            return@mapNotNull null
                        }

                        // Include all properties in the display map (including registration, registrationShort, etc.)
                        val displayProperties = properties.filterKeys { key ->
                            key !in setOf("longitude", "latitude", "geometry") // Exclude geometry-related properties
                        }                        .mapValues { (key, value) ->
                            // Handle MapLibre JsonLiteral values properly
                            val result = when (value) {
                                is kotlinx.serialization.json.JsonPrimitive -> {
                                    if (value.isString) value.content else value.toString()
                                }
                                is String -> value
                                is Number -> value.toString()
                                is Boolean -> value.toString()
                                else -> value.toString()
                            }
                            result
                        }

                        // Only require deviceId - get it from the processed displayProperties
                        val deviceId = displayProperties["deviceId"]
                        Logger.log("LIVETRACKING_CLICK", LogLevel.DEBUG, "Raw displayProperties deviceId: '${displayProperties["deviceId"]}'")
                        Logger.log("LIVETRACKING_CLICK", LogLevel.DEBUG, "deviceId variable: '$deviceId'")
                        Logger.log("LIVETRACKING_CLICK", LogLevel.DEBUG, "deviceId equals 'fc44fe4': ${deviceId == "fc44fe4"}")
                        Logger.log("LIVETRACKING_CLICK", LogLevel.DEBUG, "deviceId equals '\"fc44fe4\"': ${deviceId == "\"fc44fe4\""}")

                        if (deviceId.isNullOrEmpty()) {
                            Logger.log("LIVETRACKING_CLICK", LogLevel.DEBUG, "Missing deviceId, skipping feature")
                            return@mapNotNull null
                        }

                        Logger.log("LIVETRACKING_CLICK", LogLevel.DEBUG, "All display properties: $displayProperties")

                        org.mountaincircles.app.modules.livetracking.logic.data.AircraftFeatureData(
                            deviceId = deviceId,
                            properties = displayProperties
                        )
                    } catch (e: Exception) {
                        Logger.log("LIVETRACKING_CLICK", LogLevel.ERROR, "Failed to parse aircraft feature: ${e.message}")
                        null
                    }
                }

                Logger.log("LIVETRACKING_CLICK", LogLevel.INFO, "Converted to ${aircraftFeatures.size} aircraft features")

                if (aircraftFeatures.isNotEmpty()) {
                    Logger.log("LIVETRACKING_CLICK", LogLevel.INFO, "Showing popup for deviceId: ${aircraftFeatures.first().deviceId}")
                    module.showAircraftPopup(aircraftFeatures.first().deviceId)
                } else {
                    Logger.log("LIVETRACKING_CLICK", LogLevel.WARN, "No valid aircraft features found for popup")
                }

                ClickResult.Consume // Consume the click to prevent other layers from handling it
            }
        )

        // Log when aircraft are rendered
        LaunchedEffect(aircraftFeatures) {
            Logger.log("LIVETRACKING_LAYERS", LogLevel.INFO, "Rendered ${aircraftFeatures.size} aircraft positions")
        }
    } else {
        Logger.log("LIVETRACKING_LAYERS", LogLevel.DEBUG, "No aircraft features available for rendering")
    }
}
