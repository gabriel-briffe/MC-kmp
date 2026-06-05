package org.mountaincircles.app.modules.skysight.logic

import org.mountaincircles.app.io.FileManager
import org.mountaincircles.app.network.DownloadManager
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * iOS implementation of SkysightStorage
 * TODO: Implement GZIP decompression for iOS
 */
actual class SkysightStorage actual constructor(
    private val fileManager: FileManager,
    private val downloadManager: DownloadManager
) {

    /**
     * Ensure the skysight directory exists
     */
    actual suspend fun ensureSkysightDirectory(): Result<Unit> {
        return try {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Ensuring skysight directory exists")

            val dataDir = fileManager.getAppDataDirectory()
            val skysightDir = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}"

            // Create directory if it doesn't exist
            val success = fileManager.createDirectory(skysightDir)

            if (success) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Skysight directory ready: $skysightDir")
                Result.success(Unit)
            } else {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to create skysight directory: $skysightDir")
                Result.failure(Exception("Failed to create skysight directory"))
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Error ensuring skysight directory: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Check if a forecast file exists
     */
    actual suspend fun fileExists(fileKey: String): Boolean {
        val dataDir = fileManager.getAppDataDirectory()
        val filePath = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}/$fileKey${SkysightConstants.FILE_EXTENSION}"

        val exists = fileManager.exists(filePath) && fileManager.getFileSize(filePath) > 0
        Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "File check for $fileKey: ${if (exists) "EXISTS" else "NOT FOUND"}")
        return exists
    }

    /**
     * Download a forecast file from URL
     * iOS implementation - GZIP decompression not yet implemented
     */
    actual suspend fun downloadFile(fileKey: String, url: String): Result<Unit> {
        Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Skysight downloads not yet implemented for iOS")
        return Result.failure(Exception("Skysight downloads not implemented for iOS"))
    }

    actual suspend fun saveRadarImage(imageBytes: ByteArray, timestamp: String): Result<String> {
        return try {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Saving radar image: ${imageBytes.size} bytes, timestamp: $timestamp")

            val dataDir = fileManager.getAppDataDirectory()
            val rainDir = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}/rain"

            // Ensure rain directory exists
            val dirCreated = fileManager.createDirectory(rainDir)
            if (!dirCreated) {
                return Result.failure(Exception("Failed to create rain directory"))
            }

            val fileName = "radar_${timestamp}.png"
            val filePath = "$rainDir/$fileName"

            // Save the image bytes
            val success = fileManager.writeBytes(filePath, imageBytes)

            if (success) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.INFO, "Radar image saved: $filePath (${imageBytes.size} bytes)")
                Result.success(filePath)
            } else {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to save radar image to $filePath")
                Result.failure(Exception("Failed to write radar image file"))
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to save radar image: ${e.message}", e)
            Result.failure(e)
        }
    }

    actual suspend fun saveSatelliteImage(imageBytes: ByteArray, filename: String): Result<String> {
        return try {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Saving satellite image: ${imageBytes.size} bytes, filename: $filename")

            val dataDir = fileManager.getAppDataDirectory()
            val satelliteDir = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}/satellite"

            // Ensure satellite directory exists
            val dirCreated = fileManager.createDirectory(satelliteDir)
            if (!dirCreated) {
                return Result.failure(Exception("Failed to create satellite directory"))
            }

            val filePath = "$satelliteDir/$filename"

            // Save the image bytes
            val success = fileManager.writeBytes(filePath, imageBytes)

            if (success) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.INFO, "Satellite image saved: $filePath (${imageBytes.size} bytes)")
                Result.success(filePath)
            } else {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to save satellite image to $filePath")
                Result.failure(Exception("Failed to write satellite image file"))
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to save satellite image: ${e.message}", e)
            Result.failure(e)
        }
    }

    actual suspend fun getSatelliteTilePath(filename: String): String? {
        return try {
            val dataDir = fileManager.getAppDataDirectory()
            val satelliteDir = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}/satellite"
            val filePath = "$satelliteDir/$filename"

            if (fileManager.exists(filePath) && fileManager.getFileSize(filePath) > 0) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Satellite tile exists: $filePath")
                filePath
            } else {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Satellite tile does not exist: $filePath")
                null
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Error checking satellite tile existence: ${e.message}", e)
            null
        }
    }

    actual suspend fun loadSatelliteTile(filename: String): ByteArray? {
        return try {
            val tilePath = getSatelliteTilePath(filename)
            if (tilePath != null) {
                val bytes = fileManager.readBytes(tilePath)
                if (bytes != null) {
                    Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Loaded satellite tile: $filename (${bytes.size} bytes)")
                    bytes
                } else {
                    Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to read satellite tile bytes: $filename")
                    null
                }
            } else {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Satellite tile not found: $filename")
                null
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Error loading satellite tile: ${e.message}", e)
            null
        }
    }

    actual suspend fun saveTileImage(layerType: String, imageBytes: ByteArray, zoom: Int, x: Int, y: Int, year: Int, month: Int, day: Int, hour: Int, minute: Int): Result<String> {
        return try {
            val filename = "${layerType}_z${zoom}_x${x}_y${y}_${year}${month.toString().padStart(2, '0')}${day.toString().padStart(2, '0')}_${hour.toString().padStart(2, '0')}${minute.toString().padStart(2, '0')}.jpg"
            Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Saving $layerType tile: ${imageBytes.size} bytes, filename: $filename")

            val dataDir = fileManager.getAppDataDirectory()
            val layerDir = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}/$layerType"

            // Ensure layer directory exists
            val dirCreated = fileManager.createDirectory(layerDir)
            if (!dirCreated) {
                return Result.failure(Exception("Failed to create $layerType directory"))
            }

            val filePath = "$layerDir/$filename"

            // Save the image bytes
            val success = fileManager.writeBytes(filePath, imageBytes)

            if (success) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.INFO, "$layerType tile saved: $filePath (${imageBytes.size} bytes)")
                Result.success(filePath)
            } else {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to save $layerType tile to $filePath")
                Result.failure(Exception("Failed to write $layerType tile file"))
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Error saving $layerType tile: ${e.message}", e)
            Result.failure(e)
        }
    }

    actual suspend fun loadTileImage(layerType: String, zoom: Int, x: Int, y: Int, year: Int, month: Int, day: Int, hour: Int, minute: Int): ByteArray? {
        return try {
            val filename = "${layerType}_z${zoom}_x${x}_y${y}_${year}${month.toString().padStart(2, '0')}${day.toString().padStart(2, '0')}_${hour.toString().padStart(2, '0')}${minute.toString().padStart(2, '0')}.jpg"
            val tilePath = getTilePath(layerType, filename)

            if (tilePath != null) {
                val bytes = fileManager.readBytes(tilePath)
                if (bytes != null) {
                    Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Loaded $layerType tile: $filename (${bytes.size} bytes)")
                    bytes
                } else {
                    Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to read $layerType tile bytes: $filename")
                    null
                }
            } else {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "$layerType tile not found: $filename")
                null
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Error loading $layerType tile: ${e.message}", e)
            null
        }
    }

    private suspend fun getTilePath(layerType: String, filename: String): String? {
        return try {
            val dataDir = fileManager.getAppDataDirectory()
            val layerDir = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}/$layerType"
            val filePath = "$layerDir/$filename"

            if (fileManager.fileExists(filePath)) {
                filePath
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Error getting $layerType tile path: ${e.message}", e)
            null
        }
    }

    /**
     * Get the local file path for a file key
     */
    actual fun getLocalFilePath(fileKey: String): String {
        val dataDir = fileManager.getAppDataDirectory()
        return "$dataDir/${SkysightConstants.SKYSIGHT_DIR}/$fileKey${SkysightConstants.FILE_EXTENSION}"
    }

    /**
     * Get the local file path for a file key
     */
    actual fun getLocalFilePath(fileKey: String): String {
        // iOS implementation not used
        return ""
    }

    /**
     * Clean up old files (remove files older than 1 day)
     */
    actual suspend fun cleanupOldFiles(): Int {
        return try {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Starting cleanup of old skysight files")

            val dataDir = fileManager.getAppDataDirectory()
            val skysightDir = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}"
            val cutoffTime = System.currentTimeMillis() - (1 * 24 * 60 * 60 * 1000L) // 1 day ago

            val files = fileManager.listFiles(skysightDir)
            var deletedCount = 0

            for (fileName in files) {
                val filePath = "$skysightDir/$fileName"
                val lastModified = fileManager.getLastModified(filePath)

                if (lastModified < cutoffTime) {
                    val deleted = fileManager.delete(filePath)
                    if (deleted) {
                        deletedCount++
                        Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Cleaned up old file: $fileName")
                    }
                }
            }

            if (deletedCount > 0) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.INFO, "Cleanup: removed $deletedCount old files")
            }

            deletedCount
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to cleanup old files: ${e.message}", e)
            0
        }
    }

    /**
     * Get storage statistics
     */
    actual suspend fun getStorageStats(): Map<String, Any> {
        return try {
            val dataDir = fileManager.getAppDataDirectory()
            val skysightDir = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}"

            val files = fileManager.listFiles(skysightDir)
            val totalSize = files.sumOf { fileName ->
                fileManager.getFileSize("$skysightDir/$fileName")
            }
            val fileCount = files.size

            val oldestFile = files.minByOrNull { fileName ->
                fileManager.getLastModified("$skysightDir/$fileName")
            }
            val newestFile = files.maxByOrNull { fileName ->
                fileManager.getLastModified("$skysightDir/$fileName")
            }

            mapOf(
                "totalSize" to totalSize,
                "fileCount" to fileCount,
                "directoryPath" to skysightDir,
                "oldestFile" to (oldestFile ?: "none"),
                "newestFile" to (newestFile ?: "none")
            )
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to get storage stats: ${e.message}", e)
            emptyMap()
        }
    }
}