package org.mountaincircles.app.modules.airspace.layer.ui

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.layers.*
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.expressions.dsl.*
import org.maplibre.compose.expressions.value.*
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature.Companion.getStringProperty
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.MarkerStyle
import org.mountaincircles.app.state.getGlobalState
import org.mountaincircles.app.ui.map.*
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.modules.airspace.AirspaceModule
import kotlinx.serialization.json.*
import org.maplibre.spatialk.geojson.Feature
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceColors
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceFeatureData
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceSources
import org.mountaincircles.app.modules.airspace.layer.logic.AirspaceLayerFilteringController
import org.mountaincircles.app.modules.airspace.logic.AirspaceConstants

/**
 * Airspace LayerManager implementation
 *
 * Renders airspace data with the same colors as the Android-native app
 */
  class AirspaceLayerManager(private val module: AirspaceModule) {

    // Track registered layer IDs for BaseStatefulModule adapter
    private val layerIds = mutableListOf<String>()
    val layerIdsPublic: List<String> get() = layerIds

    // Filtering controller for highlight operations only
    private val filteringController = AirspaceLayerFilteringController(module)

    // Public getter for current visibility state (from module state)
    fun isLayerCurrentlyVisible(): Boolean = module.airspaceState.value.isVisible

    // ✅ Removed local state - composables now collect from module state directly

    // Highlight state - starts empty, gets populated when user clicks a card
    private var highlightData by mutableStateOf("""{"type":"FeatureCollection","features":[]}""")

    // Shared GeoJSON source for both visual and click layers
    private var sharedGeoJsonSource by mutableStateOf<GeoJsonSource?>(null)

    // Public accessor for the shared GeoJSON source
    val currentSharedGeoJsonSource: GeoJsonSource? get() = sharedGeoJsonSource

    fun getColorForType(type: String?): Color {
        return type?.let { AirspaceColors.getAirspaceTypeColor(it) } ?: Color.Black
    }

    // Ordered types mirroring Android-native AIRSPACE_TYPE_ORDER
    val AIRSPACE_TYPE_ORDER: List<String> = listOf(
        "A","C","D","E","G","PROHIBITED","RESTRICTED","DANGER","MTA",
        "OVERFLIGHT_RESTRICTION","GLIDING_SECTOR","ACTIVITY","TRA","RMZ","TMZ",
        "FIS","ATZ","VFRSEC","FIR","UNCLASSIFIED"
    )

    // Filtering is now handled by MapLibre expressions - no pre-filtering needed

    // No caching needed - MapLibre handles filtering dynamically

    // Prepare airspace layers - no caching needed with MapLibre filtering
    suspend fun prepareAirspaceLayers() {
        try {
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Initializing airspace layer with MapLibre filtering")

            if (module.hasAirspaceData()) {
                Logger.log("AIRSPACE", LogLevel.INFO, "Airspace data available - using MapLibre dynamic filtering")
            } else {
                Logger.log("AIRSPACE", LogLevel.DEBUG, "No airspace data available")
            }
        } catch (e: Exception) {
            Logger.log("AIRSPACE", LogLevel.ERROR, "Error initializing airspace layer: ${e.message}", e)
        }
    }

    @Composable
    private fun AirspaceLayerContent() {
        // This is the composable that gets called by the layer registration system
        Logger.log("AIRSPACE", LogLevel.DEBUG, "🎯 AirspaceLayerContent composable called")
        RenderAirspaceLayers()
    }

    @Composable
    fun RenderAirspaceLayers() {
        Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Airspace layers recomposing")

        // ✅ SIMPLIFIED: Only listen to visibility
        val isVisible by module.layerVisibility.collectAsState()

        // 🎯 Selective reactivity - collect only needed fields
        val currentVisibleTypes by module.currentVisibleTypes.collectAsState()

        Logger.log("AIRSPACE", LogLevel.DEBUG, "🎯 Rendering airspace layers with MapLibre filtering (visible: $isVisible, visibleTypes: ${currentVisibleTypes.size})")

        // 🚀 TEST: Load raw airspace data directly (bypass caching)
        val rawAirspaceUri = run {
            val fileManager = org.mountaincircles.app.io.getGlobalFileManager()
            val dataDir = fileManager.getAppDataDirectory()
            val airspaceDir = "$dataDir/${AirspaceConstants.AIRSPACE_DIR}"
            val filePath = "$airspaceDir/${AirspaceConstants.AIRSPACE_GEOJSON_FILE}"
            "file://$filePath"
        }

        // Only render if we have airspace data
        if (rawAirspaceUri.isBlank()) {
            Logger.log("AIRSPACE", LogLevel.DEBUG, "No airspace data available")
            return
        }

        Logger.log("AIRSPACE", LogLevel.DEBUG, "Using raw airspace data with MapLibre filtering: $rawAirspaceUri")

        // Create SHARED GeoJSON source using RAW airspace data
        val sharedGeoJsonSource = rememberGeoJsonSource(
            data = GeoJsonData.Uri(rawAirspaceUri)
        )

        // Store the shared source for the click layer to access
        this.sharedGeoJsonSource = sharedGeoJsonSource

        // Create MapLibre filter for airspace types
        val airspaceTypeFilter = remember(currentVisibleTypes) {
            if (currentVisibleTypes.isNotEmpty()) {
                // Only show airspace types that are in currentVisibleTypes
                const(currentVisibleTypes.toList()).contains(feature["type"].asString())
            } else {
                // When no types are selected, match nothing (show no features)
                const(false)
            }
        }

        Logger.log("AIRSPACE", LogLevel.DEBUG, "🎯 TEST MODE: Applied MapLibre filter for ${currentVisibleTypes.size} visible types: ${currentVisibleTypes.joinToString()}")

        // Line layer (colored lines) - main airspace rendering with Android-native colors
        LineLayer(
            id = "airspace-line",
            source = sharedGeoJsonSource,
            filter = airspaceTypeFilter,  // 🎯 MapLibre filtering instead of pre-filtered data
            visible = isVisible,  // ← Control visibility without destroying layer!
            sortKey = feature["upperLimitMeters"].asNumber(),  // Sort by altitude (higher first)
            color = switch(
                input = feature["type"].asString(),
                case("A", const(AirspaceColors.AirspaceType.CLASS_A)),
                case("C", const(AirspaceColors.AirspaceType.CLASS_C)),
                case("D", const(AirspaceColors.AirspaceType.CLASS_D)),
                case("E", const(AirspaceColors.AirspaceType.CLASS_E)),
                case("G", const(AirspaceColors.AirspaceType.CLASS_G)),
                case("PROHIBITED", const(AirspaceColors.AirspaceType.PROHIBITED)),
                case("DANGER", const(AirspaceColors.AirspaceType.DANGER)),
                case("RESTRICTED", const(AirspaceColors.AirspaceType.RESTRICTED)),
                case("FIR", const(AirspaceColors.AirspaceType.FIR)),
                case("FIS", const(AirspaceColors.AirspaceType.FIS)),
                case("OVERFLIGHT_RESTRICTION", const(AirspaceColors.AirspaceType.OVERFLIGHT_RESTRICTION)),
                case("TRA", const(AirspaceColors.AirspaceType.TRA)),
                case("UNCLASSIFIED", const(AirspaceColors.AirspaceType.UNCLASSIFIED)),
                case("ACTIVITY", const(AirspaceColors.AirspaceType.ACTIVITY)),
                case("GLIDING_SECTOR", const(AirspaceColors.AirspaceType.GLIDING_SECTOR)),
                case("MTA", const(AirspaceColors.AirspaceType.MTA)),
                case("TMZ", const(AirspaceColors.AirspaceType.TMZ)),
                fallback = const(Color.Black)
            ),
            width = const(2.0f.dp)
        )
    }



    // Initialize layers when the module is ready
    suspend fun initializeLayers() {
        Logger.log("AIRSPACE", LogLevel.DEBUG, "Initializing airspace layers")

        // Prepare airspace layers by warming cache
        prepareAirspaceLayers()

        // Register airspace layers with the LayerManager system (initially hidden)
        registerAirspaceLayers()

        Logger.log("AIRSPACE", LogLevel.INFO, "Airspace layers registered with map system")
    }


    private fun registerAirspaceLayers() {
        try {
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Registering airspace layers with LayerManager")

            // Register the airspace layer
            val boundariesLayerId = LayerRegistrationHelper.registerLayer(
                moduleId = "airspace",
                layerName = "airspace_boundaries",
                zIndex = 10 * LayerZIndex.getZIndex("airspace_boundaries"),
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = false,
                description = "Airspace boundary layers with color coding",
                tags = setOf("airspace", "boundaries", "geojson"),
                composable = { AirspaceLayerContent() }
            )
            layerIds.add(boundariesLayerId)

            // Register the airspace click layer (transparent, for interaction)
            val clickLayerId = LayerRegistrationHelper.registerLayer(
                moduleId = "airspace",
                layerName = "airspace_click_areas",
                zIndex = 10 * LayerZIndex.getZIndex("airspace_click_areas"),
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = false,  // Click handling done in FillLayer onClick
                description = "Transparent airspace click areas (invisible, for interaction only)",
                tags = setOf("airspace", "click", "transparent", "geojson"),
                composable = { AirspaceClickLayer(module) }
            )
            layerIds.add(clickLayerId)

            // Register the airspace highlight layer (turquoise overlay)
            val highlightLayerId = LayerRegistrationHelper.registerLayer(
                moduleId = "airspace",
                layerName = "airspace_highlight",
                zIndex = 10 * LayerZIndex.getZIndex("airspace_highlight"),
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = false,  // Highlight layer is not clickable
                description = "Turquoise highlight overlay for selected airspace features",
                tags = setOf("airspace", "highlight", "turquoise", "overlay"),
                composable = { AirspaceHighlightLayer(highlightData) }
            )
            layerIds.add(highlightLayerId)


            Logger.log("AIRSPACE", LogLevel.INFO, "Airspace layers registered successfully")
        } catch (e: Exception) {
            Logger.log("AIRSPACE", LogLevel.ERROR, "Failed to register airspace layers: ${e.message}", e)
        }
    }

    /**
     * Apply type filter to the airspace layers
     */
    fun applyTypeFilter(visibleTypes: Set<String>) {
        Logger.log("AIRSPACE", LogLevel.INFO, "Applying type filter: ${visibleTypes.joinToString()}")

        // ✅ No longer need to store local state - composable collects from module state directly
        // The composable will automatically recompose when module state changes

        Logger.log("AIRSPACE", LogLevel.INFO, "Filter applied - MapLibre will filter by types: ${visibleTypes.joinToString()}")
    }

    // Update highlight with a specific feature by AI field
    fun updateHighlight(aiField: String?) {
        if (aiField.isNullOrBlank()) {
            // Clear highlight
            highlightData = filteringController.clearHighlight()
            Logger.log("AIRSPACE_HIGHLIGHT", LogLevel.DEBUG, "Highlight cleared")
            return
        }

        // Get raw airspace URI for highlighting
        val rawAirspaceUri = run {
            val fileManager = org.mountaincircles.app.io.getGlobalFileManager()
            val dataDir = fileManager.getAppDataDirectory()
            val airspaceDir = "$dataDir/${AirspaceConstants.AIRSPACE_DIR}"
            val filePath = "$airspaceDir/${AirspaceConstants.AIRSPACE_GEOJSON_FILE}"
            "file://$filePath"
        }

        highlightData = filteringController.createHighlightData(aiField, rawAirspaceUri)
    }

    // Clear highlight
    fun clearHighlight() {
        updateHighlight(null)
    }

    // Cleanup layers when module is destroyed
    fun cleanup() {
        Logger.log("AIRSPACE", LogLevel.INFO, "Cleaning up airspace layers")

        // Clear highlight
        clearHighlight()

        // Clear layer IDs
        layerIds.clear()

        // Unregister both airspace layers
        try {
            LayerRegistrationHelper.layerManager.unregisterLayer("airspace_boundaries")
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Airspace boundaries layer unregistered")
        } catch (e: Exception) {
            Logger.log("AIRSPACE", LogLevel.WARN, "Error unregistering airspace boundaries layer: ${e.message}", e)
        }

        try {
            LayerRegistrationHelper.layerManager.unregisterLayer("airspace_click_areas")
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Airspace click layer unregistered")
        } catch (e: Exception) {
            Logger.log("AIRSPACE", LogLevel.WARN, "Error unregistering airspace click layer: ${e.message}", e)
        }

        try {
            LayerRegistrationHelper.layerManager.unregisterLayer("airspace_highlight")
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Airspace highlight layer unregistered")
        } catch (e: Exception) {
            Logger.log("AIRSPACE", LogLevel.WARN, "Error unregistering airspace highlight layer: ${e.message}", e)
        }

        try {
            LayerRegistrationHelper.layerManager.unregisterLayer("airspace_click_marker")
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Airspace click marker layer unregistered")
        } catch (e: Exception) {
            Logger.log("AIRSPACE", LogLevel.WARN, "Error unregistering airspace click marker layer: ${e.message}", e)
        }
    }


}

/**
 * Transparent airspace click layer composable
 * This layer renders the same airspace geometries as completely transparent areas for click detection
 * Layer visibility is controlled by the visible property (not early return)
 * 0% opacity - completely invisible, for interaction only
 */
    @Composable
    private fun AirspaceClickLayer(module: AirspaceModule) {
        Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Airspace click layer recomposing")

        // ✅ SIMPLIFIED: Only listen to visibility
        val isVisible by module.layerVisibility.collectAsState()

        // 🎯 Selective reactivity - collect only needed fields
        val visibleTypes by module.currentVisibleTypes.collectAsState()

    // Get raw airspace URI
    val rawAirspaceUri = run {
        val fileManager = org.mountaincircles.app.io.getGlobalFileManager()
        val dataDir = fileManager.getAppDataDirectory()
        val airspaceDir = "$dataDir/${AirspaceConstants.AIRSPACE_DIR}"
        val filePath = "$airspaceDir/${AirspaceConstants.AIRSPACE_GEOJSON_FILE}"
        "file://$filePath"
    }

    Logger.log("AIRSPACE_CLICK_LAYER", LogLevel.DEBUG, "AirspaceClickLayer: Checking conditions - " +
        "isVisible=$isVisible, hasValidUri=${rawAirspaceUri.isNotBlank()}")

    // Only render if has valid URI and is visible
    if (rawAirspaceUri.isBlank() || !isVisible) {
        Logger.log("AIRSPACE_CLICK_LAYER", LogLevel.DEBUG, "AirspaceClickLayer: Conditions NOT met - click layer not rendered")
        return
    }

    Logger.log("AIRSPACE_CLICK_LAYER", LogLevel.DEBUG, "AirspaceClickLayer using raw data with MapLibre filtering: $rawAirspaceUri")

    // Create GeoJSON source using the RAW airspace data
    val geoJsonSource = rememberGeoJsonSource(
        data = GeoJsonData.Uri(rawAirspaceUri)
    )

    // Create the same MapLibre filter for click layer
    val clickLayerFilter = remember(visibleTypes) {
        if (visibleTypes.isNotEmpty()) {
            const(visibleTypes.toList()).contains(feature["type"].asString())
        } else {
            // When no types are selected, match nothing (show no clickable features)
            const(false)
        }
    }

    // Render a completely transparent fill layer that captures clicks
    // This creates clickable areas for all airspace polygons without visual interference
    FillLayer(
        id = "airspace-click-fill",
        source = geoJsonSource,
        filter = clickLayerFilter,  // 🎯 Same MapLibre filtering for consistency
        visible = isVisible,       // ← Control visibility without destroying layer!
        color = const(Color.Transparent),      // Completely transparent
        opacity = const(0.0f),                 // 0% opacity - completely invisible
        onClick = { features ->
            // Handle click using MapLibre's feature querying
            Logger.log("AIRSPACE_CLICK", LogLevel.DEBUG, "FillLayer onClick: Received ${features.size} features")

            if (features.isNotEmpty()) {
                Logger.log("AIRSPACE_CLICK", LogLevel.INFO, "🛩️ Airspace clicked - ${features.size} feature(s) detected:")

                // Debug: Log all available properties in the first feature
                if (features.isNotEmpty()) {
                    val firstFeature = features.first()
                    Logger.log("AIRSPACE_CLICK", LogLevel.DEBUG, "First feature available properties:")
                    try {
                        // Try to get all properties by attempting common property names
                        val commonProps = listOf("AI", "id", "name", "type", "upperLimit", "lowerLimit", "AG", "AF", "frequency", "freq", "AN", "AH", "AL", "upperLimitMeters", "lowerLimitMeters", "AC", "AY")
                        commonProps.forEach { propName ->
                            try {
                                val value = safeGetProperty(firstFeature, propName)
                                if (value != null) {
                                    Logger.log("AIRSPACE_CLICK", LogLevel.DEBUG, "  Property '$propName': '$value'")
                                }
                            } catch (e: Exception) {
                                Logger.log("AIRSPACE_CLICK", LogLevel.ERROR, "Error getting property '$propName': ${e.message}")
                                throw e // Re-throw to be caught by outer catch
                            }
                        }
                    } catch (e: Exception) {
                        Logger.log("AIRSPACE_CLICK", LogLevel.ERROR, "Error inspecting feature properties: ${e.message}")
                        Logger.log("AIRSPACE_CLICK", LogLevel.ERROR, "Exception type: ${e::class.simpleName}")
                        Logger.log("AIRSPACE_CLICK", LogLevel.ERROR, "Stack trace: ${e.stackTraceToString()}")
                    }
                }

                // Sort features by lowerLimitMeters descending (highest first) - replicate PWA order
                val sortedFeatures = features.sortedWith(compareByDescending { feature ->
                    safeGetProperty(feature, "lowerLimitMeters")?.toDoubleOrNull() ?: 0.0
                })

                Logger.log("AIRSPACE_CLICK", LogLevel.DEBUG, "Features sorted by lowerLimitMeters (highest first)")

                // Convert sorted features to AirspaceFeatureData for the popup
                val featureDataList = sortedFeatures.mapIndexed { index, feature ->
                    // Use the correct property names from the GeoJSON data (matching what debug shows)
                    // Try multiple ways to get the feature ID
                    val airspaceId = safeGetProperty(feature, "AI") ?:
                                    safeGetProperty(feature, "id") ?:
                                    "feature_${index}"  // Fallback to index-based ID

                    val airspaceName = safeGetProperty(feature, "name") ?: "Unknown Airspace"
                    val airspaceType = safeGetProperty(feature, "type") ?: "Unknown"
                    val upperLimit = safeGetProperty(feature, "upperLimit") ?: ""
                    val lowerLimit = safeGetProperty(feature, "lowerLimit") ?: ""
                    val lowerLimitMeters = safeGetProperty(feature, "lowerLimitMeters")?.toDoubleOrNull()
                    val upperLimitMeters = safeGetProperty(feature, "upperLimitMeters")?.toDoubleOrNull()

                    // Frequency extraction - match PWA logic (AG + AF with space)
                    val agFreq = safeGetProperty(feature, "AG")?.takeIf { it != "null" && it.isNotBlank() }
                    val afFreq = safeGetProperty(feature, "AF")?.takeIf { it != "null" && it.isNotBlank() }
                    val freqProp = safeGetProperty(feature, "frequency")?.takeIf { it != "null" && it.isNotBlank() }
                    val freqShort = safeGetProperty(feature, "freq")?.takeIf { it != "null" && it.isNotBlank() }

                    // Build frequency string like PWA: "AG_VALUE AF_VALUE" or fallback to other frequency fields
                    val frequency = if (agFreq != null && afFreq != null) {
                        "$agFreq $afFreq"
                    } else if (agFreq != null) {
                        agFreq
                    } else if (afFreq != null) {
                        afFreq
                    } else {
                        // Fallback to other frequency fields if AG/AF not present
                        listOfNotNull(freqProp, freqShort).joinToString(" ").ifBlank { "" }
                    }

                    Logger.log("AIRSPACE_CLICK", LogLevel.DEBUG,
                        "  Feature properties - id: '$airspaceId', name: '$airspaceName', type: '$airspaceType', upperLimit: '$upperLimit', lowerLimit: '$lowerLimit', upperLimitMeters: '$upperLimitMeters', lowerLimitMeters: '$lowerLimitMeters'")
                    Logger.log("AIRSPACE_CLICK", LogLevel.DEBUG,
                        "  Frequency properties - AG: '$agFreq', AF: '$afFreq', frequency: '$freqProp', freq: '$freqShort' -> Final frequency: '$frequency'")
                    Logger.log("AIRSPACE_CLICK", LogLevel.INFO,
                        "  Sorted: ID: '$airspaceId', Name: '$airspaceName', Type: '$airspaceType', Upper: '$upperLimit', Lower: '$lowerLimit', UpperMeters: '$upperLimitMeters', LowerMeters: '$lowerLimitMeters', Freq: '$frequency'")

                    // Extract all properties from the feature
                    val allProperties = mutableMapOf<String, String>()
                    // Try to get common properties we know about
                    val commonProps = listOf("AI", "id", "name", "type", "upperLimit", "lowerLimit", "AG", "AF", "frequency", "freq", "AN", "AH", "AL", "upperLimitMeters", "lowerLimitMeters", "AC", "AY")
                    commonProps.forEach { propName ->
                        safeGetProperty(feature, propName)?.let { value ->
                            allProperties[propName] = value
                        }
                    }



                    AirspaceFeatureData(
                        id = airspaceId,
                        name = airspaceName,
                        type = airspaceType,
                        upperLimit = upperLimit,
                        lowerLimit = lowerLimit,
                        frequency = frequency,
                        allProperties = allProperties
                    )
                }

                // 🎯 STEP 1: Show marker FIRST for immediate visual feedback
                val globalState = getGlobalState()
                val clickedPosition = globalState.clickedPosition.value
                if (clickedPosition != null) {
                    globalState.showMarkerAt(clickedPosition, MarkerStyle.AIRSPACE)
                    Logger.log("AIRSPACE_CLICK", LogLevel.INFO, "🎯 Marker shown immediately at clicked position")
                } else {
                    Logger.log("AIRSPACE_CLICK", LogLevel.WARN, "No clicked position available for marker")
                }

                // 🎯 STEP 2: Show airspace popup (this will trigger overlay rendering)
                Logger.log("AIRSPACE_CLICK", LogLevel.INFO, "🎯 Creating airspace popup with ${featureDataList.size} features")
                try {
                    module.showAirspacePopup(featureDataList)
                } catch (e: Exception) {
                    Logger.log("AIRSPACE_CLICK", LogLevel.ERROR, "Error showing airspace popup: ${e.message}")
                    Logger.log("AIRSPACE_CLICK", LogLevel.ERROR, "Exception type: ${e::class.simpleName}")
                    Logger.log("AIRSPACE_CLICK", LogLevel.ERROR, "Stack trace: ${e.stackTraceToString()}")
                }


                Logger.log("AIRSPACE_CLICK", LogLevel.INFO, "✅ Click handling completed - marker, popup, and layer ordering all set")

                Logger.log("AIRSPACE_CLICK", LogLevel.DEBUG, "Click handling completed - popup and marker shown")

                ClickResult.Consume
            } else {
                Logger.log("AIRSPACE_CLICK", LogLevel.DEBUG, "FillLayer onClick: No features in click area")
                ClickResult.Pass
            }
        }
    )

    Logger.log("AIRSPACE_CLICK_LAYER", LogLevel.DEBUG, "AirspaceClickLayer: transparent fill layer created for click detection")
}

/**
 * Airspace highlight layer composable - turquoise overlay for selected features
 */
@Composable
private fun AirspaceHighlightLayer(highlightData: String) {
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Airspace highlight layer recomposing")

    // Create highlight source and layer
    val highlightSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(highlightData)
    )

    FillLayer(
        id = "airspace-highlight",
        source = highlightSource,
        color = const(AirspaceColors.Highlight.FILL_COLOR),
        opacity = const(1.0f)
    )
}

/**
 * Safely get a property value from a feature, handling different JSON element types.
 * This avoids the JsonDecodingException that occurs with getStringProperty on numeric values.
 */
private fun safeGetProperty(feature: Feature<*, JsonObject?>, propertyName: String): String? {
    return try {
        // Cast to JsonObject since we know that's the type for MapLibre features
        val properties = feature.properties as? JsonObject
        val jsonElement = properties?.get(propertyName)

        when {
            jsonElement is JsonPrimitive && jsonElement.isString -> jsonElement.content
            jsonElement is JsonPrimitive && jsonElement.doubleOrNull != null -> jsonElement.doubleOrNull.toString()
            jsonElement is JsonPrimitive && jsonElement.longOrNull != null -> jsonElement.longOrNull.toString()
            jsonElement is JsonPrimitive && jsonElement.booleanOrNull != null -> jsonElement.booleanOrNull.toString()
            else -> jsonElement?.toString()
        }
    } catch (e: Exception) {
        // Fallback to getStringProperty for complex cases, but this might still fail
        try {
            feature.getStringProperty(propertyName)
        } catch (e2: Exception) {
            null
        }
    }
}

/**
 * Airspace Marker Layer - Shows a red circle marker at the clicked airspace position when popup is open
 */
