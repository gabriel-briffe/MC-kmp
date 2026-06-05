package org.mountaincircles.app.modules.skysight.logic.controllers

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.RasterResampling
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.sources.rememberImageSource
import org.maplibre.compose.util.PositionQuad
import org.maplibre.spatialk.geojson.Position
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.SkysightApiManager
import org.mountaincircles.app.modules.skysight.logic.SkysightStorage
import org.mountaincircles.app.modules.skysight.logic.data.DownloadStatusType
import org.mountaincircles.app.modules.skysight.logic.data.TileDownloadStatus
import org.mountaincircles.app.modules.skysight.logic.data.TileLayer
import org.mountaincircles.app.ui.components.bytesToImageBitmap
import org.mountaincircles.app.ui.map.LayerDescriptor
import org.mountaincircles.app.ui.map.LayerRegistrationHelper
import org.mountaincircles.app.ui.map.LayerZIndex
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Tile lifecycle for RealTimeTilingController (download status, ensure/remove/add tiles).
 * Extracted for clearer separation of concerns.
 */
object RealTimeTilingControllerTileLifecycle {

    fun checkDownloadStatus(module: SkysightModule, tileKey: String): TileDownloadStatus? {
        return module.state.value.tileDownloadStatuses[tileKey]
    }

    suspend fun setDownloadStatus(module: SkysightModule, tileKey: String, status: DownloadStatusType) {
        val downloadStatus = TileDownloadStatus(status = status)
        module.updateState { currentState ->
            val updatedStatuses = currentState.tileDownloadStatuses.toMutableMap()
            updatedStatuses[tileKey] = downloadStatus
            currentState.copy(tileDownloadStatuses = updatedStatuses)
        }
        Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Set download status for $tileKey: $status")
    }

    suspend fun removeDownloadStatus(module: SkysightModule, tileKey: String) {
        module.updateState { currentState ->
            val updatedStatuses = currentState.tileDownloadStatuses.toMutableMap()
            updatedStatuses.remove(tileKey)
            currentState.copy(tileDownloadStatuses = updatedStatuses)
        }
        Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Removed download status for $tileKey")
    }

    suspend fun ensureTileAvailableForType(
        module: SkysightModule,
        storage: SkysightStorage,
        apiManager: SkysightApiManager,
        tileKey: String,
        layerType: String,
        useRealtimeTimeouts: Boolean = false
    ): Boolean {
        Logger.log("REALTIME_TILING", LogLevel.INFO, "Ensuring $layerType tile available and registering: $tileKey")
        val existingBitmap = checkTileExistsLocallyForType(storage, tileKey, layerType)
        if (existingBitmap != null) {
            Logger.log("REALTIME_TILING", LogLevel.INFO, "$layerType tile exists locally: $tileKey")
            val coordKey = tileKey.substringBeforeLast('_').substringBeforeLast('_')
            if (coordKey in module.state.value.tilesToRender) {
                addTileToMapForType(module, tileKey, existingBitmap, layerType)
                RealTimeTilingController.refreshMap(module)
            } else {
                Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "$layerType cached tile $tileKey is no longer needed, skipping registration")
            }
            return false
        }
        val downloadStatus = checkDownloadStatus(module, tileKey)
        if (downloadStatus != null) {
            when (downloadStatus.status) {
                DownloadStatusType.PENDING,
                DownloadStatusType.FAILED -> {
                    Logger.log("REALTIME_TILING", LogLevel.DEBUG, "$layerType tile download ${downloadStatus.status}: $tileKey")
                    return false
                }
            }
        } else {
            Logger.log("REALTIME_TILING", LogLevel.DEBUG, "$layerType tile no download status (first attempt): $tileKey")
        }
        Logger.log("REALTIME_TILING", LogLevel.INFO, "$layerType tile not found locally, downloading: $tileKey")
        downloadAndAddTileForType(module, storage, apiManager, tileKey, layerType, useRealtimeTimeouts)
        return true
    }

    suspend fun removeTileForType(module: SkysightModule, tileKey: String, layerType: String) {
        Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.DEBUG, "removeTileForType called for $layerType tile: $tileKey")
        module.updateState { it.copy(activeTileLayers = it.activeTileLayers.toMutableMap().apply { remove(tileKey) }) }
        val layerId = "skysight_${tileKey}"
        Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.DEBUG, "Attempting to unregister layer: $layerId")
        try {
            if (LayerRegistrationHelper.layerManager.isLayerRegistered(layerId)) {
                LayerRegistrationHelper.unregisterLayer(layerId)
                Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "Successfully unregistered layer $layerId")
            } else {
                Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.WARN, "Layer $layerId was not registered (already removed)")
            }
        } catch (e: Exception) {
            Logger.log("REALTIME_TILING", LogLevel.ERROR, "Failed to unregister layer $layerId: ${e.message}")
        }
    }

    suspend fun checkTileExistsLocallyForType(storage: SkysightStorage, tileKey: String, layerType: String): ImageBitmap? {
        return try {
            val parts = tileKey.split("_")
            val zoom = parts[1].substring(1).toInt()
            val x = parts[2].substring(1).toInt()
            val y = parts[3].substring(1).toInt()
            val datePart = parts[4]
            val timePart = parts[5]
            val year = datePart.substring(0, 4).toInt()
            val month = datePart.substring(4, 6).toInt()
            val day = datePart.substring(6, 8).toInt()
            val hour = timePart.substring(0, 2).toInt()
            val minute = timePart.substring(2, 4).toInt()
            val bytes = storage.loadTileImage(layerType, zoom, x, y, year, month, day, hour, minute)
            if (bytes != null) {
                Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Loaded $layerType tile: ${tileKey} (${bytes.size} bytes)")
                bytesToImageBitmap(bytes)
            } else {
                Logger.log("REALTIME_TILING", LogLevel.DEBUG, "$layerType tile not found locally: $tileKey")
                null
            }
        } catch (e: Exception) {
            Logger.log("REALTIME_TILING", LogLevel.ERROR, "Error checking local $layerType tile: $tileKey - ${e.message}")
            null
        }
    }

    suspend fun addTileToMapForType(module: SkysightModule, tileKey: String, bitmap: ImageBitmap, layerType: String) {
        Logger.log("REALTIME_TILING", LogLevel.INFO, "Adding $layerType tile to map: $tileKey")
        val coordKey = tileKey.substringBeforeLast('_').substringBeforeLast('_')
        if (coordKey !in module.state.value.tilesToRender) {
            Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "$layerType tile $tileKey is no longer needed, skipping registration")
            return
        }
        val parts = tileKey.split("_")
        if (parts.size >= 6) {
            val datePart = parts[4]
            val timePart = parts[5]
            val tileYear = datePart.substring(0, 4).toInt()
            val tileMonth = datePart.substring(4, 6).toInt()
            val tileDay = datePart.substring(6, 8).toInt()
            val tileHour = timePart.substring(0, 2).toInt()
            val tileMinute = timePart.substring(2, 4).toInt()
            val tileTime = kotlinx.datetime.LocalDateTime(
                year = tileYear, monthNumber = tileMonth, dayOfMonth = tileDay,
                hour = tileHour, minute = tileMinute, second = 0, nanosecond = 0
            ).toInstant(kotlinx.datetime.TimeZone.UTC)
            val currentTime = module.realTimeTimestamp.value
            if (tileTime != currentTime) {
                Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO,
                    "$layerType tile $tileKey time mismatch - tile: $tileTime, current: $currentTime, skipping registration")
                return
            }
        }
        try {
            val partList = tileKey.split("_")
            if (partList.size >= 6) {
                val zoom = partList[1].substring(1).toInt()
                val x = partList[2].substring(1).toInt()
                val y = partList[3].substring(1).toInt()
                val bounds = RealTimeTilingControllerTileMath.calculateTileBounds(zoom, x, y)
                val layerName = tileKey
                val layerId = "skysight_${layerName}"
                LayerRegistrationHelper.registerLayer(
                    moduleId = "skysight",
                    layerName = layerName,
                    zIndex = when (layerType) {
                        "satellite" -> 10 * LayerZIndex.getZIndex("skysight_satellite") + zoom
                        "rain" -> 10 * LayerZIndex.getZIndex("skysight_rain") + zoom
                        else -> 10 * LayerZIndex.getZIndex("skysight")
                    },
                    layerType = LayerDescriptor.LayerType.OVERLAY,
                    isInteractive = false,
                    description = "SkySight $layerType tile for $layerName",
                    tags = setOf("skysight", layerType, "tile", tileKey),
                    composable = {
                        Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Rendering $layerType tile layer: $layerName")
                        val currentOpacity = when (layerType) {
                            "satellite", "rain" -> 1.0f
                            else -> 1.0f
                        }
                        val tileData by remember(tileKey) {
                            derivedStateOf { module.state.value.activeTileLayers[tileKey] }
                        }
                        val currentBitmap = tileData?.bitmap ?: bitmap
                        val tileQuad = remember(bounds) {
                            if (bounds.size >= 4) PositionQuad(
                                topLeft = Position(bounds[0], bounds[3]),
                                topRight = Position(bounds[2], bounds[3]),
                                bottomRight = Position(bounds[2], bounds[1]),
                                bottomLeft = Position(bounds[0], bounds[1])
                            ) else null
                        }
                        if (tileQuad != null) {
                            val imageSource = rememberImageSource(position = tileQuad, bitmap = currentBitmap)
                            RasterLayer(
                                id = layerName,
                                source = imageSource,
                                opacity = const(currentOpacity),
                                resampling = const(RasterResampling.Nearest)
                            )
                            Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Rendered $layerType tile: $tileKey")
                        }
                    }
                )
                val tileLayer = TileLayer(tileKey, bitmap, bounds)
                module.updateState { it.copy(activeTileLayers = it.activeTileLayers.toMutableMap().apply { put(tileKey, tileLayer) }) }
                Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "Successfully registered $layerType tile layer: $layerName (source: $tileKey)")
            } else {
                Logger.log("REALTIME_TILING", LogLevel.ERROR, "Invalid tile key format for $layerType registration: $tileKey")
            }
        } catch (e: Exception) {
            Logger.log("REALTIME_TILING", LogLevel.ERROR, "Failed to add $layerType tile to map $tileKey: ${e.message}")
        }
    }

    suspend fun downloadAndAddTileForType(
        module: SkysightModule,
        storage: SkysightStorage,
        apiManager: SkysightApiManager,
        tileKey: String,
        layerType: String,
        useRealtimeTimeouts: Boolean = false
    ) {
        setDownloadStatus(module, tileKey, DownloadStatusType.PENDING)
        try {
            val parts = tileKey.split("_")
            val zoom = parts[1].substring(1).toInt()
            val x = parts[2].substring(1).toInt()
            val y = parts[3].substring(1).toInt()
            val datePart = parts[4]
            val timePart = parts[5]
            val year = datePart.substring(0, 4).toInt()
            val month = datePart.substring(4, 6).toInt()
            val day = datePart.substring(6, 8).toInt()
            val hour = timePart.substring(0, 2).toInt()
            val minute = timePart.substring(2, 4).toInt()
            Logger.log("REALTIME_TILING", LogLevel.INFO, "Downloading $layerType tile: zoom=$zoom, x=$x, y=$y, time=${year}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')} ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}")
            val downloadResult = apiManager.downloadTileImage(layerType, zoom, x, y, year, month, day, hour, minute, useRealtimeTimeouts)
            if (downloadResult.isSuccess) {
                val imageBytes = downloadResult.getOrNull()!!
                Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Downloaded ${imageBytes.size} bytes of $layerType image data")
                val saveResult = storage.saveTileImage(layerType, imageBytes, zoom, x, y, year, month, day, hour, minute)
                if (saveResult.isSuccess) {
                    Logger.log("REALTIME_TILING", LogLevel.INFO, "$layerType image saved to: ${saveResult.getOrNull()}")
                    val bitmap = bytesToImageBitmap(imageBytes)
                    if (bitmap != null) {
                        Logger.log("REALTIME_TILING", LogLevel.INFO, "$layerType image loaded as bitmap: ${bitmap.width}x${bitmap.height}")
                        addTileToMapForType(module, tileKey, bitmap, layerType)
                        removeDownloadStatus(module, tileKey)
                        RealTimeTilingController.refreshMap(module)
                    } else {
                        Logger.log("REALTIME_TILING", LogLevel.ERROR, "Failed to convert $layerType image bytes to bitmap")
                        removeDownloadStatus(module, tileKey)
                        throw Exception("Bitmap conversion failed")
                    }
                } else {
                    Logger.log("REALTIME_TILING", LogLevel.ERROR, "Failed to save $layerType image: ${saveResult.exceptionOrNull()?.message}")
                    removeDownloadStatus(module, tileKey)
                    throw Exception("Save failed: ${saveResult.exceptionOrNull()?.message}")
                }
            } else {
                val exception = downloadResult.exceptionOrNull()
                val errorMessage = exception?.message ?: "Unknown download error"
                Logger.log("REALTIME_TILING", LogLevel.ERROR, "Failed to download $layerType image: $errorMessage")
                val isHttpError = errorMessage.contains("HTTP") && (errorMessage.contains(" 4") || errorMessage.contains(" 5"))
                if (isHttpError) {
                    setDownloadStatus(module, tileKey, DownloadStatusType.FAILED)
                    Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.DEBUG, "$layerType tile $tileKey HTTP error - marked as FAILED")
                } else {
                    removeDownloadStatus(module, tileKey)
                    Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.DEBUG, "$layerType tile $tileKey non-HTTP error - status removed for retry")
                }
                throw Exception("Download failed: $errorMessage")
            }
        } catch (e: Exception) {
            val currentStatus = checkDownloadStatus(module, tileKey)
            if (currentStatus?.status != DownloadStatusType.FAILED) {
                removeDownloadStatus(module, tileKey)
                Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.DEBUG, "$layerType tile $tileKey unhandled exception - status removed for retry: ${e.message}")
            } else {
                Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.DEBUG, "$layerType tile $tileKey already marked as FAILED, keeping status: ${e.message}")
            }
            throw e
        }
    }
}
