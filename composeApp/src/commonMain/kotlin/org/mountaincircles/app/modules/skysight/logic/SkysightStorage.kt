package org.mountaincircles.app.modules.skysight.logic

import org.mountaincircles.app.io.FileManager
import org.mountaincircles.app.network.DownloadManager
import org.mountaincircles.app.network.DownloadRequest
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Skysight storage for forecast data downloads
 * Follows the same pattern as AirportsStorage
 */
expect class SkysightStorage(
    fileManager: FileManager,
    downloadManager: DownloadManager
) {
    suspend fun ensureSkysightDirectory(): Result<Unit>
    suspend fun fileExists(fileKey: String): Boolean
    suspend fun downloadFile(fileKey: String, url: String): Result<Unit>
    suspend fun saveRadarImage(imageBytes: ByteArray, timestamp: String): Result<String>
    suspend fun saveSatelliteImage(imageBytes: ByteArray, filename: String): Result<String>
    suspend fun getSatelliteTilePath(filename: String): String?
    suspend fun loadSatelliteTile(filename: String): ByteArray?
    suspend fun saveTileImage(layerType: String, imageBytes: ByteArray, zoom: Int, x: Int, y: Int, year: Int, month: Int, day: Int, hour: Int, minute: Int): Result<String>
    suspend fun loadTileImage(layerType: String, zoom: Int, x: Int, y: Int, year: Int, month: Int, day: Int, hour: Int, minute: Int): ByteArray?
    suspend fun cleanupOldFiles(): Int
    suspend fun cleanupOldRealtimeTiles(): Int
    suspend fun clearAllFiles(): Int
    suspend fun getStorageStats(): Map<String, Any>
    fun getLocalFilePath(fileKey: String): String
}