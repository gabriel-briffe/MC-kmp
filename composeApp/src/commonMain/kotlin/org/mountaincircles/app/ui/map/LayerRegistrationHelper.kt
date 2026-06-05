package org.mountaincircles.app.ui.map

import androidx.compose.runtime.Composable
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Helper class to simplify layer registration for modules
 */
object LayerRegistrationHelper {

    val layerManager = LayerManager.instance

    /**
     * Register a single layer with automatic ID generation
     * NOW USING Z-INDEX INSTEAD OF PRIORITY FOR STRICT ORDERING
     */

    /**
     * Data class for stable layer registrations that can pass pre-computed values to composables
     */
    data class StableLayerRegistration(
        val layerDescriptor: LayerDescriptor,
        val composable: @Composable () -> Unit,
        val stableComposable: (@Composable (Any?) -> Unit)? = null,
        val stableValues: Any? = null
    )

    /**
     * Register a layer with stable values that can be passed directly to composables
     * This eliminates StateFlow collection at the composable level for better performance
     */
    fun registerLayerWithStableValues(
        moduleId: String,
        layerName: String,
        zIndex: Int,
        layerType: LayerDescriptor.LayerType = LayerDescriptor.LayerType.FEATURE,
        isInteractive: Boolean = false,
        description: String? = null,
        tags: Set<String> = emptySet(),
        stableValues: Any? = null,
        composable: @Composable (Any?) -> Unit
    ): String {
        val layerId = "${moduleId}_${layerName}"

        val descriptor = LayerDescriptor(
            id = layerId,
            moduleId = moduleId,
            displayName = layerName,
            renderPriority = zIndex,
            layerType = layerType,
            isInteractive = isInteractive,
            isVisible = true,
            description = description,
            tags = tags
        )

        val stableRegistration = StableLayerRegistration(
            layerDescriptor = descriptor,
            composable = { /* fallback */ },
            stableComposable = composable,
            stableValues = stableValues
        )

        layerManager.registerStableLayer(stableRegistration)

        Logger.log("LAYER_REGISTRATION", LogLevel.INFO,
            "✅ Stable layer registered: $layerId (z-index: $zIndex)")

        return layerId
    }
    fun registerLayer(
        moduleId: String,
        layerName: String,
        zIndex: Int,  // NEW: Explicit z-index instead of renderPriority
        layerType: LayerDescriptor.LayerType = LayerDescriptor.LayerType.FEATURE,
        isInteractive: Boolean = false,
        description: String? = null,
        tags: Set<String> = emptySet(),
        composable: @Composable () -> Unit
    ): String {
        val layerId = "${moduleId}_${layerName}"

        // Add targeted logging for skysight tile registrations during visibility changes
        if (moduleId == "skysight" && (layerName.startsWith("satellite_") || layerName.startsWith("rain_"))) {
            Logger.log("LAYER_REGISTRATION_DEBUG", LogLevel.INFO, "=== REGISTERING SKYSIGHT TILE: $layerId ===")
            Logger.log("LAYER_REGISTRATION_DEBUG", LogLevel.DEBUG, "Tile registration details: layerName=$layerName, zIndex=$zIndex, tags=$tags")
        }

        val descriptor = LayerDescriptor(
            id = layerId,
            moduleId = moduleId,
            displayName = layerName,
            renderPriority = zIndex,  // Use zIndex as renderPriority for backward compatibility
            layerType = layerType,
            isInteractive = isInteractive,
            isVisible = true,  // Explicitly set to true
            description = description,
            tags = tags
        )

        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "LayerRegistrationHelper registering layer: $layerId, module=$moduleId, zIndex=$zIndex, type=${layerType}, visible=${descriptor.isVisible}")
        layerManager.registerLayer(descriptor, composable)

        Logger.log("LAYER_REGISTRATION", LogLevel.INFO,
            "✅ Layer registered: $layerId (z-index: $zIndex)")

        return layerId
    }

    /**
     * Unregister a layer by its full layer ID
     */
    fun unregisterLayer(layerId: String) {
        Logger.log("LAYER_REGISTRATION", LogLevel.DEBUG, "Unregistering layer: $layerId")
        layerManager.unregisterLayer(layerId)
        Logger.log("LAYER_REGISTRATION", LogLevel.INFO, "❌ Layer unregistered: $layerId")
    }

    /**
     * Register multiple layers for a module
     * NOW USING Z-INDEX INSTEAD OF PRIORITY
     */
    fun registerModuleLayers(
        moduleId: String,
        layers: List<LayerConfig>
    ): List<String> {
        return layers.map { config ->
            registerLayer(
                moduleId = moduleId,
                layerName = config.name,
                zIndex = config.renderPriority,  // Use existing renderPriority as zIndex
                layerType = config.layerType,
                isInteractive = config.isInteractive,
                description = config.description,
                tags = config.tags,
                composable = config.composable
            )
        }
    }

    /**
     * Register a click handler for a layer
     */
    fun registerClickHandler(
        layerId: String,
        handler: (MapClickEvent) -> Boolean
    ) {
        layerManager.registerClickHandler(layerId, handler)
    }


    /**
     * Update layer visibility for a module
     */
    fun setModuleLayerVisibility(moduleId: String, visible: Boolean) {
        val layers = layerManager.layers.value.filter { it.moduleId == moduleId }
        layers.forEach { layer ->
            layerManager.setLayerVisibility(layer.id, visible)
        }
    }

    /**
     * Configuration for a single layer
     */
    data class LayerConfig(
        val name: String,
        val renderPriority: Int,
        val layerType: LayerDescriptor.LayerType = LayerDescriptor.LayerType.FEATURE,
        val isInteractive: Boolean = false,
        val description: String? = null,
        val tags: Set<String> = emptySet(),
        val composable: @Composable () -> Unit
    )
}

/**
 * Layer order registry - z-index is determined by position in this list
 * BOTTOM layers (index 0) render first, TOP layers (higher index) render last
 * To reorder: simply move lines up/down in this list
 */
object LayerZIndex {

    // Layer ordering - z-index = index position in this list
    val layerOrder = listOf(
        // BOTTOM LAYERS (rendered first)
        "maps_terrain",

        // CIRCLES LAYERS - Aviation circles data
        "circles_polygons",
        "circles_lines",
        "circles_line_labels",
        "circles_points",
        "circles_airfield_labels",

        // SKYSIGHT LAYERS
        "skysight_bitmap",
        "skysight_labels",
        "skysight_satellite",
        "skysight_rain",

        // AIRPORT LAYERS - Airport data
        "airports_circles",
        "airports_labels",
        
        // AIRSPACE LAYERS - Airspace boundaries
        "airspace_boundaries",
        "airspace_highlight",      // Turquoise highlight overlay

        
        // WAVE LAYERS - Weather forecast data
        "wave_raster",
        
        // Wind
        "wave_mbtiles_points",


        // MARKERS - User location
        "geolocation_marker",
        "airspace_click_marker",

        // CLICKABLE LAYERS (transparent, for interaction only)
        "airspace_click_areas",  // Airspace click detection (lower priority)
        "airports_click_areas",
        "circles_click_areas",   // Circles click detection (higher priority)
        
        // LIVETRACKING LAYERS - Real-time aircraft data
        "livetracking_aircraft",
        "livetracking_aircraft_labels",

    )

    init {
        // Debug: Log the complete layer order on initialization
        Logger.log("LAYER_ORDER", LogLevel.DEBUG, "=== LAYER ORDER REGISTRY ===")
        layerOrder.forEachIndexed { index, layerName ->
            Logger.log("LAYER_ORDER", LogLevel.DEBUG, "[$index] $layerName")
        }
        Logger.log("LAYER_ORDER", LogLevel.DEBUG, "=== END LAYER ORDER ===\n")
    }

    // Get z-index for a layer by its position in the list
    fun getZIndex(layerName: String): Int {
        val index = layerOrder.indexOf(layerName)
        val zIndex = if (index >= 0) index else 999 // Default high value for unknown layers

        // Debug: Log z-index lookup
        Logger.log("LAYER_ZINDEX", LogLevel.DEBUG, "getZIndex('$layerName') = $zIndex (position $index)")

        // Warn if layer not found in registry
        if (index < 0) {
            Logger.log("LAYER_ZINDEX", LogLevel.WARN, "⚠️ LAYER NOT FOUND IN REGISTRY: '$layerName'")
            Logger.log("LAYER_ZINDEX", LogLevel.WARN, "Available layers: $layerOrder")
        }

        return zIndex
    }

    // Get all layer names for validation
    fun getAllLayerNames(): Set<String> = layerOrder.toSet()
}

