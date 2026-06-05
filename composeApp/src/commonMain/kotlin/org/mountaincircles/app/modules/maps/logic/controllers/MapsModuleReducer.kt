package org.mountaincircles.app.modules.maps.logic.controllers

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.maps.logic.data.DownloadProgress
import org.mountaincircles.app.modules.maps.logic.data.MapSources
import org.mountaincircles.app.modules.maps.logic.data.MapsState
import org.mountaincircles.app.state.ModuleAction
import org.mountaincircles.app.state.ModuleReducer

/**
 * Reducer for Maps module state management
 *
 * Handles all state changes through the Redux pattern with actions and reducers.
 */
class MapsModuleReducer : ModuleReducer<org.mountaincircles.app.modules.ModuleState>() {

    override fun getModuleId(): String = "maps"

    override fun createInitialState(): org.mountaincircles.app.modules.ModuleState = MapsState()

    override fun reduceCustom(state: org.mountaincircles.app.modules.ModuleState, action: ModuleAction.Custom): org.mountaincircles.app.modules.ModuleState {
        if (state !is MapsState) return state

        return when (action.action) {
            "INITIALIZE_MODULE" -> {
                val installedMaps = action.data["installedMaps"] as? List<String> ?: emptyList()
                Logger.log("MAPS_REDUCER", LogLevel.INFO, "Initializing maps module state with ${installedMaps.size} installed maps: $installedMaps")
                state.copy(
                    isInitialized = true,
                    installedMaps = installedMaps
                )
            }

            "SET_ERROR" -> {
                val errorMessage = action.data["errorMessage"] as? String ?: "Unknown error"
                Logger.log("MAPS_REDUCER", LogLevel.ERROR, "Setting error state: $errorMessage")
                state.copy(
                    hasError = true,
                    errorMessage = errorMessage
                )
            }

            "CLEAR_ERROR" -> {
                Logger.log("MAPS_REDUCER", LogLevel.INFO, "Clearing error state")
                state.copy(
                    hasError = false,
                    errorMessage = null
                )
            }

            "START_DOWNLOAD" -> {
                val mapIds = action.data["mapIds"] as? List<String> ?: emptyList()
                Logger.log("MAPS_REDUCER", LogLevel.INFO, "Starting download for maps: $mapIds")

                val progress = if (mapIds.isNotEmpty()) {
                    val firstMap = MapSources.availableMaps.find { it.id == mapIds.first() }
                    DownloadProgress(
                        mapId = mapIds.first(),
                        mapName = firstMap?.name ?: "Unknown",
                        current = 0,
                        total = mapIds.size,
                        bytesDownloaded = 0,
                        totalBytes = 0,
                        status = "Starting download..."
                    )
                } else null

                state.copy(
                    isDownloading = true,
                    downloadProgress = progress
                )
            }

            "UPDATE_DOWNLOAD_PROGRESS" -> {
                val mapId = action.data["mapId"] as? String
                val mapName = action.data["mapName"] as? String
                val current = action.data["current"] as? Int ?: 0
                val total = action.data["total"] as? Int ?: 1
                val bytesDownloaded = action.data["bytesDownloaded"] as? Long ?: 0L
                val totalBytes = action.data["totalBytes"] as? Long ?: 0L
                val status = action.data["status"] as? String ?: ""

                val updatedProgress = state.downloadProgress?.copy(
                    mapId = mapId ?: state.downloadProgress.mapId,
                    mapName = mapName ?: state.downloadProgress.mapName,
                    current = current,
                    total = total,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                    status = status
                )

                Logger.log("MAPS_REDUCER", LogLevel.DEBUG, "Download progress update: $status")
                state.copy(downloadProgress = updatedProgress)
            }

            "DOWNLOAD_COMPLETE" -> {
                val newInstalledMaps = action.data["installedMaps"] as? List<String> ?: emptyList()
                Logger.log("MAPS_REDUCER", LogLevel.INFO, "Download completed. Installed maps: $newInstalledMaps")
                state.copy(
                    isDownloading = false,
                    downloadProgress = null,
                    installedMaps = newInstalledMaps,
                    hasError = false,
                    errorMessage = null
                )
            }

            "DOWNLOAD_FAILED" -> {
                val errorMessage = action.data["errorMessage"] as? String ?: "Download failed"
                Logger.log("MAPS_REDUCER", LogLevel.ERROR, "Download failed: $errorMessage")
                state.copy(
                    isDownloading = false,
                    downloadProgress = null,
                    hasError = true,
                    errorMessage = errorMessage
                )
            }

            "DELETE_MAP" -> {
                val mapId = action.data["mapId"] as? String ?: ""
                Logger.log("MAPS_REDUCER", LogLevel.INFO, "Deleting map: $mapId")

                val newInstalledMaps = state.installedMaps.toMutableList()
                newInstalledMaps.remove(mapId)

                state.copy(
                    installedMaps = newInstalledMaps,
                    hasError = false,
                    errorMessage = null
                )
            }

            "CLEAR_ALL_MAPS" -> {
                Logger.log("MAPS_REDUCER", LogLevel.INFO, "Clearing all maps")
                state.copy(
                    installedMaps = emptyList(),
                    hasError = false,
                    errorMessage = null
                )
            }

            "UPDATE_INSTALLED_MAPS" -> {
                val newInstalledMaps = action.data["installedMaps"] as? List<String> ?: emptyList()
                Logger.log("MAPS_REDUCER", LogLevel.INFO, "Updating installed maps: $newInstalledMaps")
                state.copy(installedMaps = newInstalledMaps)
            }

            "SET_MODULE_AVAILABILITY" -> {
                val available = action.data["available"] as? Boolean ?: false
                Logger.log("MAPS_REDUCER", LogLevel.INFO, "Setting module availability: $available")
                state.copy(hasDataToRender = available)
            }

            else -> {
                Logger.log("MAPS_REDUCER", LogLevel.WARN, "Unknown action: ${action.action}")
                state
            }
        }
    }

}
