package org.mountaincircles.app.modules.wave.layer.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.offline.OfflineManagerException
import org.maplibre.compose.offline.rememberOfflineManager
import org.maplibre.compose.sources.rememberRasterSource
import org.maplibre.compose.sources.rememberVectorSource
import org.maplibre.compose.expressions.dsl.*
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.modules.wave.logic.data.RasterData
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.modules.wave.logic.data.WaveSelection
import org.mountaincircles.app.ui.map.*
import org.mountaincircles.app.utils.currentTimeMillis
import androidx.compose.ui.graphics.painter.Painter


/**
 * Wave module LayerManager implementation
 *
 * This manages the wave forecast raster layers using the new LayerManager system,
 * providing better control over layer priority, visibility, and lifecycle.
 */
class WaveLayerManager(private val waveModule: WaveModule) {

    private val layerIds = mutableListOf<String>()

    // Public getter for layer IDs (used by composable for reactive visibility)
    val layerIdsPublic: List<String> get() = layerIds


    // Track current layer source ID
    private var currentSourceId: String? = null

    /**
     * Initialize and register all wave layers with the LayerManager
     */
    fun initializeLayers() {
        Logger.log("WAVE_LAYERS", LogLevel.INFO,
            "Initializing wave layers with LayerManager")

        // Register the main wave raster layer
        Logger.log("WAVE_LAYERS", LogLevel.DEBUG, "Registering wave raster layer with LayerManager")
        val waveLayerId = LayerRegistrationHelper.registerLayer(
            moduleId = "wave",
            layerName = "wave_raster",
            zIndex = 10 * LayerZIndex.getZIndex("wave_raster"),
            layerType = LayerDescriptor.LayerType.FEATURE,
            isInteractive = false,
            description = "Wave forecast raster layer with MBTiles",
            tags = setOf("wave", "forecast", "raster", "mbtiles"),
            composable = { WaveRasterLayerComposable(waveModule) }
        )
        Logger.log("WAVE_LAYERS", LogLevel.DEBUG, "Wave layer registered with ID: $waveLayerId")

        layerIds.add(waveLayerId)

        // Register wind vectors layer (above wave raster)
        Logger.log("WAVE_LAYERS", LogLevel.DEBUG, "Registering wind vectors layer with LayerManager")
        val windLayerId = LayerRegistrationHelper.registerLayer(
            moduleId = "wave",
            layerName = "wave_wind_vectors",
            zIndex = 10 * LayerZIndex.getZIndex("wave_wind_vectors"),
            layerType = LayerDescriptor.LayerType.FEATURE,
            isInteractive = false,
            description = "Wind vectors layer with wind barb icons",
            tags = setOf("wave", "vector", "wind", "barbs"),
            composable = { WaveWindLayerComposable(waveModule) }
        )
        Logger.log("WAVE_LAYERS", LogLevel.DEBUG, "Wind vectors layer registered with ID: $windLayerId")

        layerIds.add(windLayerId)

        Logger.log("WAVE_LAYERS", LogLevel.INFO,
            "Registered ${layerIds.size} wave layers with LayerManager")
    }

    /**
     * Load a wave layer with robust validation and error handling
     */

    /**
     * Get the current layer source ID
     */
    fun getCurrentSourceId(): String? = currentSourceId



    /**
     * Request ambient cache clearing - to be called from a composable context
     * This triggers the cache clearing process after wave import
     */
    fun requestCacheClear() {
        Logger.log("WAVE_LAYERS", LogLevel.INFO, "🗑️ Cache clear requested - will be executed in composable context")
        // This will be called from WaveModule in a composable context
    }

    /**
     * Clean up layers when module is destroyed
     */
    fun cleanup() {
        Logger.log("WAVE_LAYERS", LogLevel.INFO,
            "Cleaning up ${layerIds.size} wave layers")


        // Clear current source ID
        currentSourceId = null

        // Unregister layers
        layerIds.forEach { layerId ->
            LayerRegistrationHelper.layerManager.unregisterLayer(layerId)
        }

        layerIds.clear()

        Logger.log("WAVE_LAYERS", LogLevel.INFO, "Wave layer cleanup completed")
    }
}

/**
 * Individual layer composables that can be registered separately
 */

/**
 * Composable function to clear MapLibre ambient cache
 * This is called from WaveModule after import to prevent stale MBTiles data
 */
@Composable
fun ClearAmbientCacheEffect(clearRequested: Boolean, onCacheCleared: () -> Unit) {
    if (!clearRequested) return

    Logger.log("WAVE_LAYERS", LogLevel.INFO, "🗑️ Executing ambient cache clear in composable context")

    val offlineManager = rememberOfflineManager()
    val scope = rememberCoroutineScope()

    LaunchedEffect(clearRequested) {
        scope.launch {
            try {
                Logger.log("WAVE_LAYERS", LogLevel.INFO, "🗑️ Clearing MapLibre ambient cache...")
                offlineManager.clearAmbientCache()
                Logger.log("WAVE_LAYERS", LogLevel.INFO, "✅ Ambient cache cleared successfully")
                onCacheCleared()
            } catch (e: OfflineManagerException) {
                Logger.log("WAVE_LAYERS", LogLevel.ERROR, "❌ Failed to clear ambient cache: ${e.message}", e)
                onCacheCleared() // Still call callback even on error
            } catch (e: Exception) {
                Logger.log("WAVE_LAYERS", LogLevel.ERROR, "❌ Unexpected error clearing ambient cache: ${e.message}", e)
                onCacheCleared() // Still call callback even on error
            }
        }
    }
}

/**
 * Standalone Wave Raster Layer Composable
 * EXTRACTED FROM WaveLayerManager CLASS to fix Compose runtime issues
 * This ensures proper recomposition when wave state changes
 */
@Composable
fun WaveRasterLayerComposable(waveModule: WaveModule) {
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Wave raster layer recomposing")

    // Use selective raster flow for optimal performance
    val rasterData by waveModule.layerDataFlow.collectAsState(
        initial = RasterData(
            isVisible = false,
            opacity = 0.75f,
            selection = WaveSelection("", "", 12, 500),
            entriesCount = 0,
            hasEntries = false
        )
    )

    // Single reactive effect - triggers specifically when raster data changes
    LaunchedEffect(rasterData) {
        Logger.log("WAVE_LAYERS", LogLevel.DEBUG, "Wave layer updated: ${rasterData.selection.filePath}")
    }

    // Early returns for invalid states
    if (!rasterData.isVisible) {
        return
    }

    if (!rasterData.selection.isValid() || rasterData.selection.filePath.isBlank()) {
        return
    }

    // Use the entire selection as key to ensure layer updates when any part changes
    val currentSelection = rasterData.selection

    // Ensure layer visibility is set correctly BEFORE rendering
    val shouldShow = rasterData.isVisible && rasterData.selection.isValid() && rasterData.selection.filePath.isNotBlank()

    // Layer visibility is managed by the LayerManager class
    // This composable function focuses only on rendering the raster layer

    // Create raster source using direct MBTiles access
    val filePath = currentSelection.filePath
    val mbtilesUri = "mbtiles://$filePath"

    // 🔍 ENHANCED LOGGING: Log file path and URI for debugging
    Logger.log("WAVE_LAYERS", LogLevel.DEBUG, "Creating raster source for wave file:")
    Logger.log("WAVE_LAYERS", LogLevel.DEBUG, "  File path: $filePath")
    Logger.log("WAVE_LAYERS", LogLevel.DEBUG, "  MBTiles URI: $mbtilesUri")
    Logger.log("WAVE_LAYERS", LogLevel.DEBUG, "  Selection: ${currentSelection.forecastDate}/${currentSelection.targetDate} ${currentSelection.hour}:00 ${currentSelection.pressure}hPa")

    // VALIDATION: Basic file path validation (can't check existence in composable)
    if (filePath.isBlank()) {
        Logger.log("WAVE_LAYERS", LogLevel.ERROR, "Wave file path is blank")
        return
    }

    Logger.log("WAVE_LAYERS", LogLevel.DEBUG, "Creating raster source")

    // Create raster source using direct MBTiles access
    // Note: rememberRasterSource may throw exceptions that cause app freeze
    // The errors you saw (NSPredicate and SQLite exceptions) likely come from here
    val rasterSource = rememberRasterSource(uri = mbtilesUri)

    // Create raster layer with static ID matching layer registration - source recreation happens on URI change
    RasterLayer(
        id = "wave_wave_raster",  // Matches LayerRegistrationHelper: moduleId_layerName
        source = rasterSource,
        opacity = const(rasterData.opacity)
    )
}

/**
 * Wind Vectors Layer Composable
 * Displays wind vectors as wind barb icons using inline GeoJSON
 */
@Composable
fun WaveWindLayerComposable(waveModule: WaveModule) {
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Wind layer recomposing")

    // Get only the specific state fields we need to avoid unnecessary recompositions
    val windLayerVisible by waveModule.windLayerVisibilityFlow.collectAsState()
    val windFeatureCollection by waveModule.windVectorsFeatureCollectionFlow.collectAsState()
    val windBarbSize by waveModule.windBarbSizeFlow.collectAsState()
    val windSpeedScaleDistortion by waveModule.windSpeedScaleDistortionFlow.collectAsState()
    val barbInterval by waveModule.barbIntervalFlow.collectAsState()

    // Only render if wind layer is visible and we have wind data
    if (!windLayerVisible) {
        Logger.log("WAVE_WIND", LogLevel.DEBUG, "Wind layer not visible, skipping render")
        return
    }

    if (windFeatureCollection == null) {
        Logger.log("WAVE_WIND", LogLevel.DEBUG, "No wind data to render")
        return
    }

    Logger.log("WAVE_WIND", LogLevel.DEBUG, "Rendering wind layer with FeatureCollection data (${windFeatureCollection!!.features.size} features)")

    // Create GeoJSON source with the wind data (windFeatureCollection is guaranteed non-null here due to earlier check)
    val vectorSource = org.maplibre.compose.sources.rememberGeoJsonSource(
        data = org.maplibre.compose.sources.GeoJsonData.Features(windFeatureCollection!!)
    )

    // Render wind vectors with SymbolLayer
    org.maplibre.compose.layers.SymbolLayer(
            id = "wave_wind_vectors",
            source = vectorSource,
            iconImage = step(
                input = feature["speed"].asNumber(),
                fallback = image(AppIcons.WindBarb0()), // For speeds < 1.0
                1.0f to image(AppIcons.WindBarb2()),
                2.5f to image(AppIcons.WindBarb5()),
                5.0f to image(AppIcons.WindBarb10()),
                7.5f to image(AppIcons.WindBarb15()),
                10.0f to image(AppIcons.WindBarb20()),
                12.5f to image(AppIcons.WindBarb25()),
                15.0f to image(AppIcons.WindBarb30()),
                17.5f to image(AppIcons.WindBarb35()),
                20.0f to image(AppIcons.WindBarb40()),
                22.5f to image(AppIcons.WindBarb45()),
                25.0f to image(AppIcons.WindBarb50()),
                27.5f to image(AppIcons.WindBarb55()),
                30.0f to image(AppIcons.WindBarb60()),
                32.5f to image(AppIcons.WindBarb65()),
                35.0f to image(AppIcons.WindBarb70()),
                37.5f to image(AppIcons.WindBarb75()),
                40.0f to image(AppIcons.WindBarb80()),
                42.5f to image(AppIcons.WindBarb85()),
                45.0f to image(AppIcons.WindBarb90()),
                47.5f to image(AppIcons.WindBarb95()),
                50.0f to image(AppIcons.WindBarb100()),
                52.5f to image(AppIcons.WindBarb105()),
                55.0f to image(AppIcons.WindBarb110()),
                57.5f to image(AppIcons.WindBarb115()),
                60.0f to image(AppIcons.WindBarb120()),
                62.5f to image(AppIcons.WindBarb125()),
                65.0f to image(AppIcons.WindBarb130()),
                67.5f to image(AppIcons.WindBarb135()),
                70.0f to image(AppIcons.WindBarb140()),
                72.5f to image(AppIcons.WindBarb145()),
                75.0f to image(AppIcons.WindBarb150()),
                77.5f to image(AppIcons.WindBarb155()),
                80.0f to image(AppIcons.WindBarb160()),
                82.5f to image(AppIcons.WindBarb165()),
                85.0f to image(AppIcons.WindBarb170()),
                87.5f to image(AppIcons.WindBarb175()),
                90.0f to image(AppIcons.WindBarb180()),
                92.5f to image(AppIcons.WindBarb185()),
                95.0f to image(AppIcons.WindBarb190())
            ), // Dynamic wind barb based on speed
            iconSize = const(windBarbSize) * (const(1f - windSpeedScaleDistortion) + const(windSpeedScaleDistortion) * (feature["speed"].asNumber() / const(15.0f))), // Dynamic scaling with configurable distortion
            iconRotate = feature["direction"].asNumber() + const(180.0f), // Rotate by wind direction + 180°
            iconAllowOverlap = const(true),
            iconIgnorePlacement = const(true),
            iconAnchor = const(SymbolAnchor.Center)
        )
}
