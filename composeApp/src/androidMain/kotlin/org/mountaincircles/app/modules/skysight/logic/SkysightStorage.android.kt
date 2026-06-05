package org.mountaincircles.app.modules.skysight.logic

import org.mountaincircles.app.io.FileManager
import org.mountaincircles.app.network.DownloadManager
import org.mountaincircles.app.network.DownloadRequest
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.minus

/**
 * Android implementation of SkysightStorage with GZIP decompression
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
     * Download a forecast file from URL and decompress if needed
     */
    actual suspend fun downloadFile(fileKey: String, url: String): Result<Unit> {
        return try {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.INFO, "Starting download: $fileKey from $url")

            val dataDir = fileManager.getAppDataDirectory()
            val skysightDir = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}"
            val finalFilePath = "$skysightDir/$fileKey${SkysightConstants.FILE_EXTENSION}"

            // Ensure directory exists
            ensureSkysightDirectory().getOrThrow()

            // Download data
            val downloadedBytes = downloadData(url).getOrThrow()

            // Process data (decompress if GZIP, otherwise use as-is)
            val processedBytes = decompressData(downloadedBytes).getOrThrow()

            // Save processed data to final file
            val success = fileManager.writeBytes(finalFilePath, processedBytes)

            if (success) {
                val compressionInfo = if (processedBytes.size != downloadedBytes.size) {
                    " (${processedBytes.size} bytes processed from ${downloadedBytes.size} downloaded)"
                } else {
                    " (${processedBytes.size} bytes)"
                }
                Logger.log("SKYSIGHT_STORAGE", LogLevel.INFO, "Download and processing completed: $fileKey$compressionInfo")
                Result.success(Unit)
            } else {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to save processed file: $finalFilePath")
                Result.failure(Exception("Failed to save processed file"))
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Download/decompression failed for $fileKey: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Download data from URL
     */
    private suspend fun downloadData(url: String): Result<ByteArray> {
        return try {
            // Create a temporary file path for download
            val tempFilePath = "${fileManager.getCacheDirectory()}/skysight_temp_${System.currentTimeMillis()}"

            val request = DownloadRequest(
                url = url,
                filePath = tempFilePath
            )

            val result = downloadManager.download(request)

            if (result.isSuccess) {
                // Read the downloaded compressed data
                val compressedBytes = fileManager.readBytes(tempFilePath)
                if (compressedBytes != null) {
                    // Clean up temp file
                    fileManager.delete(tempFilePath)
                    Result.success(compressedBytes)
                } else {
                    fileManager.delete(tempFilePath)
                    Result.failure(Exception("Failed to read downloaded compressed data"))
                }
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Download failed"))
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to download compressed data: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Decompress data if it's GZIP compressed, otherwise return as-is
     */
    private suspend fun decompressData(data: ByteArray): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Processing ${data.size} bytes of data")

                // Check first few bytes for debugging
                val firstBytes = if (data.size >= 4) {
                    data.take(4).joinToString(" ") { "0x${it.toUByte().toString(16).padStart(2, '0')}" }
                } else {
                    data.map { "0x${it.toUByte().toString(16).padStart(2, '0')}" }.joinToString(" ")
                }
                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "First bytes: [$firstBytes]")

                // Check if data is GZIP compressed by looking for GZIP magic bytes (0x1f 0x8b)
                val isGzipCompressed = data.size >= 2 && data[0].toInt() == 0x1f && data[1].toInt() == 0x8b

                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "GZIP signature check: ${if (isGzipCompressed) "FOUND (0x1f 0x8b)" else "NOT FOUND"}")

                if (isGzipCompressed) {
                    Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Data is GZIP compressed, decompressing...")

                    val outputStream = ByteArrayOutputStream()
                    GZIPInputStream(ByteArrayInputStream(data)).use { gzipStream ->
                        gzipStream.copyTo(outputStream)
                    }

                    val decompressedBytes = outputStream.toByteArray()
                    val compressionRatio = if (data.size > 0) String.format("%.2f", decompressedBytes.size.toFloat() / data.size) else "N/A"
                    Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "GZIP decompression completed: ${data.size} bytes -> ${decompressedBytes.size} bytes (ratio: ${compressionRatio})")

                    Result.success(decompressedBytes)
                } else {
                    Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Data is not GZIP compressed, using as-is")
                    Result.success(data)
                }
            } catch (e: Exception) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Data processing failed: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Clear all SkySight files
     */
    actual suspend fun clearAllFiles(): Int {
        return try {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.INFO, "Clearing all Skysight files...")

            val dataDir = fileManager.getAppDataDirectory()
            val skysightDir = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}"

            var deletedCount = 0

            // Clear satellite directory
            try {
                val satelliteDir = "$skysightDir/satellite"
                val satelliteFiles = fileManager.listFiles(satelliteDir)
                for (fileName in satelliteFiles) {
                    val filePath = "$satelliteDir/$fileName"
                    val deleted = fileManager.delete(filePath)
                    if (deleted) {
                        deletedCount++
                        Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Deleted satellite file: $fileName")
                    } else {
                        Logger.log("SKYSIGHT_STORAGE", LogLevel.WARN, "Failed to delete satellite file: $fileName")
                    }
                }
            } catch (e: Exception) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Satellite directory doesn't exist or error clearing: ${e.message}")
            }

            // Clear rain directory
            try {
                val rainDir = "$skysightDir/rain"
                val rainFiles = fileManager.listFiles(rainDir)
                for (fileName in rainFiles) {
                    val filePath = "$rainDir/$fileName"
                    val deleted = fileManager.delete(filePath)
                    if (deleted) {
                        deletedCount++
                        Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Deleted rain file: $fileName")
                    } else {
                        Logger.log("SKYSIGHT_STORAGE", LogLevel.WARN, "Failed to delete rain file: $fileName")
                    }
                }
            } catch (e: Exception) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Rain directory doesn't exist or error clearing: ${e.message}")
            }

            // Clear forecast files (everything else in main directory)
            val files = fileManager.listFiles(skysightDir)
            val forecastFiles = files.filterNot { fileName ->
                fileName == "satellite" || fileName == "rain"
            }

            for (fileName in forecastFiles) {
                val filePath = "$skysightDir/$fileName"
                val deleted = fileManager.delete(filePath)
                if (deleted) {
                    deletedCount++
                    Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Deleted forecast file: $fileName")
                } else {
                    Logger.log("SKYSIGHT_STORAGE", LogLevel.WARN, "Failed to delete forecast file: $fileName")
                }
            }

            Logger.log("SKYSIGHT_STORAGE", LogLevel.INFO, "Cleared $deletedCount Skysight files")
            deletedCount
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to clear files: ${e.message}", e)
            0
        }
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
     * Clean up old realtime tiles (satellite and rain) based on date in filename
     * Deletes tiles from yesterday UTC or older
     */
    actual suspend fun cleanupOldRealtimeTiles(): Int {
        return try {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.INFO, "Starting cleanup of old realtime tiles")

            val dataDir = fileManager.getAppDataDirectory()
            val skysightDir = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}"

            // Get yesterday's date in UTC
            val yesterday = Clock.System.now()
                .toLocalDateTime(TimeZone.UTC)
                .date
                .minus(1, DateTimeUnit.DAY)

            val yesterdayStr = yesterday.toString().replace("-", "") // Format: YYYYMMDD

            Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Cleaning up tiles older than: $yesterdayStr")

            var totalDeleted = 0

            // Clean satellite tiles
            val satelliteDir = "$skysightDir/satellite"
            try {
                val satelliteFiles = fileManager.listFiles(satelliteDir)
                val satelliteDeleted = cleanupRealtimeTilesInDirectory(satelliteDir, satelliteFiles, yesterdayStr, "satellite")
                totalDeleted += satelliteDeleted
            } catch (e: Exception) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Satellite directory doesn't exist or is not accessible: $satelliteDir")
            }

            // Clean rain tiles
            val rainDir = "$skysightDir/rain"
            try {
                val rainFiles = fileManager.listFiles(rainDir)
                val rainDeleted = cleanupRealtimeTilesInDirectory(rainDir, rainFiles, yesterdayStr, "rain")
                totalDeleted += rainDeleted
            } catch (e: Exception) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Rain directory doesn't exist or is not accessible: $rainDir")
            }

            if (totalDeleted > 0) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.INFO, "Cleanup realtime tiles: removed $totalDeleted old files")
            } else {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Cleanup realtime tiles: no old files found")
            }

            totalDeleted
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Failed to cleanup old realtime tiles: ${e.message}", e)
            0
        }
    }

    /**
     * Helper method to clean up realtime tiles in a specific directory
     */
    private suspend fun cleanupRealtimeTilesInDirectory(directory: String, files: List<String>, yesterdayStr: String, layerType: String): Int {
        var deletedCount = 0

        for (fileName in files) {
            try {
                // Parse date from filename format: {layerType}_z{Z}_x{X}_y{Y}_{YYYYMMDD}_{HHMM}.png
                val parts = fileName.split("_")
                if (parts.size >= 6) {
                    val datePart = parts[4] // YYYYMMDD

                    // Only delete if the date is yesterday or older
                    if (datePart <= yesterdayStr) {
                        val filePath = "$directory/$fileName"
                        val deleted = fileManager.delete(filePath)
                        if (deleted) {
                            deletedCount++
                            Logger.log("SKYSIGHT_STORAGE", LogLevel.DEBUG, "Cleaned up old $layerType tile: $fileName (date: $datePart)")
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.log("SKYSIGHT_STORAGE", LogLevel.WARN, "Failed to parse filename during cleanup: $fileName", e)
            }
        }

        return deletedCount
    }

    /**
     * Get storage statistics
     */
    /**
     * Get the local file path for a file key
     */
    actual fun getLocalFilePath(fileKey: String): String {
        val dataDir = fileManager.getAppDataDirectory()
        return "$dataDir/${SkysightConstants.SKYSIGHT_DIR}/$fileKey${SkysightConstants.FILE_EXTENSION}"
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

            if (fileManager.exists(filePath)) {
                filePath
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_STORAGE", LogLevel.ERROR, "Error getting $layerType tile path: ${e.message}", e)
            null
        }
    }

    actual suspend fun getStorageStats(): Map<String, Any> {
        return try {
            val dataDir = fileManager.getAppDataDirectory()
            val skysightDir = "$dataDir/${SkysightConstants.SKYSIGHT_DIR}"

            // Calculate satellite stats
            var satelliteSize = 0L
            var satelliteCount = 0
            try {
                val satelliteDir = "$skysightDir/satellite"
                val satelliteFiles = fileManager.listFiles(satelliteDir)
                satelliteSize = satelliteFiles.sumOf { fileName ->
                    fileManager.getFileSize("$satelliteDir/$fileName")
                }
                satelliteCount = satelliteFiles.size
            } catch (e: Exception) {
                // Directory doesn't exist or can't be accessed
            }

            // Calculate rain stats
            var rainSize = 0L
            var rainCount = 0
            try {
                val rainDir = "$skysightDir/rain"
                val rainFiles = fileManager.listFiles(rainDir)
                rainSize = rainFiles.sumOf { fileName ->
                    fileManager.getFileSize("$rainDir/$fileName")
                }
                rainCount = rainFiles.size
            } catch (e: Exception) {
                // Directory doesn't exist or can't be accessed
            }

            // Calculate forecast stats (everything else in main directory)
            val files = fileManager.listFiles(skysightDir)
            val forecastFiles = files.filterNot { fileName ->
                fileName == "satellite" || fileName == "rain"
            }
            val forecastSize = forecastFiles.sumOf { fileName ->
                fileManager.getFileSize("$skysightDir/$fileName")
            }
            val forecastCount = forecastFiles.size

            val oldestFile = forecastFiles.minByOrNull { fileName ->
                fileManager.getLastModified("$skysightDir/$fileName")
            }
            val newestFile = forecastFiles.maxByOrNull { fileName ->
                fileManager.getLastModified("$skysightDir/$fileName")
            }

            mapOf(
                "satelliteSize" to satelliteSize,
                "satelliteCount" to satelliteCount,
                "rainSize" to rainSize,
                "rainCount" to rainCount,
                "forecastSize" to forecastSize,
                "forecastCount" to forecastCount,
                "totalSize" to (satelliteSize + rainSize + forecastSize),
                "totalCount" to (satelliteCount + rainCount + forecastCount),
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