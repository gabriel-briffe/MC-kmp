package org.mountaincircles.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import org.maplibre.spatialk.geojson.Position
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import kotlin.time.Duration.Companion.seconds
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.ui.map.LayerManagerComposables
import org.mountaincircles.app.modules.wave.logic.data.RasterData
import org.mountaincircles.app.modules.wave.logic.data.WaveSelection
import org.mountaincircles.app.ui.AppIcons
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.SymbolLayer
import org.mountaincircles.app.utils.currentTimeMillis
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.datetime.*
import org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingVisibilityMode
import org.mountaincircles.app.modules.skysight.logic.data.SkysightLayerData
import org.mountaincircles.app.modules.skysight.logic.controllers.SkysightTilingControllerV2

// OpenStreetMap style - MapLibre handles tile fetching natively
private val osmMapStyle = """
{
    "version": 8,
    "name": "OpenStreetMap",
    "metadata": {
        "mapbox:autocomposite": false,
        "mapbox:type": "template"
    },
    "sources": {
        "osm": {
            "type": "raster",
            "tiles": [
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            ],
            "tileSize": 256,
            "minzoom": 0,
            "maxzoom": 18,
            "attribution": "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors"
        }
    },
    "sprite": "",
    "glyphs": "$glyphsBaseUri{fontstack}/{range}.pbf",
    "layers": [
        {
            "id": "osm-layer",
            "type": "raster",
            "source": "osm",
            "minzoom": 0,
            "maxzoom": 18,
            "paint": {
                "raster-opacity": 1.0
            }
        }
    ]
}
"""

/**
 * Helper function to update Skysight viewport data for current selection
 */

/**
 * Map rendering container - Phase 1: Extracted from MainMapComposable
 * Still has some dependencies that will be cleaned up in later phases
 */
@Composable
fun MapContainer(
    mapState: MapRenderingState,
    locationState: LocationState,
    moduleLayersState: ModuleLayersState,
    globalState: org.mountaincircles.app.state.GlobalState // TODO: Phase 4 - remove via callbacks
) {
    val mapReady by mapState.mapReady.collectAsState()
    val northLocked by mapState.northLocked.collectAsState()
    val availableModules by moduleLayersState.modulesAvailableForUI.collectAsState()

    // Initialize camera state with European Alps position
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(latitude = 46.5, longitude = 8.5), // European Alps (Switzerland/Austria)
            zoom = 6.0, // Zoom level to see the broader Alps region
            bearing = 0.0,
            tilt = 0.0
        )
    )

    // Update current camera state in global state whenever it changes
    LaunchedEffect(cameraState) {
        globalState.updateCurrentCameraState(cameraState)
    }

    // Collect location marker settings from DataStore (using singleton)
    val locationMarkerSettings by org.mountaincircles.app.ui.locationMarkerSettingsManager.settings
        .collectAsState(initial = org.mountaincircles.app.ui.LocationMarkerSettings())

    // Track if we've already centered on the user's location (only once)
    var hasCenteredOnLocation by remember { mutableStateOf(false) }

    // North-lock is now in-memory only (no persistence)
    // Lock north when enabled
    LaunchedEffect(northLocked) {
        if (northLocked) {
            // Reset bearing to 0 (north up) when north-lock is enabled
            val currentPosition = cameraState.position
            val northUpPosition = currentPosition.copy(bearing = 0.0)
            cameraState.animateTo(northUpPosition)
        }
    }

    // Log startup
    LaunchedEffect(Unit) {
        Logger.log("MAP", LogLevel.INFO, "MapContainer initialized with pure map rendering")
    }

    // Auto-refresh wind vectors when camera moves and wind layer is visible
    // MOVED OUTSIDE MapLibreMap lambda so it can recompose properly
    val waveModule = remember(availableModules) {
        globalState.moduleManager.getModule("wave") as? org.mountaincircles.app.modules.wave.WaveModule
    }

    val skysightModule = remember(availableModules) {
        globalState.moduleManager.getModule("skysight") as? org.mountaincircles.app.modules.skysight.SkysightModule
    }

    val liveTrackingModule = remember(availableModules) {
        globalState.moduleManager.getModule("livetracking") as? org.mountaincircles.app.modules.livetracking.LiveTrackingModule
    }

    if (waveModule != null) {
        val windLayerVisible by waveModule.windLayerVisibilityFlow.collectAsState()
        val barbInterval by waveModule.barbIntervalFlow.collectAsState()
        val showZeroWindBarbs by waveModule.showZeroWindBarbsFlow.collectAsState()
        val layerData by waveModule.layerDataFlow.collectAsState()

        // Calculate wind vectors when wind visibility is enabled
        LaunchedEffect(windLayerVisible) {
            if (windLayerVisible) {
                Logger.log("WIND_CAMERA", LogLevel.INFO, "Wind visibility enabled, calculating initial wind vectors")
                val currentBarbInterval = waveModule.barbIntervalFlow.value
                waveModule.calculateWindVectors(cameraState.position, currentBarbInterval, (cameraState.position.bearing ?: 0.0).toFloat(), showZeroWindBarbs)
            }
        }

        // Reactive camera change detection using snapshotFlow
        if (windLayerVisible) {
            // Camera position changes
            LaunchedEffect(Unit) {
                snapshotFlow { cameraState.position }
                    .distinctUntilChanged()
                    .debounce(100) // 100ms debounce
                    .collect { position ->
                        Logger.log("WIND_CAMERA", LogLevel.INFO, "Camera position changed: ${position.target}, zoom=${position.zoom}")
                        val currentBarbInterval = waveModule.barbIntervalFlow.value
                waveModule.calculateWindVectors(position, currentBarbInterval, (position.bearing ?: 0.0).toFloat(), showZeroWindBarbs)
                    }
            }

            // Wave selection changes (date, time, isobar)
            LaunchedEffect(Unit) {
                snapshotFlow { layerData.selection }
                    .distinctUntilChanged()
                    .collect { selection ->
                        Logger.log("WIND_CAMERA", LogLevel.INFO, "Wave selection changed: ${selection.forecastDate}/${selection.targetDate} ${selection.hour}h ${selection.pressure}hPa")
                        val currentBarbInterval = waveModule.barbIntervalFlow.value
                        waveModule.calculateWindVectors(cameraState.position, currentBarbInterval, (cameraState.position.bearing ?: 0.0).toFloat(), showZeroWindBarbs)
                    }
            }

            // Barb interval changes
            LaunchedEffect(barbInterval) {
                Logger.log("WIND_CAMERA", LogLevel.INFO, "Barb interval changed: ${barbInterval}mm - recalculating wind vectors")
                waveModule.calculateWindVectors(cameraState.position, barbInterval, (cameraState.position.bearing ?: 0.0).toFloat(), showZeroWindBarbs)
            }

            // Show zero wind barbs setting changes
            LaunchedEffect(Unit) {
                snapshotFlow { showZeroWindBarbs }
                    .distinctUntilChanged()
                    .collect { showZero ->
                        Logger.log("WIND_CAMERA", LogLevel.INFO, "Show zero wind barbs changed: $showZero - recalculating wind vectors")
                        waveModule.calculateWindVectors(cameraState.position, barbInterval, (cameraState.position.bearing ?: 0.0).toFloat(), showZero)
                    }
            }
        }
    }

    // Auto-refresh Skysight data when camera moves and Skysight has data
    if (skysightModule != null) {
        val satelliteEnabled by skysightModule.satelliteEnabled.collectAsState()
        val localRainEnabled by skysightModule.localRainEnabled.collectAsState()
        val layerData by skysightModule.layerDataFlow.collectAsState()

        // Check if any SkySight layers are active (forecast or real-time)
        val anyLayersActive = layerData.selectedLayerId.isNotEmpty() || satelliteEnabled || localRainEnabled

        // Unified SkySight camera tracking - only active when any layers are selected
        if (anyLayersActive) {
            Logger.log("SKYSIGHT_CAMERA", LogLevel.DEBUG, "SkySight layers active - starting camera tracking")

            // Single camera position tracking for all SkySight layers
            LaunchedEffect(Unit) {
                snapshotFlow { cameraState.position }
                    .distinctUntilChanged()
                    .debounce(100)
                    .drop(1)
                    .collect { cameraPosition ->
                        Logger.log("SKYSIGHT_CAMERA", LogLevel.INFO, "Camera position changed: ${cameraPosition.target}, zoom=${cameraPosition.zoom}")

                        // Handle forecast layers
                        val currentLayerData = skysightModule.layerDataFlow.value
                        if (currentLayerData.hasData && currentLayerData.selectedLayerId.isNotEmpty() && currentLayerData.selectedDate != null) {
                            Logger.log("SKYSIGHT_CAMERA", LogLevel.INFO, "Handling forecast camera move for viewport update")
                            try {
                                SkysightTilingControllerV2.handleCameraMove(
                                    module = skysightModule,
                                    globalState = globalState,
                                    layerId = currentLayerData.selectedLayerId,
                                    date = currentLayerData.selectedDate!!,
                                    timePair = currentLayerData.currentTime
                                )
                            } catch (e: Throwable) {
                                Logger.log("SKYSIGHT_CAMERA", LogLevel.ERROR, "Error handling forecast camera move: ${e.message}")
                            }
                        }

                        // Handle real-time layers
                        if (satelliteEnabled || localRainEnabled) {
                            Logger.log("SKYSIGHT_CAMERA", LogLevel.INFO, "Handling real-time camera position change")
                            try {
                                org.mountaincircles.app.modules.skysight.logic.controllers.RealTimeTilingController.handleTileUpdate(
                                    module = skysightModule,
                                    globalState = globalState,
                                    timePair = null, // Use timestamp from module
                                    isNavigation = false
                                )
                            } catch (e: Throwable) {
                                Logger.log("SKYSIGHT_CAMERA", LogLevel.ERROR, "Error in real-time camera pipeline: ${e.message}")
                            }
                        }
                    }
            }
        }

        // Separate navigation change tracking (not camera position related)
        if (layerData.selectedLayerId.isNotEmpty()) {
            LaunchedEffect(layerData.selectedLayerId, layerData.selectedDate, layerData.currentTime, layerData.hasData) {
                val shouldProcess = layerData.selectedLayerId.isNotEmpty() &&
                                  layerData.selectedDate != null &&
                                  layerData.hasData == true
                if (shouldProcess) {
                    Logger.log("SKYSIGHT_CAMERA", LogLevel.INFO, "Forecast navigation data changed: ${layerData.selectedLayerId} ${layerData.selectedDate} ${layerData.currentTime.display} UTC")
                    try {
                        SkysightTilingControllerV2.handleNavigationChange(
                            module = skysightModule,
                            globalState = globalState,
                            layerId = layerData.selectedLayerId,
                            date = layerData.selectedDate!!,
                            timePair = layerData.currentTime
                        )
                    } catch (e: Throwable) {
                        Logger.log("SKYSIGHT_CAMERA", LogLevel.ERROR, "Error in forecast navigation change pipeline: ${e.message}")
                    }
                }
            }
        }

        // Real-time timestamp changes
        if (satelliteEnabled || localRainEnabled) {
            LaunchedEffect(Unit) {
                combine(
                    skysightModule.realTimeTimestamp,
                    snapshotFlow { Pair(satelliteEnabled, localRainEnabled) }
                ) { timestamp, layerStates ->
                    timestamp to layerStates
                }.collect { (timestamp, layerStates) ->
                    val (satEnabled, rainEnabled) = layerStates
                    val layersEnabled = satEnabled || rainEnabled
                    if (layersEnabled) {
                        Logger.log("SKYSIGHT_CAMERA", LogLevel.INFO, "Real-time timestamp changed to ${timestamp}")
                        try {
                            org.mountaincircles.app.modules.skysight.logic.controllers.RealTimeTilingController.handleTileUpdate(
                                module = skysightModule,
                                globalState = globalState,
                                timePair = null, // Use timestamp from module
                                isNavigation = true
                            )
                        } catch (e: Throwable) {
                            Logger.log("SKYSIGHT_CAMERA", LogLevel.ERROR, "Error in real-time timestamp pipeline: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    // LiveTracking aircraft data polling with smart camera-aware repolling
    if (liveTrackingModule != null) {
        val liveTrackingData by liveTrackingModule.layerDataFlow.collectAsState()

        // Start/stop polling based on visibility
        LaunchedEffect(liveTrackingData.visibilityMode) {
            if (liveTrackingData.visibilityMode != LiveTrackingVisibilityMode.ALL_HIDDEN) {
                // Start initial polling
                liveTrackingModule.handleAircraftPolling(cameraState)
                // Start the 20s polling cycle
                liveTrackingModule.startPollingCycle(cameraState)
            } else {
                // Stop polling when hidden
                liveTrackingModule.stopPollingCycle()
            }
        }

        // Handle camera moves with debouncing and smart repolling
        if (liveTrackingData.visibilityMode != LiveTrackingVisibilityMode.ALL_HIDDEN) {
            LaunchedEffect(cameraState.position) {
                liveTrackingModule.handleCameraMove(cameraState)
            }
        }
    }

    // MapLibre Map with OSM tiles - MapLibre handles tile fetching natively
    Logger.log("MAP", LogLevel.INFO, "Using OpenStreetMap tiles - MapLibre handles fetching natively")

    MaplibreMap(
        modifier = Modifier.fillMaxSize(),
        cameraState = cameraState,
        baseStyle = BaseStyle.Json(osmMapStyle), // OpenStreetMap tiles handled natively
        options = MapOptions(
            ornamentOptions = OrnamentOptions(
                isLogoEnabled = false, // Remove MapLibre logo
                isAttributionEnabled = false, // Remove attribution text
                isScaleBarEnabled = false, // Remove scale bar
                isCompassEnabled = false // Enable compass ornament
            ),
            gestureOptions = GestureOptions(
                isRotateEnabled = !northLocked // Disable rotation when north is locked
            )
        ),
        onMapClick = { position, offset ->
            // ✅ Store clicked position in global state for modules to use
            val clickEvent = org.mountaincircles.app.ui.map.MapClickEvent(
                latitude = position.latitude,
                longitude = position.longitude,
                zoom = cameraState.position.zoom,
                screenX = offset.x.value,
                screenY = offset.y.value
            )

            // Store clicked position in case modules want to show markers
            globalState.setClickedPosition(clickEvent)

            ClickResult.Pass // Allow layer click handlers to process
        }
    ) {
        // Click handling is now done directly in the layers using MapLibre's onClick parameter
        // No need for overlay since we have proper feature querying

        // Map content/layers can be added here using Composables
        LaunchedEffect(Unit) {
            Logger.log("MAP", LogLevel.INFO, "MapLibre map with minimal base style loaded!")
            Logger.log("MAP", LogLevel.INFO, "Calling globalState.onMapReady() to trigger module layer rendering...")

            // TODO: Here we should capture the actual map instance
            // and pass it to globalState.setMapLibreReference()
            // For now, call onMapReady which will trigger module layer addition attempts
            globalState.onMapReady()

            Logger.log("MAP", LogLevel.INFO, "globalState.onMapReady() completed")
        }

        // Add all module layers using the new LayerManager system
        // This provides hybrid support: new system if layers registered, fallback to old system
        // LayerManagerComposables handles its own recomposition through collectAsState()
        LayerManagerComposables(globalState)

        // STEP 3.3: Add visible LocationPuck with blue colors (matching previous marker)
        // Render LocationPuck when location infrastructure exists and data is available
        // Use stable locationState from app level (demo app pattern)
        locationState.locationState?.let { state ->
            // Camera centering - runs once when first location is received
            LaunchedEffect(state.location, hasCenteredOnLocation) {
                if (!hasCenteredOnLocation && state.location != null) {
                    val location = state.location!!
                    Logger.log("LOCATION", LogLevel.INFO, "First location fix received, centering map")

                    // Center camera on user's location (following demo app pattern)
                    cameraState.animateTo(
                        CameraPosition(
                            target = location.position, // Use position property like in demo app
                            zoom = 10.0, // Good zoom level for location context
                            bearing = 0.0,
                            tilt = 0.0
                        )
                    )

                    hasCenteredOnLocation = true // Ensure this only happens once
                    Logger.log("LOCATION", LogLevel.INFO, "Map centered on user location (first time only)")
                }
            }

            // LocationPuck with stable locationState
            if (state.location != null) {
                // Bearing line extending from current position (just below the puck) - only render if length > 0
                androidx.compose.runtime.key("bearing_line") {
                    val currentLocation = state.location!!
                    val vectorLengthKm by globalState.vectorLengthKm.collectAsState()
                    val vectorWidthDp by globalState.vectorWidthDp.collectAsState()

                    // Only render bearing line if length > 0
                    if (vectorLengthKm > 0f) {
                        val bearingLineGeoJson = remember(currentLocation, vectorLengthKm) {
                            // Logger.log("GEOLOCATION_VECTOR", LogLevel.DEBUG, "Recomputing bearing line geometry - length: ${vectorLengthKm}km, bearing: ${currentLocation.bearing ?: 0.0}°")
                            val bearing = currentLocation.bearing ?: 0.0 // Default to north if no bearing
                            val distanceMeters = vectorLengthKm * 1000.0 // Convert km to meters
                            val earthRadius = 6371000.0

                            // Convert bearing from degrees to radians
                            val bearingRad = bearing * PI / 180.0

                            // Calculate endpoint using geodesic approximation
                            val latOffset = (distanceMeters / earthRadius) * (180.0 / PI) * cos(bearingRad)
                            val lngOffset = (distanceMeters / earthRadius) * (180.0 / PI) * sin(bearingRad) / cos(currentLocation.position.latitude * PI / 180.0)

                            val startLng = currentLocation.position.longitude
                            val startLat = currentLocation.position.latitude
                            val endLng = startLng + lngOffset
                            val endLat = startLat + latOffset

                            """
                            {
                                "type": "Feature",
                                "properties": {},
                                "geometry": {
                                    "type": "LineString",
                                    "coordinates": [
                                        [${startLng}, ${startLat}],
                                        [${endLng}, ${endLat}]
                                    ]
                                }
                            }
                            """.trimIndent()
                        }

                        LineLayer(
                            id = "bearing_line",
                            source = rememberGeoJsonSource(
                                data = GeoJsonData.JsonString(bearingLineGeoJson)
                            ),
                            color = const(Color.Black), // black
                            width = const(vectorWidthDp.dp), // Dynamic line width from settings
                            opacity = const(0.8f),
                            cap = const(LineCap.Round),
                            join = const(LineJoin.Round)
                        )
                    }
                }

                key(locationMarkerSettings.dotRadius, locationMarkerSettings.bearingSize) {
                    org.maplibre.compose.location.LocationPuck(
                        idPrefix = "location_marker",
                        locationState = state,
                        cameraState = cameraState,
                        oldLocationThreshold = 10.seconds, // Mark location as "old" immediately
                        showBearingAccuracy = false, // No bearing accuracy wedge
                        // Use blue colors matching the previous marker (no accuracy rings)
                        colors = org.maplibre.compose.location.LocationPuckColors(
                            dotFillColorCurrentLocation = androidx.compose.ui.graphics.Color(0xFF2196F3), // Material Blue
                            bearingColor = androidx.compose.ui.graphics.Color(0xFF2196F3), // Same blue as main dot
                            accuracyFillColor = androidx.compose.ui.graphics.Color.Transparent, // No accuracy fill
                            accuracyStrokeColor = androidx.compose.ui.graphics.Color.Transparent // No accuracy border
                        ),
                        sizes = org.maplibre.compose.location.LocationPuckSizes(
                            dotRadius = locationMarkerSettings.dotRadius.dp,
                            bearingSize = locationMarkerSettings.bearingSize.dp,
                            accuracyStrokeWidth = 0.dp // No accuracy ring
                        )
                    )
                }

                // Circle centered on current position (using LineLayer for outline) - only render if radius > 0
                androidx.compose.runtime.key("position_circle") {
                    val currentPos = state.location!!.position
                    val circleRadiusKm by globalState.circleRadiusKm.collectAsState()
                    val circleWidthDp by globalState.circleWidthDp.collectAsState()

                    // Only render circle if radius > 0
                    if (circleRadiusKm > 0f) {
                        val circleGeoJson = remember(currentPos, circleRadiusKm) {
                        // Logger.log("GEOLOCATION_CIRCLE", LogLevel.DEBUG, "Recomputing circle geometry - radius: ${circleRadiusKm}km")
                        // Create a circular polygon with 32 points (like a proper circle)
                        val points = mutableListOf<Pair<Double, Double>>()
                        val earthRadius = 6371000.0 // Earth radius in meters
                        val radiusMeters = circleRadiusKm * 1000.0 // Convert km to meters

                        // Generate 32 points around the circle
                        for (i in 0..31) {
                            val angle = (i.toDouble() / 32.0) * 2 * PI
                            val latOffset = (radiusMeters / earthRadius) * (180.0 / PI)
                            val lngOffset = (radiusMeters / earthRadius) * (180.0 / PI) / cos(currentPos.latitude * PI / 180.0)

                            val lat = currentPos.latitude + latOffset * sin(angle)
                            val lng = currentPos.longitude + lngOffset * cos(angle)
                            points.add(Pair(lng, lat))
                        }
                        // Close the polygon
                        points.add(points.first())

                        """
                        {
                            "type": "Feature",
                            "properties": {},
                            "geometry": {
                                "type": "Polygon",
                                "coordinates": [[${points.joinToString(",") { "[${it.first}, ${it.second}]" }}]]
                            }
                        }
                        """.trimIndent()
                    }

                        LineLayer(
                            id = "position_circle",
                            source = rememberGeoJsonSource(
                                data = GeoJsonData.JsonString(circleGeoJson)
                            ),
                            color = const(Color.Black),
                            width = const(circleWidthDp.dp), // Dynamic line width from settings
                            opacity = const(1.0f)
                        )
                    }
                }

            }
        }

        // ✅ ADD: Core marker layer (shared by all modules)
        CoreMarkerLayer()
    }
}
