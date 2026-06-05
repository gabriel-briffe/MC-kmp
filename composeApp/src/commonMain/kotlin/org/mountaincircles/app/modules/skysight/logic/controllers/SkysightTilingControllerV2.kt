package org.mountaincircles.app.modules.skysight.logic.controllers

import kotlinx.datetime.LocalDate
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.data.TimePair
import org.mountaincircles.app.state.GlobalState
/**
 * Clean Tiling Controller V2 - Crystal clear entry points and orchestrators
 * Handles navigation changes and camera moves with separate pipelines
 */
object SkysightTilingControllerV2 {

    // ===== NAVIGATION CHANGE PIPELINE =====

    /**
     * ENTRY POINT: Handle complete navigation change (time/layer/date)
     * This is the main orchestrator for navigation changes
     */
    suspend fun handleNavigationChange(
        module: SkysightModule,
        globalState: GlobalState,
        layerId: String,
        date: LocalDate,
        timePair: TimePair
    ): Result<Unit> {
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "=== NAVIGATION CHANGE STARTED ===")
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Layer: $layerId, Date: $date, Time: ${timePair.display}")

        // Debug: Compare passed parameters vs module state
        Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "=== NAVIGATION DEBUG ===")
        Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "Passed parameters: layerId='$layerId', date='$date', time='${timePair.display}'")
        Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "Module state: selectedLayerId='${module.state.value.selectedLayerId}', selectedDate='${module.state.value.selectedDate}', currentTime='${module.state.value.currentTime.display}'")
        Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "File key from passed params: ${SkysightTilingControllerV2Viewport.constructDataFileKey(layerId, date, timePair.hour, timePair.minute)}")
        Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "File key from module state: ${SkysightTilingControllerV2Viewport.constructDataFileKey(module.state.value.selectedLayerId, module.state.value.selectedDate ?: date, module.state.value.currentTime.hour, module.state.value.currentTime.minute)}")

        return try {
            // Early return if unified visibility is off or no forecast layer selected
            if (!module.state.value.isVisible || module.state.value.selectedLayerId.isEmpty()) {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Skipping navigation change: visibility is off or no forecast layer selected")
                return Result.success(Unit)
            }

            // Early return if zoom is below forecastMinZoom (layers won't be visible anyway)
            val currentCameraState = globalState.currentCameraState.value
            val currentZoom = currentCameraState?.position?.zoom ?: 0.0
            val forecastMinZoom = module.forecastMinZoom.value

            if (currentZoom < forecastMinZoom) {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Skipping navigation change: zoom $currentZoom < minZoom $forecastMinZoom")
                return Result.success(Unit)
            }

            // Step 1: Clear all existing tiles (fresh start)
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "clearAllExistingTiles")
            clearAllExistingTiles(module)
            module.updateState { it.copy(viewportData = null) }
            refreshMap(module)

            // Step 2: Get current viewport bounds
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "getViewportBounds")
            val viewportBounds = SkysightTilingControllerV2Viewport.getViewportBounds(globalState)

            // Step 3: Update tiles for new navigation
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "updateTiles")
            updateTiles(module, globalState, viewportBounds, layerId, date, timePair)

            // Step 4: Update labels for new navigation (only if visible)
            val isLabelsVisible = module.state.value.isLabelsVisible
            if (isLabelsVisible) {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "updateLabels")
                SkysightTilingControllerV2Labels.updateLabels(module, globalState, viewportBounds, layerId, date, timePair)
            } else {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Skipping updateLabels (labels disabled)")
                // Clear viewport data when labels are disabled
                module.updateState { it.copy(viewportData = null) }
            }

            // Step 5: Clear downloading state and refresh map to show new tiles and labels
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Clearing downloading state and refreshing map")
            module.updateState { it.copy(isDownloading = false) }
            refreshMap(module)

            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "=== NAVIGATION CHANGE COMPLETED ===")
            Result.success(Unit)

        } catch (e: Exception) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Navigation change failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Step 1: Clear all existing tiles from map and state
     */
    suspend fun clearAllExistingTiles(module: SkysightModule): Result<Unit> {
        Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "CLEARING ALL EXISTING TILES - START")
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Clearing all existing tiles from map and state")

        return try {
            val activeTiles = SkysightTilingControllerV2Tiles.getActiveTileIds(module)
            Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "FOUND ${activeTiles.size} ACTIVE TILES TO REMOVE: ${activeTiles.joinToString(", ")}")
            for (tileId in activeTiles) {
                Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "UNREGISTERING TILE: $tileId")
                SkysightTilingControllerV2Tiles.removeTile(module, tileId)
            }
            refreshMap(module)
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Successfully cleared all tiles")
            Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "CLEARING ALL EXISTING TILES - COMPLETED")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Failed to clear tiles: ${e.message}")
            Result.failure(e)
        }
    }



    // ===== CAMERA MOVE PIPELINE =====

    /**
     * ENTRY POINT: Handle camera move (pan/zoom)
     * Camera moves require navigation context to parse fresh data
     */
    suspend fun handleCameraMove(
        module: SkysightModule,
        globalState: GlobalState,
        layerId: String,
        date: LocalDate,
        timePair: TimePair
    ): Result<Unit> {
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "=== CAMERA MOVE STARTED ===")
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Layer: $layerId, Date: $date, Time: ${timePair.display}")

        return try {
            // Early return if unified visibility is off or no forecast layer selected
            if (!module.state.value.isVisible || module.state.value.selectedLayerId.isEmpty()) {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Skipping camera move: visibility is off or no forecast layer selected")
                return Result.success(Unit)
            }

            // Early return if zoom is below forecastMinZoom (layers won't be visible anyway)
            val currentCameraState = globalState.currentCameraState.value
            val currentZoom = currentCameraState?.position?.zoom ?: 0.0
            val forecastMinZoom = module.forecastMinZoom.value

            if (currentZoom < forecastMinZoom) {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Skipping camera move: zoom $currentZoom < minZoom $forecastMinZoom")
                return Result.success(Unit)
            }

            // Step 1: Get new viewport bounds
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "getViewportBounds")
            val viewportBounds = SkysightTilingControllerV2Viewport.getViewportBounds(globalState)

            // Step 2: Update tiles for camera move
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "updateTiles")
            updateTiles(module, globalState, viewportBounds, layerId, date, timePair)

            // Step 3: Update labels for camera move (only if visible)
            val isLabelsVisible = module.state.value.isLabelsVisible
            if (isLabelsVisible) {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "updateLabels")
                SkysightTilingControllerV2Labels.updateLabels(module, globalState, viewportBounds, layerId, date, timePair)
            } else {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Skipping updateLabels (labels disabled)")
                // Clear viewport data when labels are disabled
                module.updateState { it.copy(viewportData = null) }
            }

            // Step 4: Clear downloading state and refresh map to show new tiles and labels
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Clearing downloading state and refreshing map")
            module.updateState { it.copy(isDownloading = false) }
            refreshMap(module)

            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "=== CAMERA MOVE COMPLETED ===")
            Result.success(Unit)

        } catch (e: Exception) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Camera move failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update tiles for viewport - handles both navigation changes and camera moves
     * Uses navigation context for data access (layerId, date, timePair)
     */
    suspend fun updateTiles(
        module: SkysightModule,
        globalState: GlobalState,
        viewportBounds: List<Double>,
        layerId: String,
        date: LocalDate,
        timePair: TimePair
    ): Result<Unit> {
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "calculateRequiredTiles")
        val requiredTiles = SkysightTilingControllerV2Tiles.calculateRequiredTiles(viewportBounds)
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "getActiveTileIds")
        val activeTiles = SkysightTilingControllerV2Tiles.getActiveTileIds(module)
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Required tiles: ${requiredTiles.size}, Active tiles: ${activeTiles.size}")
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "getTilesToRemove and getTilesToAdd")
        val tilesToRemove = SkysightTilingControllerV2Tiles.getTilesToRemove(activeTiles, requiredTiles)
        val tilesToAdd = SkysightTilingControllerV2Tiles.getTilesToAdd(activeTiles, requiredTiles)
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Tiles to remove: ${tilesToRemove.size}, Tiles to add: ${tilesToAdd.size}")
        for (tileId in tilesToRemove) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "removeTile: $tileId")
            SkysightTilingControllerV2Tiles.removeTile(module, tileId)
        }
        for (tileId in tilesToAdd) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "createSingleTile: $tileId")
            SkysightTilingControllerV2Tiles.createSingleTile(module, globalState, tileId, layerId, date, timePair)
        }
        return Result.success(Unit)
    }


    /**
     * Refresh map to show newly registered tiles
     * Triggers batch update for controlled re-rendering of all tiles at once
     */
    suspend fun refreshMap(module: SkysightModule): Result<Unit> {
        Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "REFRESH MAP - START")
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Triggering batch update for tile rendering")

        try {
            // Trigger batch update for controlled re-rendering (same as V1)
            module.triggerBatchUpdate()
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Batch update triggered - tiles should render together")
            Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "REFRESH MAP - COMPLETED")
            return Result.success(Unit)
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Failed to trigger batch update: ${e.message}")
            return Result.failure(e)
        }
    }
}
