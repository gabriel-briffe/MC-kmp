package org.mountaincircles.app.modules.wave.import.logic

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.*
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.wave.logic.controllers.WaveManager
import org.mountaincircles.app.modules.wave.logic.data.WaveProgress
import org.mountaincircles.app.network.ProgressData
import org.mountaincircles.app.network.DownloadManager
import org.mountaincircles.app.network.DownloadRequest
import org.mountaincircles.app.network.createDownloadManager
import org.mountaincircles.app.modules.wave.ui.WaveConstants

/**
 * Wave forecast import types
 */
enum class WaveImportType {
    TODAY,
    TOMORROW,
    YESTERDAY_FOR_TODAY
}

/**
 * Common wave import logic
 */
class WaveImporter {

    // Unified download manager for all download operations
    private val downloadManager: DownloadManager = createDownloadManager()

    companion object {
        private val isobareList = WaveConstants.ISOBARE_LIST
        private val hourList = WaveConstants.HOUR_LIST
        private const val directDownloadBase = "https://github.com/gabriel-briffe/arome/releases/download/"
        private const val proxyDownloadBase = "https://edl-proxy.gabriel-briffe.workers.dev/?url=https://github.com/gabriel-briffe/arome/releases/download/"
    }
    
    /**
     * Download item with fallback URLs
     */
    data class DownloadItem(
        val directUrl: String,
        val proxyUrl: String,
        val fileName: String,
        val label: String,
        val forecastDateStr: String,
        val targetDateStr: String,
        val hour: Int,
        val pressure: Int
    ) {
        val primaryUrl: String get() = directUrl
        val fallbackUrl: String get() = proxyUrl
    }
    
    /**
     * Import wave forecasts with parallel downloads (3x faster)
     */
    suspend fun import(
        importType: WaveImportType,
        waveManager: WaveManager,
        onProgress: (WaveProgress) -> Unit,
        maxConcurrentDownloads: Int = 3,
        includeWindFiles: Boolean = false,
        selectedWindRegions: Set<org.mountaincircles.app.modules.wave.ui.WindRegion> = emptySet()
    ) {
        Logger.log("WAVE_IMPORT", LogLevel.INFO, "Wave parallel import start type=$importType maxConcurrent=$maxConcurrentDownloads includeWind=$includeWindFiles")
        
        val items = buildDownloadList(importType, includeWindFiles, selectedWindRegions)
        Logger.log("WAVE_IMPORT", LogLevel.INFO, "Wave parallel import planning ${items.size} files")
        
        val total = items.size
        var processed = 0
        var successCount = 0
        var failedCount = 0
        val progressMutex = Mutex()
        
        onProgress(WaveProgress(0, total, "Initializing parallel downloads...", 0, "", 0, 0))
        
        // Process items in parallel batches
        items.chunked(maxConcurrentDownloads).forEach { batch ->
            coroutineScope {
                batch.map { item ->
                    async {
                        try {
                            val filePath = "${waveManager.getWaveDirectory()}/${item.fileName}"
                            
                            // Check if file already exists
                            if (waveManager.fileExists(filePath)) {
                                Logger.log("WAVE_IMPORT", LogLevel.DEBUG, "Wave parallel import skip exists file=${item.fileName}")
                                progressMutex.withLock {
                                    processed++
                                    successCount++
                                    onProgress(WaveProgress(processed, total, "Already present: ${item.label}", 100, item.label, successCount, failedCount))
                                }
                                return@async true
                            }
                            
                            progressMutex.withLock {
                                onProgress(WaveProgress(processed, total, "Downloading ${item.label}...", 0, item.label, successCount, failedCount))
                            }
                            Logger.log("WAVE_IMPORT", LogLevel.DEBUG, "Wave parallel import GET url=${item.primaryUrl} -> file=${item.fileName}")

                            // Try direct download first, fallback to proxy if needed
                            val downloadSuccess = tryDownloadWithFallback(item, filePath) { progressData ->
                                val pct = progressData.percentage.toInt().coerceIn(0, 100)
                                // Don't update global progress here to avoid flooding, just log
                                if (pct % 25 == 0 || pct == 100) { // Only update every 25% or at completion
                                    Logger.log("WAVE_IMPORT", LogLevel.DEBUG, "Download progress ${item.fileName}: ${progressData.getFormattedProgress()}")
                                }
                            }

                            if (!downloadSuccess) {
                                Logger.log("WAVE_IMPORT", LogLevel.WARN, "Download failed for ${item.fileName}, skipping file")
                                progressMutex.withLock {
                                    failedCount++
                                    processed++
                                    onProgress(WaveProgress(processed, total, "Skipped: ${item.label}", 0, item.label, successCount, failedCount))
                                }
                                return@async false
                            }
                            
                            Logger.log("WAVE_IMPORT", LogLevel.DEBUG, "Wave parallel import done file=${item.fileName}")
                            progressMutex.withLock {
                                processed++
                                successCount++
                                onProgress(WaveProgress(processed, total, "Imported ${item.label}", 100, item.label, successCount, failedCount))
                            }
                            true
                            
                        } catch (e: Exception) {
                            progressMutex.withLock {
                                failedCount++
                                processed++
                                Logger.log("WAVE_IMPORT", LogLevel.ERROR, "Wave parallel import fail label=${item.label} urls=[${item.primaryUrl}, ${item.fallbackUrl}]: ${e.message}", e)
                                onProgress(WaveProgress(processed, total, "Failed: ${item.label}", 0, item.label, successCount, failedCount))
                            }
                            false
                        }
                    }
                }.awaitAll() // Wait for all downloads in this batch to complete
            }
        }
        
        Logger.log("WAVE_IMPORT", LogLevel.INFO, "Wave parallel import end ok=$successCount total=$total failed=$failedCount")
    }
    
    /**
     * Build download list for import type
     */
    private fun buildDownloadList(importType: WaveImportType, includeWindFiles: Boolean = false, selectedWindRegions: Set<org.mountaincircles.app.modules.wave.ui.WindRegion> = emptySet()): List<DownloadItem> {
        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.UTC).date
        
        val items = when (importType) {
            WaveImportType.TODAY -> {
                buildDownloadItemsForForecast(today, listOf(today), includeWindFiles, selectedWindRegions)
            }
            WaveImportType.TOMORROW -> {
                val tomorrow = today.plus(1, DateTimeUnit.DAY)
                buildDownloadItemsForForecast(today, listOf(tomorrow), includeWindFiles, selectedWindRegions)
            }
            WaveImportType.YESTERDAY_FOR_TODAY -> {
                val yesterday = today.minus(1, DateTimeUnit.DAY)
                buildDownloadItemsForForecast(yesterday, listOf(today), includeWindFiles, selectedWindRegions)
            }
        }
        
        return items
    }
    
    /**
     * Build download items for a specific forecast date and target dates
     */
    private fun buildDownloadItemsForForecast(
        forecastDate: LocalDate,
        targetDates: List<LocalDate>,
        includeWindFiles: Boolean = false,
        selectedWindRegions: Set<org.mountaincircles.app.modules.wave.ui.WindRegion> = emptySet()
    ): List<DownloadItem> {
        val items = mutableListOf<DownloadItem>()
        val forecastDateStr = forecastDate.toString() // yyyy-MM-dd format

        targetDates.forEach { target ->
            val targetDateStr = target.toString() // yyyy-MM-dd format
            hourList.forEach { hour ->
                isobareList.forEach { pressure ->
                    val releaseTag = "arome-$forecastDateStr"
                    val hourPadded = hour.toString().padStart(2, '0')

                    // Original wave file (vv component)
                    val vvFilename = "arome_vv_${forecastDateStr}_${targetDateStr}_${hourPadded}_${pressure}.mbtiles"
                    val vvDirectUrl = "$directDownloadBase$releaseTag/$vvFilename"
                    val vvProxyUrl = "$proxyDownloadBase$releaseTag/$vvFilename"
                    val vvLabel = "$targetDateStr ${hour}:00 - ${pressure}hPa (VV)"

                    items.add(DownloadItem(vvDirectUrl, vvProxyUrl, vvFilename, vvLabel, forecastDateStr, targetDateStr, hour, pressure))

                    // U and V component files (only if wind files are requested and regions are selected)
                    if (includeWindFiles && selectedWindRegions.isNotEmpty()) {
                        // Define region name mappings
                        val regionNames = mapOf(
                            org.mountaincircles.app.modules.wave.ui.WindRegion.NORTH to "North",
                            org.mountaincircles.app.modules.wave.ui.WindRegion.SOUTH to "South",
                            org.mountaincircles.app.modules.wave.ui.WindRegion.MIDDLE_EAST to "MiddleEast",
                            org.mountaincircles.app.modules.wave.ui.WindRegion.MIDDLE_WEST to "MiddleWest"
                        )

                        // Add U and V files for each selected region
                        selectedWindRegions.forEach { region ->
                            val regionName = regionNames[region] ?: return@forEach

                            // U component file
                            val uFilename = "arome_u_${regionName}_${forecastDateStr}_${targetDateStr}_${hourPadded}_${pressure}.tiff"
                            val uDirectUrl = "$directDownloadBase$releaseTag/$uFilename"
                            val uProxyUrl = "$proxyDownloadBase$releaseTag/$uFilename"
                            val uLabel = "$targetDateStr ${hour}:00 - ${pressure}hPa (U-${regionName})"

                            items.add(DownloadItem(uDirectUrl, uProxyUrl, uFilename, uLabel, forecastDateStr, targetDateStr, hour, pressure))

                            // V component file
                            val vFilename = "arome_v_${regionName}_${forecastDateStr}_${targetDateStr}_${hourPadded}_${pressure}.tiff"
                            val vDirectUrl = "$directDownloadBase$releaseTag/$vFilename"
                            val vProxyUrl = "$proxyDownloadBase$releaseTag/$vFilename"
                            val vLabel = "$targetDateStr ${hour}:00 - ${pressure}hPa (V-${regionName})"

                            items.add(DownloadItem(vDirectUrl, vProxyUrl, vFilename, vLabel, forecastDateStr, targetDateStr, hour, pressure))
                        }
                    }
                }
            }
        }

        return items
    }

    /**
     * Try to download with fallback from direct URL to proxy URL
     */
    private suspend fun tryDownloadWithFallback(
        item: DownloadItem,
        filePath: String,
        onProgress: (ProgressData) -> Unit
    ): Boolean {
        // Create download request - wave module handles proxy URL construction
        val downloadRequest = DownloadRequest(
            url = item.proxyUrl,  // Use proxy URL as primary for wave forecasts
            filePath = filePath
        )

        return try {
            Logger.log("WAVE_IMPORT", LogLevel.DEBUG, "Starting wave download via proxy: ${item.proxyUrl}")

            val result = downloadManager.download(downloadRequest, onProgress)

            if (result.isSuccess) {
                Logger.log("WAVE_IMPORT", LogLevel.INFO, "✅ Wave download successful: ${item.fileName}")
                true
            } else {
                Logger.log("WAVE_IMPORT", LogLevel.ERROR, "❌ Wave download failed: ${item.fileName}")
                Logger.log("WAVE_IMPORT", LogLevel.ERROR, "   Error: ${result.exceptionOrNull()?.message}")
                Logger.log("WAVE_IMPORT", LogLevel.ERROR, "   URL: ${item.proxyUrl}")
                false
            }
        } catch (e: Exception) {
            Logger.log("WAVE_IMPORT", LogLevel.ERROR, "💥 Wave download exception: ${item.fileName}")
            Logger.log("WAVE_IMPORT", LogLevel.ERROR, "   Exception: ${e.message}")
            Logger.log("WAVE_IMPORT", LogLevel.ERROR, "   URL: ${item.proxyUrl}")
            false
        }
    }
}





