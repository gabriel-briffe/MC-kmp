package org.mountaincircles.app.modules.maps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import platform.Foundation.*
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * iOS platform-specific map file operations
 *
 * Uses NSFileManager to handle file operations on iOS platform.
 */

/**
 * iOS implementation: Get the maps directory path
 */
private fun getMapsDirectory(): String {
    val fileManager = NSFileManager.defaultManager
    val urls = fileManager.URLsForDirectory(
        NSDocumentDirectory,
        NSUserDomainMask
    )
    val documentsUrl = urls.firstOrNull() as? NSURL
    val documentsPath = documentsUrl?.path ?: ""

    return "$documentsPath/maps"
}

/**
 * iOS implementation: Get list of installed map file IDs
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun getInstalledMapFileIds(): List<String> = withContext(Dispatchers.Default) {
    try {
        val mapsDir = getMapsDirectory()
        val fileManager = NSFileManager.defaultManager

        // Create directory if it doesn't exist
        val nsUrl = NSURL.fileURLWithPath(mapsDir)
        try {
            fileManager.createDirectoryAtURL(
                nsUrl,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        } catch (e: Exception) {
            Logger.log("MAPS_IOS", LogLevel.WARN, "Could not create maps directory: $mapsDir")
        }

        // List files in the directory
        val contents = fileManager.contentsOfDirectoryAtPath(mapsDir, error = null)
        val mapFiles = contents?.filter { fileName ->
            (fileName as? String)?.endsWith(".mbtiles") == true
        }?.mapNotNull { fileName ->
            (fileName as? String)?.removeSuffix(".mbtiles")
        } ?: emptyList()

        Logger.log("MAPS_IOS", LogLevel.DEBUG, "Found ${mapFiles.size} installed maps: $mapFiles")
        return@withContext mapFiles

    } catch (e: Exception) {
        Logger.log("MAPS_IOS", LogLevel.ERROR, "Error getting installed maps: ${e.message}")
        return@withContext emptyList()
    }
}

/**
 * iOS implementation: Check if a specific map file exists
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun checkMapFileExists(mapId: String): Boolean = withContext(Dispatchers.Default) {
    try {
        val filePath = getMapFilePath(mapId)
        val fileManager = NSFileManager.defaultManager
        return@withContext fileManager.fileExistsAtPath(filePath)
    } catch (e: Exception) {
        Logger.log("MAPS_IOS", LogLevel.ERROR, "Error checking map file existence: ${e.message}")
        return@withContext false
    }
}

/**
 * iOS implementation: Delete all map files
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun deleteAllMapFiles(): Int = withContext(Dispatchers.Default) {
    try {
        val mapsDir = getMapsDirectory()
        val fileManager = NSFileManager.defaultManager

        if (!fileManager.fileExistsAtPath(mapsDir)) {
            Logger.log("MAPS_IOS", LogLevel.DEBUG, "Maps directory doesn't exist, nothing to delete")
            return@withContext 0
        }

        val contents = fileManager.contentsOfDirectoryAtPath(mapsDir, error = null)
        var deletedCount = 0

        contents?.forEach { fileName ->
            val fileNameStr = fileName as? String
            if (fileNameStr?.endsWith(".mbtiles") == true) {
                val filePath = "$mapsDir/$fileNameStr"
                val nsUrl = NSURL.fileURLWithPath(filePath)
                val success = fileManager.removeItemAtURL(nsUrl, error = null)
                if (success) {
                    deletedCount++
                    Logger.log("MAPS_IOS", LogLevel.DEBUG, "Deleted map file: $fileNameStr")
                }
            }
        }

        Logger.log("MAPS_IOS", LogLevel.INFO, "Deleted $deletedCount map files")
        return@withContext deletedCount

    } catch (e: Exception) {
        Logger.log("MAPS_IOS", LogLevel.ERROR, "Error deleting all map files: ${e.message}")
        return@withContext 0
    }
}

/**
 * iOS implementation: Delete a specific map file
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun deleteMapFile(mapId: String): Boolean = withContext(Dispatchers.Default) {
    try {
        val filePath = getMapFilePath(mapId)
        val fileManager = NSFileManager.defaultManager

        if (!fileManager.fileExistsAtPath(filePath)) {
            Logger.log("MAPS_IOS", LogLevel.WARN, "Map file doesn't exist: $filePath")
            return@withContext true // Consider it successful if file doesn't exist
        }

        val nsUrl = NSURL.fileURLWithPath(filePath)
        val success = fileManager.removeItemAtURL(nsUrl, error = null)

        if (success) {
            Logger.log("MAPS_IOS", LogLevel.INFO, "Successfully deleted map file: $mapId")
        } else {
            Logger.log("MAPS_IOS", LogLevel.ERROR, "Failed to delete map file: $mapId")
        }

        return@withContext success

    } catch (e: Exception) {
        Logger.log("MAPS_IOS", LogLevel.ERROR, "Error deleting map file $mapId: ${e.message}")
        return@withContext false
    }
}

/**
 * iOS implementation: Get the file path for a map
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun getMapFilePath(mapId: String): String = withContext(Dispatchers.Default) {
    val mapsDir = getMapsDirectory()
    return@withContext "$mapsDir/$mapId.mbtiles"
}
