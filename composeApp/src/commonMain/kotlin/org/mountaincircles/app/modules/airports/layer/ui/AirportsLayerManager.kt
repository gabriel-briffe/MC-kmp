package org.mountaincircles.app.modules.airports.layer.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.expressions.dsl.*
import org.maplibre.spatialk.geojson.Feature
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.modules.airports.layer.ui.composables.AirportsClickAreaLayer
import org.mountaincircles.app.modules.airports.logic.data.AirportColors
import org.mountaincircles.app.modules.airports.logic.data.AirportsLayerDisplayData
import org.mountaincircles.app.ui.map.*

/**
 * Airports LayerManager implementation
 * Basic version - displays all airports as blue dots
 */
@Stable
class AirportsLayerManager(private val module: AirportsModule) {

    // Track registered layer IDs for BaseStatefulModule adapter
    private val layerIds = mutableListOf<String>()
    val layerIdsPublic: List<String> get() = layerIds

    /**
     * Check if airports layer is currently visible
     */
    // Public getter for current visibility state (from module state, like airspace)
    fun isLayerCurrentlyVisible(): Boolean = module.airportsState.value.airportsVisibility

    // ✅ URI-ONLY APPROACH: No internal caching - URI comes from module StateFlow

    // ✅ Removed cached URI management - MapLibre handles filtering dynamically

    /**
     * Prepare airports layers
     */
    suspend fun prepareAirportsLayers() {
        try {
            Logger.log("AIRPORTS", LogLevel.DEBUG, "Preparing airports layer")

            if (module.airportsStorage.hasAirportsData()) {
                Logger.log("AIRPORTS", LogLevel.INFO, "Airports data available - cache warming handled by module StateFlow")
                // URI initialization is now handled by the module's filteredUri StateFlow
            } else {
                Logger.log("AIRPORTS", LogLevel.DEBUG, "No airports data available")
            }
        } catch (e: Exception) {
            Logger.log("AIRPORTS", LogLevel.ERROR, "Error preparing airports layer: ${e.message}", e)
        }
    }

    @Composable
    private fun AirportsCirclesLayerContent() {
        Logger.log("AIRPORTS", LogLevel.DEBUG, "🎯 AirportsCirclesLayerContent composable called")
        RenderAirportsCircles()
    }

    @Composable
    private fun AirportsLabelsLayerContent() {
        Logger.log("AIRPORTS", LogLevel.DEBUG, "🎯 AirportsLabelsLayerContent composable called")
        RenderAirportsLabels()
    }

    @Composable
    fun RenderAirportsCircles() {
        Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Airports circles layer recomposing")

        // ✅ AIRSPACE-STYLE: Simple visibility + raw data + MapLibre filtering
        val isVisible by module.layerVisibility.collectAsState()
        val iconSize by module.iconSize.collectAsState()
        val iconsMinZoom by module.iconsMinZoom.collectAsState()
        val labelsMinZoom by module.labelsMinZoom.collectAsState()

        // 🎯 Selective reactivity - collect only needed fields
        val currentVisibleTypes by module.currentVisibleTypes.collectAsState()
        val disabledIds by module.disabledAirportIds.collectAsState()

        Logger.log("AIRPORTS", LogLevel.DEBUG, "🎯 Rendering airports circles with MapLibre filtering (visible: $isVisible, visibleTypes: ${currentVisibleTypes.size})")

        // Early exit if not visible
        if (!isVisible) {
            Logger.log("AIRPORTS", LogLevel.DEBUG, "Airports circles not rendered - visibility: $isVisible")
            return
        }

        // Get raw airports data URI
        val rawAirportsUri = run {
            val fileManager = org.mountaincircles.app.io.getGlobalFileManager()
            val dataDir = fileManager.getAppDataDirectory()
            val airportsDir = "$dataDir/${org.mountaincircles.app.modules.airports.logic.AirportsConstants.AIRPORTS_DIR}"
            val filePath = "$airportsDir/${org.mountaincircles.app.modules.airports.logic.AirportsConstants.AIRPORTS_GEOJSON_FILE}"
            "file://$filePath"
        }

        // Create GeoJSON source from raw data
        val geoJsonSource = rememberGeoJsonSource(
            data = GeoJsonData.Uri(rawAirportsUri)
        )

        // Create MapLibre filter for airport visibility
        val airportTypeFilter = remember(currentVisibleTypes, disabledIds) {
            // Check if airport is disabled first, then fall back to type logic
            switch(
                condition(
                    test = const(disabledIds.toList()).contains(feature["_id"].asString()),
                    output = const(currentVisibleTypes.toList()).contains(const("disabled"))
                ),
                fallback = const(currentVisibleTypes.toList()).contains(feature["type"].asString())
            )
        }

        // Render airports as circles with MapLibre filtering and dynamic colors
        CircleLayer(
            id = "airports_circles",
            source = geoJsonSource,
            filter = airportTypeFilter,  // 🎯 MapLibre filtering instead of pre-filtered data
            visible = isVisible,
            minZoom = iconsMinZoom,
            radius = interpolate(
                exponential(2.0f),
                zoom(),
                0.0 to const(0.5f.dp),                // Zoom 0: 0 dp (invisible)
                labelsMinZoom to const(iconSize.dp)  // Zoom at labelsMinZoom: full size
            ),
            color = switch(
                condition(
                    test = const(disabledIds.toList()).contains(feature["_id"].asString()),
                    output = const(Color.Gray)
                ),
                condition(
                    test = feature["type"].asString().eq(const("International Airport")),
                    output = const(AirportColors.AirportType.INTERNATIONAL_AIRPORT)
                ),
                condition(
                    test = feature["type"].asString().eq(const("Military Aerodrome")),
                    output = const(AirportColors.AirportType.MILITARY_AERODROME)
                ),
                condition(
                    test = feature["type"].asString().eq(const("Airport (civil/military)")),
                    output = const(AirportColors.AirportType.AIRPORT_CIVIL_MILITARY)
                ),
                condition(
                    test = feature["type"].asString().eq(const("Airport resp. Airfield IFR")),
                    output = const(AirportColors.AirportType.AIRPORT_IFR)
                ),
                condition(
                    test = feature["type"].asString().eq(const("Airfield Civil")),
                    output = const(AirportColors.AirportType.AIRFIELD_CIVIL)
                ),
                condition(
                    test = feature["type"].asString().eq(const("Glider Site")),
                    output = const(AirportColors.AirportType.GLIDER_SITE)
                ),
                condition(
                    test = feature["type"].asString().eq(const("Ultra Light Flying Site")),
                    output = const(AirportColors.AirportType.ULTRALIGHT_SITE)
                ),
                condition(
                    test = feature["type"].asString().eq(const("Altiport")),
                    output = const(AirportColors.AirportType.ALTIPORT)
                ),
                fallback = const(AirportColors.AirportType.OTHER)
            ),
            strokeColor = const(Color.White),  // White border
            strokeWidth = const(1.5f.dp)  // 1.5dp border width
        )

        Logger.log("AIRPORTS", LogLevel.DEBUG, "Airports circles layer rendered")
    }

    @Composable
    fun RenderAirportsLabels() {
        Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Airports labels layer recomposing")

        // ✅ AIRSPACE-STYLE: Simple visibility + raw data + MapLibre filtering
        val isVisible by module.layerVisibility.collectAsState()
        val labelSize by module.labelSize.collectAsState()
        val labelsMinZoom by module.labelsMinZoom.collectAsState()

        // 🎯 Selective reactivity - collect only needed fields
        val currentVisibleTypes by module.currentVisibleTypes.collectAsState()
        val disabledIds by module.disabledAirportIds.collectAsState()

        Logger.log("AIRPORTS", LogLevel.DEBUG, "🎯 Rendering airports labels with MapLibre filtering (visible: $isVisible, visibleTypes: ${currentVisibleTypes.size})")

        // Early exit if not visible
        if (!isVisible) {
            Logger.log("AIRPORTS", LogLevel.DEBUG, "Airports labels not rendered - visibility: $isVisible")
            return
        }

        // Get raw airports data URI
        val rawAirportsUri = run {
            val fileManager = org.mountaincircles.app.io.getGlobalFileManager()
            val dataDir = fileManager.getAppDataDirectory()
            val airportsDir = "$dataDir/${org.mountaincircles.app.modules.airports.logic.AirportsConstants.AIRPORTS_DIR}"
            val filePath = "$airportsDir/${org.mountaincircles.app.modules.airports.logic.AirportsConstants.AIRPORTS_GEOJSON_FILE}"
            "file://$filePath"
        }

        // Create GeoJSON source from raw data
        val geoJsonSource = rememberGeoJsonSource(
            data = GeoJsonData.Uri(rawAirportsUri)
        )

        // Create MapLibre filter for airport types (same as circles)
        val airportTypeFilter = remember(currentVisibleTypes, disabledIds) {
            // Check if airport is disabled first, then fall back to type logic
            switch(
                condition(
                    test = const(disabledIds.toList()).contains(feature["_id"].asString()),
                    output = const(currentVisibleTypes.toList()).contains(const("disabled"))
                ),
                fallback = const(currentVisibleTypes.toList()).contains(feature["type"].asString())
            )
        }

        // Render airport labels with MapLibre filtering
        SymbolLayer(
            id = "airports_labels",
            source = geoJsonSource,
            filter = airportTypeFilter,  // 🎯 MapLibre filtering instead of pre-filtered data
            visible = isVisible,
            minZoom = labelsMinZoom,
            textField = feature["name"].asString(),  // Display airport name
            textFont = const(listOf("Open Sans Regular")),
            textSize = const(labelSize.sp),
            textColor = const(Color.Black),  // Black text
            textHaloColor = const(Color.White),  // White halo
            textHaloWidth = const(2.0f.dp),  // 2dp halo width
            textOffset = offset(0.em, 1.em)  // Offset below the circle
        )

        Logger.log("AIRPORTS", LogLevel.DEBUG, "Airports labels layer rendered")
    }


    // Initialize layers when the module is ready
    suspend fun initializeLayers() {
        Logger.log("AIRPORTS", LogLevel.DEBUG, "Initializing airports layers")

        // Prepare airports layers
        prepareAirportsLayers()

        // Register airports layers with the LayerManager system
        registerAirportsLayers()

        Logger.log("AIRPORTS", LogLevel.INFO, "Airports layers registered with map system")
    }

    private fun registerAirportsLayers() {
        try {
            Logger.log("AIRPORTS", LogLevel.DEBUG, "Registering airports layers with LayerManager")

            // Register the airports circles layer (just above maps_terrain)
            val airportsCirclesLayerId = LayerRegistrationHelper.registerLayer(
                moduleId = "airports",
                layerName = "airports_circles",
                zIndex = 10 * LayerZIndex.getZIndex("airports_circles"), // Position 1 in layerOrder
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = true,
                description = "Airport symbols as blue circles",
                tags = setOf("airports", "symbols", "geojson"),
                composable = { AirportsCirclesLayerContent() }
            )
            layerIds.add(airportsCirclesLayerId)

            // Register the airports labels layer (above circles)
            val airportsLabelsLayerId = LayerRegistrationHelper.registerLayer(
                moduleId = "airports",
                layerName = "airports_labels",
                zIndex = 10 * LayerZIndex.getZIndex("airports_labels"), // Position 2 in layerOrder
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = false,
                description = "Airport labels showing airport names",
                tags = setOf("airports", "labels", "text", "geojson"),
                composable = { AirportsLabelsLayerContent() }
            )
            layerIds.add(airportsLabelsLayerId)

            // Register the airports click areas layer (above labels, lowest click priority)
            val airportsClickLayerId = LayerRegistrationHelper.registerLayer(
                moduleId = "airports",
                layerName = "airports_click_areas",
                zIndex = 10 * LayerZIndex.getZIndex("airports_click_areas"), // Position 3 in layerOrder
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = true,
                description = "Airport click areas (invisible circles for interaction)",
                tags = setOf("airports", "click", "interaction", "geojson"),
                composable = { AirportsClickAreaLayer(module, this) }
            )
            layerIds.add(airportsClickLayerId)

            Logger.log("AIRPORTS", LogLevel.DEBUG, "Airports layers registered - circles: $airportsCirclesLayerId, labels: $airportsLabelsLayerId, click: $airportsClickLayerId")
        } catch (e: Exception) {
            Logger.log("AIRPORTS", LogLevel.ERROR, "Failed to register airports layers: ${e.message}", e)
        }
    }
}