package org.mountaincircles.app.modules.maps.logic.controllers

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.maps.logic.data.MapSources
import org.mountaincircles.app.modules.maps.checkMapFileExists
import org.mountaincircles.app.modules.maps.deleteAllMapFiles
import org.mountaincircles.app.modules.maps.deleteMapFile
import org.mountaincircles.app.modules.maps.getInstalledMapFileIds
import org.mountaincircles.app.modules.maps.getMapFilePath
import org.mountaincircles.app.network.DownloadManager
import org.mountaincircles.app.network.DownloadRequest
import org.mountaincircles.app.network.createDownloadManager
import org.mountaincircles.app.state.*

/**
 * Effect processor for Maps module
 *
 * Handles side effects like downloading maps and checking file existence.
 */
class MapsEffectProcessor : BaseEffectProcessor() {

    override val moduleId: String = "maps"

    override fun canProcess(effect: Effect): Boolean {
        return when (effect) {
            is MapsEffect -> effect.moduleId == moduleId
            else -> false
        }
    }

    override suspend fun process(context: EffectContext): EffectResult {
        return when (context.effect) {
            is MapsEffect.DownloadMap -> handleDownloadMap(context)
            is MapsEffect.DeleteMap -> handleDeleteMap(context)
            is MapsEffect.ClearAllMaps -> handleClearAllMaps(context)
            is MapsEffect.CheckMapInstalled -> handleCheckMapInstalled(context)
            is MapsEffect.GetInstalledMaps -> handleGetInstalledMaps(context)
            else -> {
                Logger.log("MAPS_EFFECT", LogLevel.WARN, "Unknown effect type: ${context.effect::class}")
                EffectResult.Error(IllegalArgumentException("Unknown effect type"))
            }
        }
    }

    private suspend fun handleDownloadMap(context: EffectContext): EffectResult {
        val effect = context.effect as MapsEffect.DownloadMap
        val mapIds = effect.mapIds

        Logger.log("MAPS_EFFECT", LogLevel.INFO, "Processing download effect for maps: $mapIds")

        try {
            // Dispatch start download action
            dispatchAction("START_DOWNLOAD", mapOf("mapIds" to mapIds))

            val selectedMaps = MapSources.availableMaps.filter { mapIds.contains(it.id) }
            if (selectedMaps.isEmpty()) {
                val error = "No valid maps found for download: $mapIds"
                Logger.log("MAPS_EFFECT", LogLevel.ERROR, error)
                dispatchAction("DOWNLOAD_FAILED", mapOf("errorMessage" to error))
                return EffectResult.Error(IllegalArgumentException(error))
            }

            // Download each map sequentially
            for ((index, map) in selectedMaps.withIndex()) {
                // Check if already installed
                val isInstalled = checkMapFileExists(map.id)
                if (isInstalled) {
                    Logger.log("MAPS_EFFECT", LogLevel.INFO, "Map ${map.id} already installed, skipping")
                    continue
                }

                // Update progress
                dispatchAction("UPDATE_DOWNLOAD_PROGRESS", mapOf(
                    "mapId" to map.id,
                    "mapName" to map.name,
                    "current" to index,
                    "total" to selectedMaps.size,
                    "status" to "Starting download of ${map.name}..."
                ))

                // Download the map using unified DownloadManager
                val filePath = getMapFilePath(map.id)
                val downloadRequest = DownloadRequest(
                    url = map.url,
                    filePath = filePath
                )

                val downloadManager: DownloadManager = createDownloadManager()
                val result = downloadManager.download(downloadRequest) { progressData ->
                    dispatchAction("UPDATE_DOWNLOAD_PROGRESS", mapOf(
                        "mapId" to map.id,
                        "mapName" to map.name,
                        "current" to index,
                        "total" to selectedMaps.size,
                        "bytesDownloaded" to progressData.downloaded,
                        "totalBytes" to progressData.total,
                        "status" to progressData.status
                    ))
                }

                if (result.isFailure) {
                    throw result.exceptionOrNull() ?: Exception("Download failed for ${map.name}")
                }

                Logger.log("MAPS_EFFECT", LogLevel.INFO, "Successfully downloaded map: ${map.id}")
            }

            // Update installed maps list
            val newInstalledMaps = getInstalledMapFileIds()


            // Dispatch completion action
            dispatchAction("DOWNLOAD_COMPLETE", mapOf("installedMaps" to newInstalledMaps))

            Logger.log("MAPS_EFFECT", LogLevel.INFO, "Download effect completed successfully")
            return EffectResult.Success(Unit)

        } catch (e: Exception) {
            Logger.log("MAPS_EFFECT", LogLevel.ERROR, "Download effect failed: ${e.message}", e)
            dispatchAction("DOWNLOAD_FAILED", mapOf("errorMessage" to e.message))
            return EffectResult.Error(e)
        }
    }

    private suspend fun handleDeleteMap(context: EffectContext): EffectResult {
        val effect = context.effect as MapsEffect.DeleteMap
        val mapId = effect.mapId

        Logger.log("MAPS_EFFECT", LogLevel.INFO, "Processing delete map effect: $mapId")

        try {
            val success = deleteMapFile(mapId)
            if (success) {
                // Update installed maps list
                val newInstalledMaps = getInstalledMapFileIds()


                // Dispatch delete action
                dispatchAction("DELETE_MAP", mapOf("mapId" to mapId))

                Logger.log("MAPS_EFFECT", LogLevel.INFO, "Successfully deleted map: $mapId")
                return EffectResult.Success(Unit)
            } else {
                val error = "Failed to delete map file: $mapId"
                Logger.log("MAPS_EFFECT", LogLevel.WARN, error)
                return EffectResult.Error(Exception(error))
            }
        } catch (e: Exception) {
            Logger.log("MAPS_EFFECT", LogLevel.ERROR, "Delete map effect failed: ${e.message}", e)
            return EffectResult.Error(e)
        }
    }

    private suspend fun handleClearAllMaps(context: EffectContext): EffectResult {
        Logger.log("MAPS_EFFECT", LogLevel.INFO, "Processing clear all maps effect")

        try {
            val clearedCount = deleteAllMapFiles()


            // Dispatch clear action
            dispatchAction("CLEAR_ALL_MAPS", emptyMap())

            Logger.log("MAPS_EFFECT", LogLevel.INFO, "Successfully cleared $clearedCount map files")
            return EffectResult.Success(Unit)

        } catch (e: Exception) {
            Logger.log("MAPS_EFFECT", LogLevel.ERROR, "Clear all maps effect failed: ${e.message}", e)
            return EffectResult.Error(e)
        }
    }

    private suspend fun handleCheckMapInstalled(context: EffectContext): EffectResult {
        val effect = context.effect as MapsEffect.CheckMapInstalled
        val mapId = effect.mapId

        try {
            val isInstalled = checkMapFileExists(mapId)
            return EffectResult.Success(isInstalled)
        } catch (e: Exception) {
            Logger.log("MAPS_EFFECT", LogLevel.ERROR, "Check map installed effect failed: ${e.message}", e)
            return EffectResult.Error(e)
        }
    }

    private suspend fun handleGetInstalledMaps(context: EffectContext): EffectResult {
        try {
            val installedMaps = getInstalledMapFileIds()
            return EffectResult.Success(installedMaps)
        } catch (e: Exception) {
            Logger.log("MAPS_EFFECT", LogLevel.ERROR, "Get installed maps effect failed: ${e.message}", e)
            return EffectResult.Error(e)
        }
    }




    private fun dispatchAction(actionType: String, data: Map<String, Any?>) {
        // This would normally dispatch to the Redux store
        // For now, we'll just log the action
        Logger.log("MAPS_EFFECT", LogLevel.DEBUG, "Dispatching action: $actionType with data: $data")
    }

    // Platform-specific functions are now declared as expect in commonMain/MapsModule.kt
}

/**
 * Maps-specific effect types
 */
sealed class MapsEffect : Effect {
    override val moduleId: String = "maps"

    data class DownloadMap(val mapIds: List<String>) : MapsEffect()
    data class DeleteMap(val mapId: String) : MapsEffect()
    data object ClearAllMaps : MapsEffect()
    data class CheckMapInstalled(val mapId: String) : MapsEffect()
    data object GetInstalledMaps : MapsEffect()
    data class StartTileProxy(val installedMaps: List<String>) : MapsEffect()
    data object StopTileProxy : MapsEffect()
    data class UpdateTileProxy(val installedMaps: List<String>) : MapsEffect()
}
