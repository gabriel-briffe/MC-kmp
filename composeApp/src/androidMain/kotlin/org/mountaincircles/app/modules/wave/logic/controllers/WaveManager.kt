package org.mountaincircles.app.modules.wave.logic.controllers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.maps.getMapsDirectory
import org.mountaincircles.app.modules.wave.logic.data.WaveEntry
import java.io.File

/**
 * Android implementation of WaveManager
 */
actual class WaveManager {

    private val waveLogic = WaveLogic()

    /**
     * Get wave directory (create if doesn't exist)
     */
    actual fun getWaveDirectory(): String {
        val mapsDir = getMapsDirectory() // Reuse maps directory helper
        val waveDir = File(mapsDir.parentFile, "wave")
        if (!waveDir.exists()) {
            waveDir.mkdirs()
        }
        return waveDir.absolutePath
    }

    /**
     * Scan wave directory for MBTiles files
     */
    actual suspend fun scan(): List<WaveEntry> = withContext(Dispatchers.IO) {
        val waveDir = File(getWaveDirectory())
        if (!waveDir.exists()) return@withContext emptyList()

        val entries = mutableListOf<WaveEntry>()

        try {
            val files = waveDir.listFiles { file ->
                file.isFile && (file.name.endsWith(".mbtiles") || file.name.endsWith(".tiff"))
            } ?: emptyArray()

            for (file in files) {
                val entry = parseFilename(file.name, file.absolutePath)
                if (entry != null) {
                    entries.add(entry)
                } else {
                    Logger.log("WAVE", LogLevel.WARN, "Invalid wave filename: ${file.name}")
                }
            }

            if (entries.isNotEmpty()) {
                val fileList = entries.joinToString(
                    separator = "; ",
                    limit = 10
                ) { entry -> File(entry.filePath).name }
                Logger.log("WAVE", LogLevel.DEBUG, "Wave files found: ${entries.size}: $fileList")
            } else {
                Logger.log("WAVE", LogLevel.DEBUG, "No wave files found")
            }

        } catch (e: Exception) {
            Logger.log("WAVE", LogLevel.ERROR, "Error scanning wave directory: ${e.message}", e)
        }

        return@withContext entries
    }

    /**
     * Check if a file exists
     */
    actual suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(path).exists()
        } catch (e: Exception) {
            Logger.log("WAVE", LogLevel.WARN, "Error checking file existence: ${e.message}")
            false
        }
    }

    /**
     * Delete a specific wave file
     */
    actual suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            val deleted = file.delete()
            if (deleted) {
                Logger.log("WAVE", LogLevel.DEBUG, "Deleted wave file: ${file.name}")
            } else {
                Logger.log("WAVE", LogLevel.WARN, "Failed to delete wave file: ${file.name}")
            }
            deleted
        } catch (e: Exception) {
            Logger.log("WAVE", LogLevel.ERROR, "Error deleting wave file: ${e.message}", e)
            false
        }
    }

    /**
     * Get file size in bytes
     */
    actual fun getFileSize(path: String): Long {
        return try {
            File(path).length()
        } catch (e: Exception) {
            Logger.log("WAVE_ANDROID", LogLevel.WARN, "Failed to get file size for $path: ${e.message}")
            0L
        }
    }

    /**
     * Delete all wave files
     */
    actual suspend fun clearAllFiles(): Int = withContext(Dispatchers.IO) {
        val waveDir = File(getWaveDirectory())
        if (!waveDir.exists()) return@withContext 0

        var deletedCount = 0

        try {
            val files = waveDir.listFiles { file ->
                file.isFile && (file.name.endsWith(".mbtiles") || file.name.endsWith(".tiff"))
            } ?: emptyArray()

            for (file in files) {
                if (file.delete()) {
                    deletedCount++
                    Logger.log("WAVE", LogLevel.DEBUG, "Deleted wave file: ${file.name}")
                } else {
                    Logger.log("WAVE", LogLevel.WARN, "Failed to delete wave file: ${file.name}")
                }
            }

            Logger.log("WAVE", LogLevel.INFO, "Cleared $deletedCount wave and wind files")

        } catch (e: Exception) {
            Logger.log("WAVE", LogLevel.ERROR, "Error clearing wave files: ${e.message}", e)
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
        val match = regex.matchEntire(filename)

        if (match == null) {
            Logger.log("WAVE_DEBUG", LogLevel.INFO, "Filename didn't match regex: $filename")
            return null
        }

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

            Logger.log("WAVE_DEBUG", LogLevel.INFO, "Parsed filename: $filename -> pressure=$pressure, hour=$hour, targetDate=$targetDate")

            return WaveEntry(
                forecastDate = forecastDate,
                targetDate = targetDate,
                hour = hour,
                pressure = pressure,
                filePath = filePath
            )
        } catch (e: NumberFormatException) {
            Logger.log("WAVE", LogLevel.WARN, "Failed to parse numbers in filename: $filename", e)
            return null
        }
    }
}