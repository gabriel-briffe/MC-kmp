package org.mountaincircles.app.modules.circles.layer.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.mountaincircles.app.utils.ScopeManager
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.layers.*
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.mountaincircles.app.utils.encodeBase64
import org.maplibre.compose.expressions.dsl.*
import org.maplibre.compose.expressions.value.SymbolPlacement
import org.maplibre.compose.expressions.value.TextRotationAlignment
import org.maplibre.compose.expressions.value.SymbolOverlap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.geometry.Offset
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.modules.circles.logic.data.PackConfig
import org.mountaincircles.app.modules.circles.logic.data.PolygonData
import org.mountaincircles.app.modules.circles.logic.data.LinesData
import org.mountaincircles.app.modules.circles.logic.data.PointsData
import org.mountaincircles.app.modules.circles.logic.data.LabelsData
import org.mountaincircles.app.modules.circles.logic.data.ClickData
import org.mountaincircles.app.modules.circles.logic.data.AirfieldLabelsData
import org.mountaincircles.app.modules.circles.layer.ui.composables.*
import org.mountaincircles.app.ui.map.*

/**
 * Circles module LayerManager implementation
 *
 * This manages the circles GeoJSON layers using the new LayerManager system,
 * providing better control over layer priority, visibility, and lifecycle.
 */


class CirclesLayerManager(private val module: CirclesModule) {

    private val layerIds = mutableListOf<String>()
    val layerIdsPublic: List<String> get() = layerIds
    private var currentSelection: PackConfig? = null
    private var currentAirfieldData: String? = null  // Track which airfield data is currently loaded

    // State for dynamic data loading - triggers recomposition when changed
    private var linesLayerData by mutableStateOf<String?>(null)
    private var labelsLayerData by mutableStateOf<String?>(null)

    // State for click layer feedback - triggers recomposition when changed
    private var clickLayerOpacity = mutableStateOf(0.0f)

    // Job to track the current fade-out coroutine (prevents flickering during rapid changes)
    private var fadeOutJob: Job? = null

    // Public accessors for the dynamic data (now URI paths instead of content)
    val currentLinesUri: String? get() = linesLayerData
    val currentLabelsUri: String? get() = labelsLayerData

    // Public accessor for click layer opacity
    val clickLayerOpacityValue: Float get() = clickLayerOpacity.value


    /**
     * Temporarily show click areas at 50% opacity for 2 seconds as visual feedback
     * when the click size setting is changed. Prevents flickering during rapid changes
     * by cancelling previous fade-out jobs.
     */
    fun showClickAreaFeedback() {
        Logger.log("CIRCLES_CLICK", LogLevel.DEBUG, "Showing click area feedback (50% opacity)")

        // Cancel any existing fade-out job to prevent flickering
        fadeOutJob?.cancel()
        fadeOutJob = null

        // Show at 50% opacity immediately
        clickLayerOpacity.value = 0.5f

        // Start new fade-out job that will reset to invisible after 2 seconds
        // This will be cancelled if showClickAreaFeedback() is called again
        fadeOutJob = ScopeManager.uiScope.launch {
            kotlinx.coroutines.delay(1000)  // Wait 1 second
            clickLayerOpacity.value = 0.0f  // Back to invisible
            Logger.log("CIRCLES_CLICK", LogLevel.DEBUG, "Click area feedback ended - back to invisible")
            fadeOutJob = null
        }
    }

    /**
     * Reset to main linestring layer (clear any dynamic selections)
     */
    fun resetToMainLayer() {
        Logger.log("CIRCLES_LAYERS", LogLevel.INFO, "Resetting to main linestring layer")

        // Clear any dynamic airfield selection to show main layer
        currentAirfieldData = null

        // Reset layer data states to trigger recomposition with main data
        linesLayerData = null
        labelsLayerData = null

        Logger.log("CIRCLES_LAYERS", LogLevel.DEBUG, "Reset complete - will show main linestring layer")
    }

    /**
     * Initialize and register all circles layers with the LayerManager
     */
    fun initializeLayers() {
        Logger.log("CIRCLES_LAYERS", LogLevel.INFO,
            "Initializing circles layers with LayerManager")
        Logger.log("CIRCLES_LAYERS", LogLevel.DEBUG,
            "Current selection: ${currentSelection?.packId}/${currentSelection?.configId}")
        Logger.log("CIRCLES_LAYERS", LogLevel.DEBUG,
            "Layer manager instance: ${this::class.simpleName}@${this.hashCode()}")

        // Initialize with main data at startup if we have a selection
        if (currentSelection != null) {
            Logger.log("CIRCLES_LAYERS", LogLevel.DEBUG, "Initializing with main data at startup")
            loadMainCirclesData()
        }

        // Register all circles layers with the LayerManager using native Flow solution
        val layers = listOf(
            LayerRegistrationHelper.registerLayer(
                moduleId = "circles",
                layerName = "circles_polygons",
                zIndex = 10 * LayerZIndex.getZIndex("circles_polygons"),
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = false,
                description = "Circles polygon layers with color coding",
                tags = setOf("circles", "polygons", "sectors", "geojson"),
                composable = { CirclesPolygonLayer(module) }
            ),
            LayerRegistrationHelper.registerLayer(
                moduleId = "circles",
                layerName = "circles_lines",
                zIndex = 10 * LayerZIndex.getZIndex("circles_lines"),
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = false,
                description = "Circles line layers for elevation contours",
                tags = setOf("circles", "lines", "contours", "geojson"),
                composable = { CirclesLineLayer(module, this) }
            ),
            LayerRegistrationHelper.registerLayer(
                moduleId = "circles",
                layerName = "circles_line_labels",
                zIndex = 10 * LayerZIndex.getZIndex("circles_line_labels"),
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = false,
                description = "Circles line labels showing elevation values with line placement",
                tags = setOf("circles", "labels", "elevation", "geojson"),
                composable = { CirclesLineLabelsLayer(module, this) }
            ),
            LayerRegistrationHelper.registerLayer(
                moduleId = "circles",
                layerName = "circles_points",
                zIndex = 10 * LayerZIndex.getZIndex("circles_points"),
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = false,
                description = "Circles point layers for airfields",
                tags = setOf("circles", "points", "airfields", "geojson"),
                composable = { CirclesPointLayer(module) }
            ),
            LayerRegistrationHelper.registerLayer(
                moduleId = "circles",
                layerName = "circles_click_areas",
                zIndex = 10 * LayerZIndex.getZIndex("circles_click_areas"),
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = true, // Now clickable!
                description = "Circles click areas for airfields (50% opaque for visibility)",
                tags = setOf("circles", "click", "airfields", "geojson"),
                composable = { CirclesClickAreaLayer(module, this) }
            ).also { clickLayerId ->
                Logger.log("CIRCLES_LAYERS", LogLevel.INFO,
                    "About to register click handler for circles click layer: $clickLayerId")
                // Register click handler for the click layer
                LayerRegistrationHelper.registerClickHandler(clickLayerId) { event ->
                    Logger.log("CIRCLES_CLICK", LogLevel.DEBUG,
                        "Click handler called for layer: $clickLayerId")
                    handleMapClickEvent(event)
                }
                Logger.log("CIRCLES_LAYERS", LogLevel.INFO,
                    "Successfully registered click handler for circles click layer: $clickLayerId")
            },
            LayerRegistrationHelper.registerLayer(
                moduleId = "circles",
                layerName = "circles_airfield_labels",
                zIndex = 10 * LayerZIndex.getZIndex("circles_airfield_labels"),
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = false,
                description = "Circles airfield labels",
                tags = setOf("circles", "airfield", "labels", "geojson"),
                composable = { CirclesAirfieldLabelsLayer(module) }
            )
        )

        layerIds.addAll(layers)

        Logger.log("CIRCLES_LAYERS", LogLevel.INFO,
            "Registered ${layers.size} circles layers with LayerManager")
    }

    /**
     * Cleanup layers when module is destroyed
     */
    fun cleanup() {
        Logger.log("CIRCLES_LAYERS", LogLevel.INFO, "Cleaning up circles layers")

        // Cancel any ongoing fade-out job
        fadeOutJob?.cancel()
        fadeOutJob = null

        // Reset opacity to invisible
        clickLayerOpacity.value = 0.0f

        // Unregister all layers
        layerIds.forEach { layerId ->
            LayerRegistrationHelper.layerManager.unregisterLayer(layerId)
        }

        layerIds.clear()
    }

    // Step 2: Click Handling Implementation

    /**
     * Handle MapClickEvent from the LayerManager click system
     */
    private fun handleMapClickEvent(event: MapClickEvent): Boolean {
        try {
            Logger.log("CIRCLES_CLICK", LogLevel.DEBUG, "handleMapClickEvent: Processing click at (${event.longitude}, ${event.latitude}) screen: (${event.screenX}, ${event.screenY})")

            // This method is now mainly for compatibility - the real click handling happens
            // in the CirclesClickAreaLayer composable using MapLibre's onClick parameter
            Logger.log("CIRCLES_CLICK", LogLevel.DEBUG, "handleMapClickEvent: Click handling delegated to layer onClick handler")

            // Return false since the actual handling happens in the layer
            return false

        } catch (t: Throwable) {
            Logger.log("CIRCLES_CLICK", LogLevel.ERROR, "handleMapClickEvent: Failed to handle click - ${t.message}")
            return false
        }
    }

    /**
     * Handle click events on the circles click layer (legacy method for compatibility)
     */
    fun handleClick(latitude: Double, longitude: Double, screenX: Float, screenY: Float): Boolean {
        // This method is kept for compatibility but delegates to handleMapClickEvent
        val mapClickEvent = MapClickEvent(
            latitude = latitude,
            longitude = longitude,
            zoom = 0.0, // Not used in our implementation
            screenX = screenX,
            screenY = screenY
        )
        return handleMapClickEvent(mapClickEvent)
    }

    /**
     * Extract airfield name from a feature
     */
    private fun extractAirfieldName(feature: Any): String {
        return try {
            // For now, this is a placeholder - actual implementation would need
            // proper MapLibre feature access
            Logger.log("CIRCLES_CLICK", LogLevel.DEBUG, "extractAirfieldName: Feature extraction placeholder")
            "" // Return empty string as placeholder
        } catch (t: Throwable) {
            Logger.log("CIRCLES_CLICK", LogLevel.ERROR, "extractAirfieldName: Failed to extract name - ${t.message}")
            ""
        }
    }

    // Step 3: Dynamic Layer Management Implementation

    /**
     * Toggle an airfield layer on/off
     */
    fun toggleAirfieldLayer(airfieldName: String) {
        try {
            Logger.log("CIRCLES_TOGGLE", LogLevel.INFO, "toggleAirfieldLayer: '$airfieldName'")

            val selection = currentSelection
            if (selection == null) {
                Logger.log("CIRCLES_TOGGLE", LogLevel.ERROR, "toggleAirfieldLayer: No current selection")
                return
            }

            // Check if we're currently showing a specific airfield
            if (currentAirfieldData != null) {
                if (currentAirfieldData == airfieldName) {
                    // Same airfield clicked - go back to main data
                    Logger.log("CIRCLES_TOGGLE", LogLevel.INFO, "toggleAirfieldLayer: Same airfield clicked, returning to main data")
                    loadMainCirclesData()
                } else {
                    // Different airfield - switch to new one
                    Logger.log("CIRCLES_TOGGLE", LogLevel.INFO, "toggleAirfieldLayer: Different airfield, switching from '${currentAirfieldData}' to '$airfieldName'")
                    loadAirfieldData(airfieldName)
                }
            } else {
                // No specific airfield showing - load the clicked one
                Logger.log("CIRCLES_TOGGLE", LogLevel.INFO, "toggleAirfieldLayer: Loading specific airfield data: '$airfieldName'")
                loadAirfieldData(airfieldName)
            }

        } catch (t: Throwable) {
            Logger.log("CIRCLES_TOGGLE", LogLevel.ERROR, "toggleAirfieldLayer: Failed for '$airfieldName' - ${t.message}")
        }
    }

    /**
     * Load the main circles data (all airfields)
     */
    private fun loadMainCirclesData() {
        try {
            Logger.log("CIRCLES_TOGGLE", LogLevel.INFO, "loadMainCirclesData: Setting main circles URI")

            val selection = currentSelection ?: return

            // Use the same logic as the CirclesLineLayer composable
            val metadata = selection.metadata
            val prefix = metadata?.prefix ?: selection.configId.split("-").dropLast(1).joinToString("-")
            val policy = metadata?.policy ?: selection.packId
            val mainFileName = "aa_${policy}_${prefix}.geojson"
            val fileManager = getGlobalFileManager()
            val geoJsonPath = "${fileManager.getAppDataDirectory()}/circles/${selection.packId}/${selection.configId}/$mainFileName"

            // ✅ URI APPROACH: Just set the URI path instead of reading content
            Logger.log("CIRCLES_TOGGLE", LogLevel.DEBUG, "loadMainCirclesData: Setting URI to $geoJsonPath")
            updateLinesLayerUri("file://$geoJsonPath")
                        currentAirfieldData = null
            Logger.log("CIRCLES_TOGGLE", LogLevel.INFO, "loadMainCirclesData: Successfully set main data URI")

        } catch (t: Throwable) {
            Logger.log("CIRCLES_TOGGLE", LogLevel.ERROR, "loadMainCirclesData: Failed - ${t.message}")
        }
    }

    /**
     * Load specific airfield data
     */
    private fun loadAirfieldData(airfieldName: String) {
        try {
            Logger.log("CIRCLES_TOGGLE", LogLevel.INFO, "loadAirfieldData: Setting URI for '$airfieldName'")

            val selection = currentSelection ?: return
            val prefix = selection.configId.split('-').take(3).joinToString("-")
            val airfieldFileName = "${airfieldName}_${prefix}.geojson"
            val fileManager = getGlobalFileManager()
            val geoJsonPath = "${fileManager.getAppDataDirectory()}/circles/${selection.packId}/${selection.configId}/$airfieldFileName"

            // ✅ URI APPROACH: Just set the URI path instead of reading content
            Logger.log("CIRCLES_TOGGLE", LogLevel.DEBUG, "loadAirfieldData: Setting URI to $geoJsonPath")
            updateLinesLayerUri("file://$geoJsonPath")
                        currentAirfieldData = airfieldName
            Logger.log("CIRCLES_TOGGLE", LogLevel.INFO, "loadAirfieldData: Successfully set URI for '$airfieldName'")

        } catch (t: Throwable) {
            Logger.log("CIRCLES_TOGGLE", LogLevel.ERROR, "loadAirfieldData: Failed for '$airfieldName' - ${t.message}")
            // Fallback to main data URI
            loadMainCirclesData()
        }
    }

    /**
     * Update the lines and labels layer URI paths (they work as a group)
     */
    private fun updateLinesLayerUri(uri: String) {
        try {
            Logger.log("CIRCLES_TOGGLE", LogLevel.DEBUG, "updateLinesLayerUri: Setting URI to $uri")

            // Update both state variables to trigger recomposition with new URI
            linesLayerData = uri
            labelsLayerData = uri

            Logger.log("CIRCLES_TOGGLE", LogLevel.DEBUG, "updateLinesLayerUri: Both lines and labels URIs updated, layers will recompose")

        } catch (t: Throwable) {
            Logger.log("CIRCLES_TOGGLE", LogLevel.ERROR, "updateLinesLayerUri: Failed - ${t.message}")
        }
    }

    /**
     * Update the lines and labels layer source data (LEGACY - kept for backward compatibility)
     */
    private fun updateLinesLayerSource(geoJsonContent: String) {
        // Convert content to URI for the new system
        val tempUri = "data:application/json;base64,${encodeBase64(geoJsonContent.encodeToByteArray())}"
        updateLinesLayerUri(tempUri)
    }

    /**
     * Check if a layer exists in the LayerManager
     */
    private fun hasLayer(layerId: String): Boolean {
        val layers = LayerRegistrationHelper.layerManager.layers.value
        return layers.any { it.id == layerId }
    }

    /**
     * Check if a layer is currently visible
     */
    private fun isLayerVisible(layerId: String): Boolean {
        val layers = LayerRegistrationHelper.layerManager.layers.value
        return layers.find { it.id == layerId }?.isVisible == true
    }

    /**
     * Set layer visibility in the LayerManager
     */
    private fun setLayerVisibility(layerId: String, visible: Boolean) {
        try {
            LayerRegistrationHelper.layerManager.setLayerVisibility(layerId, visible)
            Logger.log("CIRCLES_TOGGLE", LogLevel.DEBUG, "setLayerVisibility: '$layerId' -> $visible")
        } catch (t: Throwable) {
            Logger.log("CIRCLES_TOGGLE", LogLevel.ERROR, "setLayerVisibility: Failed for '$layerId' - ${t.message}")
        }
    }

    /**
     * Create a dynamic airfield layer
     */
    private fun createDynamicAirfieldLayer(airfieldName: String, selection: PackConfig) {
        try {
            val prefix = selection.configId.split('-').take(3).joinToString("-")
            val layerId = "airfield-${airfieldName}"
            val sourceId = "${layerId}-source"

            Logger.log("CIRCLES_DYNAMIC", LogLevel.INFO, "createDynamicAirfieldLayer: Creating layer for '$airfieldName'")

            // Create vector source for the airfield MBTiles
            val tileUrl = createTileUrl(airfieldName, selection)
            Logger.log("CIRCLES_DYNAMIC", LogLevel.DEBUG, "createDynamicAirfieldLayer: Tile URL = $tileUrl")

            // Register the dynamic layer
            val layer = LayerRegistrationHelper.registerLayer(
                moduleId = "circles",
                layerName = layerId,
                zIndex = 10 * LayerZIndex.getZIndex("dynamic_layers"), // Use dynamic layers z-index
                layerType = LayerDescriptor.LayerType.FEATURE,
                isInteractive = false,
                description = "Dynamic airfield layer for $airfieldName",
                tags = setOf("circles", "airfield", "dynamic", airfieldName),
                composable = {
                    // This will be implemented in Step 4
                    Logger.log("CIRCLES_DYNAMIC", LogLevel.DEBUG, "createDynamicAirfieldLayer: Composable called for '$layerId'")
                }
            )

            layerIds.add(layer)

            // Hide main lines layer when showing specific airfield
            val mainLayerId = "lines-${selection.packId}-${selection.configId}"
            setLayerVisibility(mainLayerId, false)

            Logger.log("CIRCLES_DYNAMIC", LogLevel.INFO, "createDynamicAirfieldLayer: Created and registered layer '$layerId'")

        } catch (t: Throwable) {
            Logger.log("CIRCLES_DYNAMIC", LogLevel.ERROR, "createDynamicAirfieldLayer: Failed for '$airfieldName' - ${t.message}")
        }
    }

    /**
     * Create tile URL for airfield MBTiles
     */
    private fun createTileUrl(airfieldName: String, selection: PackConfig): String {
        val prefix = selection.configId.split('-').take(3).joinToString("-")
        val filename = "${airfieldName}_${prefix}.mbtiles"

        return "http://127.0.0.1:8080/tiles/circles/${selection.packId}/${selection.configId}/${filename}/{z}/{x}/{y}.pbf"
    }

    /**
     * Update the current selection (called by the module when selection changes)
     */
    fun updateSelection(selection: PackConfig?) {
        Logger.log("CIRCLES_LAYERS", LogLevel.DEBUG, "updateSelection: ${selection?.packId}/${selection?.configId}")
        currentSelection = selection
    }

}



