package org.mountaincircles.app.modules.wave.logic.controllers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.wave.logic.data.WaveEntry
import platform.Foundation.*
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * iOS implementation of WaveManager using Foundation APIs
 */
@OptIn(ExperimentalForeignApi::class)
actual class WaveManager {

    private val waveLogic = WaveLogic()

    /**
     * Get wave directory (create if doesn't exist)
     * Uses iOS documents directory as base path
     */
    actual fun getWaveDirectory(): String {
        val fileManager = NSFileManager.defaultManager()

        // Get documents directory path
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val documentsDir = paths.firstOrNull() as? String ?: run {
            Logger.log("WAVE_IOS", LogLevel.ERROR, "Failed to get documents directory")
            return "/tmp/wave" // Fallback
        }

        val waveDir = "$documentsDir/wave"

        // Create directory if it doesn't exist
        if (!fileManager.fileExistsAtPath(waveDir)) {
            try {
                fileManager.createDirectoryAtPath(waveDir, withIntermediateDirectories = true, attributes = null, error = null)
                Logger.log("WAVE_IOS", LogLevel.DEBUG, "Created wave directory: $waveDir")
            } catch (e: Exception) {
                Logger.log("WAVE_IOS", LogLevel.ERROR, "Failed to create wave directory: ${e.message}")
            }
        } else {
            Logger.log("WAVE_IOS", LogLevel.DEBUG, "Wave directory exists: $waveDir")
        }

        return waveDir
    }

    /**
     * Scan wave directory for MBTiles files and parse filenames
     */
    actual suspend fun scan(): List<WaveEntry> = withContext(Dispatchers.Default) {
        val waveDir = getWaveDirectory()
        val fileManager = NSFileManager.defaultManager()
        val entries = mutableListOf<WaveEntry>()

        try {
            // Get directory contents
            val contents = fileManager.contentsOfDirectoryAtPath(waveDir, error = null)

            contents?.forEach { fileName ->
                val filename = fileName as? String
                if (filename != null && (filename.endsWith(".mbtiles") || filename.endsWith(".tiff"))) {
                    val filePath = "$waveDir/$filename"

                    val entry = parseFilename(filename, filePath)
                    if (entry != null) {
                        entries.add(entry)
                        Logger.log("WAVE_IOS", LogLevel.DEBUG, "Found wave file: $filename")
                    } else {
                        Logger.log("WAVE_IOS", LogLevel.WARN, "Invalid wave filename: $filename")
                    }
                }
            }

            if (entries.isNotEmpty()) {
                val fileList = entries.joinToString(
                    separator = "; ",
                    limit = 10
                ) { entry -> entry.filePath.substringAfterLast('/') }
                Logger.log("WAVE_IOS", LogLevel.INFO, "Wave scan complete: ${entries.size} files found: $fileList")
            } else {
                Logger.log("WAVE_IOS", LogLevel.DEBUG, "No wave files found in directory: $waveDir")
            }

        } catch (e: Exception) {
            Logger.log("WAVE_IOS", LogLevel.ERROR, "Error scanning wave directory: ${e.message}", e)
        }

        return@withContext entries
    }

    /**
     * Check if a file exists
     */
    actual suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val fileManager = NSFileManager.defaultManager()
            val exists = fileManager.fileExistsAtPath(path)

            Logger.log("WAVE_IOS", LogLevel.DEBUG, "File existence check: $path -> $exists")
            return@withContext exists
        } catch (e: Exception) {
            Logger.log("WAVE_IOS", LogLevel.ERROR, "Error checking file existence for $path: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Delete a specific wave file
     */
    actual suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val fileManager = NSFileManager.defaultManager()
            val success = fileManager.removeItemAtPath(path, error = null)

            if (success) {
                Logger.log("WAVE_IOS", LogLevel.DEBUG, "Successfully deleted wave file: ${path.substringAfterLast('/')}")
            } else {
                Logger.log("WAVE_IOS", LogLevel.WARN, "Failed to delete wave file: ${path.substringAfterLast('/')}")
            }

            return@withContext success
        } catch (e: Exception) {
            Logger.log("WAVE_IOS", LogLevel.ERROR, "Error deleting wave file $path: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Get file size in bytes (Android only implementation)
     */
    actual fun getFileSize(path: String): Long {
        // TODO: Implement iOS file size using NSFileManager
        Logger.log("WAVE_IOS", LogLevel.WARN, "getFileSize not implemented for iOS")
        return 0L
    }

    /**
     * Delete all wave files
     */
    actual suspend fun clearAllFiles(): Int = withContext(Dispatchers.Default) {
        val waveDir = getWaveDirectory()
        val fileManager = NSFileManager.defaultManager()
        var deletedCount = 0

        try {
            // Get directory contents
            val contents = fileManager.contentsOfDirectoryAtPath(waveDir, error = null)

            contents?.forEach { fileName ->
                val filename = fileName as? String
                if (filename != null && (filename.endsWith(".mbtiles") || filename.endsWith(".tiff"))) {
                    val filePath = "$waveDir/$filename"

                    try {
                        if (fileManager.removeItemAtPath(filePath, error = null)) {
                            deletedCount++
                            Logger.log("WAVE_IOS", LogLevel.DEBUG, "Cleared wave/wind file: $filename")
                        } else {
                            Logger.log("WAVE_IOS", LogLevel.WARN, "Failed to delete wave file: $filename")
                        }
                    } catch (e: Exception) {
                        Logger.log("WAVE_IOS", LogLevel.ERROR, "Error deleting file $filename: ${e.message}")
                    }
                }
            }

            Logger.log("WAVE_IOS", LogLevel.INFO, "Wave clearAllFiles completed: $deletedCount wave and wind files deleted")

        } catch (e: Exception) {
            Logger.log("WAVE_IOS", LogLevel.ERROR, "Error clearing wave files: ${e.message}", e)
        }

        return@withContext deletedCount
    }

    /**
     * Parse wave filename to extract metadata
     * Format: arome_vv_YYYY-MM-DD_YYYY-MM-DD_HH_PPP.mbtiles
     */
    private fun parseFilename(filename: String, filePath: String): WaveEntry? {
        // Handle both MBTiles (VV) and TIFF (U/V) files, with optional region names for wind files
        val regex = Regex("^arome_(vv|u|v)(_[A-Za-z]+)?_(\\d{4}-\\d{2}-\\d{2})_(\\d{4}-\\d{2}-\\d{2})_(\\d{2})_(\\d{3,4})\\.(mbtiles|tiff)$")
        val match = regex.matchEntire(filename) ?: return null

        try {
            // groupValues[1] = component type (vv/u/v)
            // groupValues[2] = optional region name (may be empty)
            // groupValues[3] = forecast date
            // groupValues[4] = target date
            // groupValues[5] = hour
            // groupValues[6] = pressure
            val forecastDate = match.groupValues[3]
            val targetDate = match.groupValues[4]
            val hour = match.groupValues[5].toInt()
            val pressure = match.groupValues[6].toInt()

            return WaveEntry(
                forecastDate = forecastDate,
                targetDate = targetDate,
                hour = hour,
                pressure = pressure,
                filePath = filePath
            )
        } catch (e: NumberFormatException) {
            Logger.log("WAVE_IOS", LogLevel.WARN, "Failed to parse numbers in filename: $filename", e)
            return null
        }
    }
}
