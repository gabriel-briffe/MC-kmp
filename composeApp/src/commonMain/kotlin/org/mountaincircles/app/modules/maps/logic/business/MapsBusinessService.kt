package org.mountaincircles.app.modules.maps.logic.business

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.maps.logic.data.DownloadProgress
import org.mountaincircles.app.modules.maps.logic.data.MapSources
import org.mountaincircles.app.network.DownloadManager
import org.mountaincircles.app.network.DownloadRequest
import org.mountaincircles.app.network.createDownloadManager
import org.mountaincircles.app.modules.maps.MapsModule
import org.mountaincircles.app.modules.maps.getInstalledMapFileIds
import org.mountaincircles.app.modules.maps.checkMapFileExists
import org.mountaincircles.app.modules.maps.deleteAllMapFiles
import org.mountaincircles.app.modules.maps.deleteMapFile
import org.mountaincircles.app.modules.maps.getMapFilePath

/**
 * Maps Business Service
 * Handles core business logic for map operations
 */
class MapsBusinessService(private val module: MapsModule) {



    /**
     * Clear all installed maps
     */
    suspend fun clearAllMaps() {
        Logger.log("MAPS", LogLevel.INFO, "Clearing all installed maps")

        try {
            val clearedCount = deleteAllMapFiles()

            // Direct MBTiles access - no server update needed

            // Update state
            module.updateState {
                it.copy(
                    installedMaps = emptyList(),
                    hasError = false,
                    errorMessage = null
                )
            }

            Logger.log("MAPS", LogLevel.INFO, "Successfully cleared $clearedCount map files")

        } catch (e: Exception) {
            Logger.log("MAPS", LogLevel.ERROR, "Failed to clear maps: ${e.message}", e)
            // Update state for error
            module.updateState {
                it.copy(
                    hasError = true,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * Delete a specific installed map
     */
    suspend fun deleteMap(mapId: String) {
        Logger.log("MAPS", LogLevel.INFO, "Deleting map: $mapId")

        try {
            val success = deleteMapFile(mapId)
            if (success) {
                // Update installed maps list
                val newInstalledMaps = getInstalledMapFileIds()

                // Direct MBTiles access - no server update needed

                // Update state
                module.updateState {
                    it.copy(
                        installedMaps = newInstalledMaps,
                        hasError = false,
                        errorMessage = null
                    )
                }

                Logger.log("MAPS", LogLevel.INFO, "Successfully deleted map: $mapId")
            } else {
                Logger.log("MAPS", LogLevel.WARN, "Failed to delete map file: $mapId")
            }

        } catch (e: Exception) {
            Logger.log("MAPS", LogLevel.ERROR, "Failed to delete map $mapId: ${e.message}", e)
            // Update state for error
            module.updateState {
                it.copy(
                    hasError = true,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * Import MBTiles file from device storage
     */
    suspend fun importMBTilesFile(filePath: String) {
        Logger.log("MAPS", LogLevel.INFO, "Importing MBTiles file: $filePath")

        try {
            // Check if file exists
            if (!checkMapFileExists(filePath)) {
                throw Exception("MBTiles file not found: $filePath")
            }

            // TODO: Validate MBTiles format
            // TODO: Extract map metadata
            // TODO: Copy file to app storage
            // TODO: Update installed maps list

            Logger.log("MAPS", LogLevel.INFO, "MBTiles file imported successfully")

        } catch (e: Exception) {
            Logger.log("MAPS", LogLevel.ERROR, "Failed to import MBTiles file: ${e.message}", e)
            module.updateState {
                it.copy(
                    hasError = true,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * Generate custom map for specified area
     */
    suspend fun generateCustomMap(
        latitude: Double,
        longitude: Double,
        zoomLevels: IntRange = 8..14,
        radiusKm: Double = 50.0
    ) {
        Logger.log("MAPS", LogLevel.INFO, "Generating custom map at $latitude, $longitude")

        try {
            // TODO: Implement custom map generation logic
            // This would involve:
            // 1. Calculate tile bounds for the area
            // 2. Download tiles for each zoom level
            // 3. Create MBTiles file
            // 4. Save to storage

            Logger.log("MAPS", LogLevel.WARN, "Custom map generation not yet implemented")

        } catch (e: Exception) {
            Logger.log("MAPS", LogLevel.ERROR, "Failed to generate custom map: ${e.message}", e)
            module.updateState {
                it.copy(
                    hasError = true,
                    errorMessage = e.message
                )
            }
        }
    }
}
