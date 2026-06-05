package org.mountaincircles.app.modules.maps.layer.ui

import androidx.compose.runtime.*
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.sources.rememberRasterSource
import org.maplibre.compose.expressions.dsl.const
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.ui.map.*
import org.mountaincircles.app.modules.maps.MapsModule
import org.mountaincircles.app.modules.maps.logic.services.MBTilesPathResolver

/**
 * Maps module LayerManager implementation
 *
 * This manages the map tile layers using the new LayerManager system,
 * providing better control over layer priority, visibility, and lifecycle.
 */
class MapsLayerManager(private val module: MapsModule) {

    private val layerIds = mutableListOf<String>()
    val layerIdsPublic: List<String> get() = layerIds

    /**
     * Initialize and register all maps layers with the LayerManager
     */
    fun initializeLayers() {
        Logger.log("MAPS_LAYERS", LogLevel.INFO,
            "Initializing maps layers with LayerManager")

        // Register the multiple MBTiles raster layers
        val tileLayerId = LayerRegistrationHelper.registerLayer(
            moduleId = "maps",
            layerName = "multi_mbtiles_raster",
            zIndex = 10 * LayerZIndex.getZIndex("maps_terrain"),
            layerType = LayerDescriptor.LayerType.BASE,
            isInteractive = false,
            description = "Multiple non-overlapping MBTiles raster layers",
            tags = setOf("maps", "terrain", "raster", "mbtiles", "multi"),
            composable = { TileProxyRasterLayer(module) }
        )

        layerIds.add(tileLayerId)

        Logger.log("MAPS_LAYERS", LogLevel.INFO,
            "Registered ${layerIds.size} maps layers with LayerManager")
    }

    /**
     * Update layer visibility based on module state
     */
    fun updateLayerVisibility() {
        val layerData = module.layerDisplayFlow.value
        val shouldShow = layerData.hasDataToRender

        layerIds.forEach { layerId ->
            LayerRegistrationHelper.layerManager.setLayerVisibility(layerId, shouldShow)
        }

        Logger.log("MAPS_LAYERS", LogLevel.DEBUG,
            "Updated layer visibility: $shouldShow for ${layerIds.size} layers")
    }

    /**
     * Clean up layers when module is destroyed
     */
    fun cleanup() {
        Logger.log("MAPS_LAYERS", LogLevel.INFO,
            "Cleaning up ${layerIds.size} maps layers")

        layerIds.forEach { layerId ->
            LayerRegistrationHelper.layerManager.unregisterLayer(layerId)
        }

        layerIds.clear()
    }
}

/**
 * Individual layer composables that can be registered separately
 */

/**
 * Multiple MBTiles raster layers - displays all installed maps simultaneously
 * Since maps don't overlap, each provides tiles for its geographic region
 */
@Composable
private fun TileProxyRasterLayer(module: MapsModule) {
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Maps tile proxy layer recomposing")

    val layerData by module.layerDisplayFlow.collectAsState()

    // Only render if available - this prevents unnecessary recompositions
    if (!layerData.hasDataToRender) return

    val installedMaps = layerData.installedMaps

    if (installedMaps.isEmpty()) {
        Logger.log("MAPS_LAYERS", LogLevel.DEBUG, "No installed maps available")
        return
    }

    // Log when maps change
    LaunchedEffect(installedMaps) {
        Logger.log("MAPS_LAYERS", LogLevel.INFO,
            "Multiple MBTiles layers active with ${installedMaps.size} installed maps: ${installedMaps.joinToString(", ")}")
    }

    // Create a RasterLayer for each installed map
    // Since they don't overlap, MapLibre will display the appropriate tiles for each region
    installedMaps.forEachIndexed { index, mapId ->
        val filePath = MBTilesPathResolver.getMBTilesFilePath(mapId)
        val mbtilesUri = "mbtiles://$filePath"

        Logger.log("MAPS_LAYERS", LogLevel.DEBUG,
            "Creating layer $index for map $mapId: $mbtilesUri")

        val rasterSource = rememberRasterSource(
            uri = mbtilesUri,
            tileSize = 256  // Standard tile size matching MBTiles content
        )

        RasterLayer(
            id = "maps-layer-$mapId", // Unique ID for each map's layer
            source = rasterSource,
            minZoom = 7.0f,  // Start at zoom 7 (matches MBTiles minzoom)
            maxZoom = 13.0f, // End at zoom 13 (allows some overzoom but prevents pixelation)
            visible = true,
            opacity = const(1.0f)
        )
    }
}
