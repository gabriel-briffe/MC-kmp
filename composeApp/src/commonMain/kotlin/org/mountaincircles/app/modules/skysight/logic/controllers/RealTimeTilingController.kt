package org.mountaincircles.app.modules.skysight.logic.controllers

import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.modules.skysight.logic.data.TimePair
import org.mountaincircles.app.modules.skysight.logic.controllers.SkysightTilingControllerV2Viewport
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.math.max
import kotlin.math.min
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.ui.components.bytesToImageBitmap
import org.mountaincircles.app.modules.skysight.logic.SkysightStorage
import org.mountaincircles.app.modules.skysight.logic.SkysightApiManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import org.mountaincircles.app.ui.map.LayerRegistrationHelper
import org.mountaincircles.app.ui.map.LayerDescriptor
import org.mountaincircles.app.ui.map.LayerZIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.maplibre.spatialk.geojson.Position
import org.maplibre.compose.util.PositionQuad
import org.maplibre.compose.expressions.value.RasterResampling
import org.maplibre.compose.expressions.dsl.const

/**
 * RealTime Tiling Controller - Handles satellite and rain radar tiles
 * Similar to SkysightTilingControllerV2 but for PNG-based real-time data
 */
object RealTimeTilingController {

    // ===== VIEWPORT CALCULATION =====



    // ===== HELPER FUNCTIONS =====

    fun calculateVisibleTiles(viewportBounds: List<Double>, globalState: org.mountaincircles.app.state.GlobalState): List<Triple<Int, Int, Int>> =
        RealTimeTilingControllerTileMath.calculateVisibleTiles(viewportBounds, globalState)

    fun calculateVisibleTilesFromBounds(viewportBounds: List<Double>, globalState: org.mountaincircles.app.state.GlobalState): List<Triple<Int, Int, Int>> =
        RealTimeTilingControllerTileMath.calculateVisibleTilesFromBounds(viewportBounds, globalState)










    // ===== DOWNLOAD STATUS (delegated to RealTimeTilingControllerTileLifecycle) =====

    fun checkDownloadStatus(module: SkysightModule, tileKey: String): org.mountaincircles.app.modules.skysight.logic.data.TileDownloadStatus? =
        RealTimeTilingControllerTileLifecycle.checkDownloadStatus(module, tileKey)

    suspend fun setDownloadStatus(
        module: SkysightModule,
        tileKey: String,
        status: org.mountaincircles.app.modules.skysight.logic.data.DownloadStatusType
    ) = RealTimeTilingControllerTileLifecycle.setDownloadStatus(module, tileKey, status)

    suspend fun removeDownloadStatus(module: SkysightModule, tileKey: String) =
        RealTimeTilingControllerTileLifecycle.removeDownloadStatus(module, tileKey)

    // ===== TILE MANAGEMENT =====

    /**
     * Handle camera movement for real-time layers
     * Following V2 pipeline pattern: get viewport bounds, update tiles selectively, refresh map
     */
    suspend fun handleTileUpdate(
        module: SkysightModule,
        globalState: GlobalState,
        timePair: org.mountaincircles.app.modules.skysight.logic.data.TimePair? = null,
        isNavigation: Boolean = false,
        useRealtimeTimeouts: Boolean = true
    ): Result<Unit> {
        val operationType = if (isNavigation) "NAVIGATION" else "CAMERA MOVE"
        Logger.log("REALTIME_TILING", LogLevel.INFO, "=== $operationType STARTED ===")

        return try {
            val hasRealTimeLayers = module.satelliteEnabled.value || module.localRainEnabled.value

            if (hasRealTimeLayers && module.state.value.isVisible) {
                Logger.log("REALTIME_TILING", LogLevel.INFO, "Processing $operationType for realtime layers")

                // Step 1: Get viewport bounds (following V2 pattern exactly)
                Logger.log("REALTIME_TILING", LogLevel.INFO, "getViewportBounds")
                val viewportBounds = SkysightTilingControllerV2Viewport.getViewportBounds(globalState)

                // Step 2: Update tiles selectively for all enabled real-time layers (following V2 pattern - only disappeared and required)
                Logger.log("REALTIME_TILING", LogLevel.INFO, "updateTiles")
                updateTiles(module, viewportBounds, globalState, timePair, useRealtimeTimeouts)

                // Map refresh now happens after each individual tile processing

            } else {
                Logger.log("REALTIME_TILING", LogLevel.DEBUG, "No real-time layers enabled, skipping $operationType handling")
            }


            Logger.log("REALTIME_TILING", LogLevel.INFO, "=== $operationType COMPLETED ===")
            Result.success(Unit)

        } catch (e: Exception) {
            Logger.log("REALTIME_TILING", LogLevel.ERROR, "$operationType failed: ${e.message}")
            Result.failure(e)
        }
    }


    /**
     * Update satellite tiles for real-time display
     * Following V2 pipeline pattern - only clear disappeared tiles, only add required tiles
     */
    suspend fun updateTiles(
        module: SkysightModule,
        viewportBounds: List<Double>,
        globalState: org.mountaincircles.app.state.GlobalState,
        timePair: org.mountaincircles.app.modules.skysight.logic.data.TimePair? = null,
        useRealtimeTimeouts: Boolean = false
    ): Result<Unit> {
        try {
            // Calculate required tiles from viewport bounds
            Logger.log("REALTIME_TILING", LogLevel.INFO, "calculateVisibleTiles from viewport bounds")
            val visibleTiles = calculateVisibleTiles(viewportBounds, globalState)
            Logger.log("REALTIME_TILING_ZZZ", LogLevel.INFO, "Found ${visibleTiles.size} visible tiles for viewport: ${visibleTiles.joinToString(", ")}")

            // Get timestamp for real-time data - use provided timePair or current timestamp
            val timestampToUse = if (timePair != null) {
                // Convert TimePair to timestamp using current date
                val currentDate = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
                val dateTime = kotlinx.datetime.LocalDateTime(
                    year = currentDate.year,
                    monthNumber = currentDate.monthNumber,
                    dayOfMonth = currentDate.dayOfMonth,
                    hour = timePair.hour,
                    minute = timePair.minute,
                    second = 0,
                    nanosecond = 0
                )
                dateTime.toInstant(kotlinx.datetime.TimeZone.UTC)
            } else {
                module.realTimeTimestamp.value
            }
            Logger.log("REALTIME_TILING", LogLevel.INFO, "Using realtime timestamp for tiles: ${timestampToUse}")

            // Convert timestamp to LocalDateTime for tile key generation
            val tileTime = timestampToUse.toLocalDateTime(kotlinx.datetime.TimeZone.UTC)

            Logger.log("REALTIME_TILING", LogLevel.INFO, "Tile time created: ${tileTime.year}-${tileTime.monthNumber.toString().padStart(2, '0')}-${tileTime.dayOfMonth.toString().padStart(2, '0')} ${tileTime.hour.toString().padStart(2, '0')}:${tileTime.minute.toString().padStart(2, '0')}")

            // Simple logic: requested tiles = all tiles needed for current viewport + time
            val requestedTiles = mutableSetOf<String>()

            // Generate all requested tile keys for enabled types
            if (module.satelliteEnabled.value) {
                val satelliteTileKeys = visibleTiles.map { (zoom, x, y) -> generateTileKey("satellite", zoom, x, y, tileTime) }
                requestedTiles.addAll(satelliteTileKeys)
            }
            if (module.localRainEnabled.value) {
                val rainTileKeys = visibleTiles.map { (zoom, x, y) -> generateTileKey("rain", zoom, x, y, tileTime) }
                requestedTiles.addAll(rainTileKeys)
            }

            // Update tilesToRender with coordinate keys (for other parts of the system)
            val coordinateKeys = requestedTiles.map { tileKey ->
                tileKey.substringBeforeLast('_').substringBeforeLast('_') // Remove time then date
            }.toSet()
            module.updateState { currentState ->
                currentState.copy(tilesToRender = coordinateKeys.toMutableSet())
            }
            Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "Updated tilesToRender with ${coordinateKeys.size} coordinates for current view")

            // Determine what to add/remove
            val activeTiles = getAllActiveTiles(module)
            val tilesToAdd = requestedTiles - activeTiles
            val tilesToRemove = activeTiles - requestedTiles

            Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "Tile changes: ${tilesToAdd.size} to add, ${tilesToRemove.size} to remove")
            Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.DEBUG, "Tiles to add: ${tilesToAdd.joinToString(", ")}")
            Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.DEBUG, "Tiles to remove: ${tilesToRemove.joinToString(", ")}")

            val allTilesToAdd = tilesToAdd.toList()
            val allTilesToRemove = tilesToRemove.toList()

            // Process all tiles from all enabled types in one combined batch
            if (allTilesToAdd.isNotEmpty() || allTilesToRemove.isNotEmpty()) {
                Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "Processing ${allTilesToAdd.size} tiles to add, ${allTilesToRemove.size} to remove")
                processCombinedTiles(module, allTilesToAdd, allTilesToRemove, tileTime, useRealtimeTimeouts)
            } else {
                Logger.log("REALTIME_TILING", LogLevel.DEBUG, "No tile changes needed for enabled real-time layers")
            }

            Logger.log("REALTIME_TILING", LogLevel.INFO, "Tile update complete")
            return Result.success(Unit)

        } catch (e: Exception) {
            Logger.log("REALTIME_TILING", LogLevel.ERROR, "Failed to update real-time tiles: ${e.message}", e)
            return Result.failure(e)
        }
    }

    /**
     * Process combined tiles from all enabled real-time layer types (satellite + rain)
     * Uses parallel processing for maximum performance (all tiles download simultaneously)
     */
    private suspend fun processCombinedTiles(
        module: SkysightModule,
        tilesToAdd: List<String>,
        tilesToRemove: List<String>,
        tileTime: kotlinx.datetime.LocalDateTime,
        useRealtimeTimeouts: Boolean = false
    ) {
        Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "=== processCombinedTiles STARTED: ${tilesToAdd.size} to add, ${tilesToRemove.size} to remove ===")

        // Initialize storage and API manager once for all types
        val storage = SkysightStorage(
            fileManager = org.mountaincircles.app.io.getGlobalFileManager(),
            downloadManager = org.mountaincircles.app.network.createDownloadManager()
        )
        val apiManager = SkysightApiManager()
        apiManager.setModule(module)

        // Process tiles: cached ones first (immediate registration), then removal, then downloads

        // First, register any cached tiles immediately (if there are tiles to add)
        var tilesToDownload = emptyList<String>()
        if (tilesToAdd.isNotEmpty()) {
            Logger.log("REALTIME_TILING", LogLevel.INFO, "Processing ${tilesToAdd.size} tiles - separating cached vs downloads")

            // Separate cached tiles from tiles that need downloading
            val cachedTiles = mutableListOf<String>()
            val downloadList = mutableListOf<String>()

            for (tileKey in tilesToAdd) {
                val layerType = tileKey.substringBefore("_")
                val existingBitmap = checkTileExistsLocallyForType(storage, tileKey, layerType)
                if (existingBitmap != null) {
                    cachedTiles.add(tileKey)
                    Logger.log("REALTIME_TILING", LogLevel.DEBUG, "$layerType tile cached: $tileKey")
                } else {
                    // Check if download is blocked (pending/failed status)
                    val downloadStatus = checkDownloadStatus(module, tileKey)
                    val isBlocked = downloadStatus != null && when (downloadStatus.status) {
                        org.mountaincircles.app.modules.skysight.logic.data.DownloadStatusType.PENDING,
                        org.mountaincircles.app.modules.skysight.logic.data.DownloadStatusType.FAILED -> true
                        else -> false
                    }
                    if (isBlocked) {
                        Logger.log("REALTIME_TILING", LogLevel.DEBUG, "$layerType tile download blocked (${downloadStatus?.status}): $tileKey")
                    } else {
                        downloadList.add(tileKey)
                        Logger.log("REALTIME_TILING", LogLevel.DEBUG, "$layerType tile queued for download: $tileKey")
                    }
                }
            }

            Logger.log("REALTIME_TILING", LogLevel.INFO, "Found ${cachedTiles.size} cached tiles, ${downloadList.size} tiles to download")
            tilesToDownload = downloadList

            // Register cached tiles immediately
            if (cachedTiles.isNotEmpty()) {
                Logger.log("REALTIME_TILING", LogLevel.INFO, "Registering ${cachedTiles.size} cached tiles immediately")
                for (tileKey in cachedTiles) {
                    val layerType = tileKey.substringBefore("_")
                    val existingBitmap = checkTileExistsLocallyForType(storage, tileKey, layerType)
                    if (existingBitmap != null) {
                        addTileToMapForType(module, tileKey, existingBitmap, layerType)
                    }
                }
                Logger.log("REALTIME_TILING", LogLevel.INFO, "Registered ${cachedTiles.size} cached tiles")

                // Initial map refresh for cached tiles
                refreshMap(module)

                // Allow map to re-render before removing unwanted tiles
                kotlinx.coroutines.delay(10)
            }
        }

        // Remove old tiles AFTER cached tiles are registered (clean transition) - regardless of tilesToAdd
        if (tilesToRemove.isNotEmpty()) {
            Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "Starting removal of ${tilesToRemove.size} tiles: ${tilesToRemove.joinToString(", ")}")
            for (tileKey in tilesToRemove) {
                val layerType = tileKey.substringBefore("_")
                Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "Removing $layerType tile: $tileKey")
                removeTileForType(module, tileKey, layerType)
            }
            refreshMap(module)
            Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "Completed removal of ${tilesToRemove.size} tiles")
        }

        // Add missing tiles to download queue (instead of downloading immediately)
        if (tilesToDownload.isNotEmpty()) {
            Logger.log("REALTIME_TILING", LogLevel.INFO, "Adding ${tilesToDownload.size} tiles to download queue")

            module.updateState { currentState ->
                val updatedQueue = currentState.tileDownloadList.toMutableList()
                // Add new tiles to queue, avoiding duplicates
                tilesToDownload.forEach { tileKey ->
                    if (!updatedQueue.contains(tileKey)) {
                        updatedQueue.add(tileKey)
                        Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Queued tile for download: $tileKey")
                    } else {
                        Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Tile already in queue: $tileKey")
                    }
                }
                currentState.copy(tileDownloadList = updatedQueue)
            }

            Logger.log("REALTIME_TILING", LogLevel.INFO, "Added ${tilesToDownload.size} tiles to download queue")
        }

        Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "=== processCombinedTiles COMPLETED ===")
    }

    /**
     * Generate tile key for any real-time layer type (satellite, rain, etc.)
     */
    fun generateTileKey(
        layerType: String,
        zoom: Int,
        x: Int,
        y: Int,
        time: kotlinx.datetime.LocalDateTime
    ): String {
        return "${layerType}_z${zoom}_x${x}_y${y}_${time.year}${time.monthNumber.toString().padStart(2, '0')}${time.dayOfMonth.toString().padStart(2, '0')}_${time.hour.toString().padStart(2, '0')}${time.minute.toString().padStart(2, '0')}"
    }

    /**
     * Get active tiles by layer type
     */
    /**
     * Get all active tiles across all enabled layer types
     */
    private fun getAllActiveTiles(module: SkysightModule): Set<String> {
        val activeTiles = mutableSetOf<String>()

        // Get all active tile keys for enabled types
        if (module.satelliteEnabled.value) {
            activeTiles.addAll(module.state.value.activeTileLayers.keys.filter { it.startsWith("satellite_") })
        }
        if (module.localRainEnabled.value) {
            activeTiles.addAll(module.state.value.activeTileLayers.keys.filter { it.startsWith("rain_") })
        }

        Logger.log("REALTIME_TILING", LogLevel.DEBUG, "All active tiles: ${activeTiles.size} - ${activeTiles.joinToString(", ")}")
        return activeTiles
    }


    private suspend fun ensureTileAvailableForType(
        module: SkysightModule,
        storage: SkysightStorage,
        apiManager: SkysightApiManager,
        tileKey: String,
        layerType: String,
        useRealtimeTimeouts: Boolean = false
    ): Boolean = RealTimeTilingControllerTileLifecycle.ensureTileAvailableForType(module, storage, apiManager, tileKey, layerType, useRealtimeTimeouts)

    private suspend fun removeTileForType(module: SkysightModule, tileKey: String, layerType: String) =
        RealTimeTilingControllerTileLifecycle.removeTileForType(module, tileKey, layerType)

    private suspend fun checkTileExistsLocallyForType(storage: SkysightStorage, tileKey: String, layerType: String): ImageBitmap? =
        RealTimeTilingControllerTileLifecycle.checkTileExistsLocallyForType(storage, tileKey, layerType)

    private suspend fun addTileToMapForType(module: SkysightModule, tileKey: String, bitmap: ImageBitmap, layerType: String) =
        RealTimeTilingControllerTileLifecycle.addTileToMapForType(module, tileKey, bitmap, layerType)

    internal suspend fun downloadAndAddTileForType(
        module: SkysightModule,
        storage: SkysightStorage,
        apiManager: SkysightApiManager,
        tileKey: String,
        layerType: String,
        useRealtimeTimeouts: Boolean = false
    ) = RealTimeTilingControllerTileLifecycle.downloadAndAddTileForType(module, storage, apiManager, tileKey, layerType, useRealtimeTimeouts)

    /**
     * Refresh the map after tile updates
     * Following V2 pipeline pattern
     */
    suspend fun refreshMap(module: SkysightModule): Result<Unit> {
        Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "=== REFRESH MAP CALLED ===")
        Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.DEBUG, "Module state during refresh: satelliteEnabled=${module.state.value.satelliteEnabled}, localRainEnabled=${module.state.value.localRainEnabled}, isVisible=${module.state.value.isVisible}")
        Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.DEBUG, "Active tiles during refresh: ${module.state.value.activeTileLayers.keys.joinToString(", ")}")

        try {
            // Trigger batch update for controlled re-rendering
            Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "Calling module.triggerBatchUpdate()")
            module.triggerBatchUpdate()
            Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "Batch update triggered - tiles should re-render")
            Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "=== REFRESH MAP COMPLETED ===")
            return Result.success(Unit)

        } catch (e: Exception) {
            Logger.log("REALTIME_TILING", LogLevel.ERROR, "Failed to trigger batch update: ${e.message}")
            return Result.failure(e)
        }
    }

    /**
     * Clear existing real-time tiles for a specific layer type when disabling that layer
     */
    suspend fun clearTilesForType(module: SkysightModule, layerType: String): Result<Unit> {
        Logger.log("REALTIME_TILING", LogLevel.INFO, "=== CLEAR TILES FOR TYPE START: $layerType ===")
        Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Current module state: satelliteEnabled=${module.state.value.satelliteEnabled}, localRainEnabled=${module.state.value.localRainEnabled}, isVisible=${module.state.value.isVisible}")

        return try {
            // Get all current layers
            val currentLayers = LayerRegistrationHelper.layerManager.layers.value
            Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Total layers in system: ${currentLayers.size}")

            // Find tiles for the specific layer type (now includes timestamp in layer ID)
            val layerPrefix = "skysight_${layerType}_"
            val typeLayers = currentLayers.filter { layer ->
                layer.id.startsWith(layerPrefix)
            }

            Logger.log("REALTIME_TILING", LogLevel.INFO, "Found ${typeLayers.size} $layerType layers to clear: ${typeLayers.map { it.id }.joinToString(", ")}")

            // Unregister layers for this type
            for (layer in typeLayers) {
                try {
                    Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Unregistering $layerType layer: ${layer.id}")
                    LayerRegistrationHelper.unregisterLayer(layer.id)
                    Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Successfully unregistered $layerType layer ${layer.id}")
                } catch (e: Exception) {
                    Logger.log("REALTIME_TILING", LogLevel.ERROR, "Failed to unregister $layerType layer ${layer.id}: ${e.message}")
                    // Continue with other layers
                }
            }

            // Clear the active tile layers state for this type
            val tilesToRemove = module.state.value.activeTileLayers.keys.filter { it.startsWith("${layerType}_") }.toList()
            Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Clearing ${tilesToRemove.size} $layerType tiles from active state: ${tilesToRemove.joinToString(", ")}")

            if (tilesToRemove.isNotEmpty()) {
                Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Updating module state to remove tiles")
                module.updateState { currentState ->
                    val updatedActiveTiles = currentState.activeTileLayers.toMutableMap()
                    tilesToRemove.forEach { tileKey ->
                        updatedActiveTiles.remove(tileKey)
                    }
                    currentState.copy(activeTileLayers = updatedActiveTiles)
                }
                Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Module state updated, tiles removed")
            }

            Logger.log("REALTIME_TILING", LogLevel.INFO, "=== CALLING REFRESH MAP AFTER CLEARING $layerType TILES ===")

            // Trigger map refresh to show tile removals
            refreshMap(module)

            Logger.log("REALTIME_TILING", LogLevel.INFO, "Successfully cleared $layerType tiles")
            Logger.log("REALTIME_TILING", LogLevel.INFO, "=== CLEAR TILES FOR TYPE COMPLETED: $layerType ===")

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.log("REALTIME_TILING", LogLevel.ERROR, "Failed to clear $layerType tiles: ${e.message}")
            Result.failure(e)
        }
    }

}