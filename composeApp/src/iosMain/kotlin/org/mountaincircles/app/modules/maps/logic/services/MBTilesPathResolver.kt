package org.mountaincircles.app.modules.maps.logic.services

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import platform.Foundation.*
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * iOS implementation for MBTiles file path resolution
 */
@OptIn(ExperimentalForeignApi::class)
actual fun getPlatformMBTilesFilePath(mapId: String): String {
    Logger.log("MBTILES_PATH_IOS", LogLevel.DEBUG, "Getting MBTiles file path for mapId: $mapId")

    // Get the documents directory path
    val documentsPath = try {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        paths.firstOrNull() as? String ?: ""
    } catch (e: Exception) {
        Logger.log("MBTILES_PATH_IOS", LogLevel.ERROR, "Failed to get documents directory: ${e.message}")
        ""
    }

    if (documentsPath.isBlank()) {
        Logger.log("MBTILES_PATH_IOS", LogLevel.ERROR, "Documents directory is empty")
        return ""
    }

    // Create maps subdirectory if it doesn't exist
    val mapsDir = "$documentsPath/maps"
    val fileManager = NSFileManager.defaultManager

    try {
        val mapsUrl = NSURL.fileURLWithPath(mapsDir)
        if (!fileManager.fileExistsAtPath(mapsDir)) {
            fileManager.createDirectoryAtURL(mapsUrl, true, null, null)
            Logger.log("MBTILES_PATH_IOS", LogLevel.DEBUG, "Created maps directory: $mapsDir")
        }
    } catch (e: Exception) {
        Logger.log("MBTILES_PATH_IOS", LogLevel.WARN, "Failed to create maps directory: ${e.message}")
    }

    val filePath = "$mapsDir/$mapId.mbtiles"
    Logger.log("MBTILES_PATH_IOS", LogLevel.DEBUG, "MBTiles file path: $filePath")
    return filePath
}
