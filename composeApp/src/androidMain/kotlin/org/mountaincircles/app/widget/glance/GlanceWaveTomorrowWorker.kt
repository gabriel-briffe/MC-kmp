package org.mountaincircles.app.widget.glance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.glance.appwidget.updateAll
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.network.DownloadRequest
import org.mountaincircles.app.network.createDownloadManager
import org.mountaincircles.app.ui.TileCoordinate
import org.mountaincircles.app.ui.WidgetTileMetadata
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "GlanceWidget_WaveTomorrowWorker"

/**
 * WorkManager worker for Wave Tomorrow Glance widget updates.
 * 
 * Completely decoupled - only updates TOMORROW's wave forecast.
 */
class GlanceWaveTomorrowWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Logger.log(TAG, LogLevel.INFO, "Starting Glance wave tomorrow update...")

            val metadata = getWidgetTileMetadata(applicationContext)
            if (metadata == null) {
                Logger.log(TAG, LogLevel.WARN, "No widget tile metadata found")
                return Result.success()
            }

            Logger.log(TAG, LogLevel.INFO, "Found ${metadata.tiles.size} tiles")

            val isForceRefresh = inputData.getBoolean("force_refresh", false)
            if (!isForceRefresh && !shouldPerformUpdate(applicationContext)) {
                Logger.log(TAG, LogLevel.INFO, "Files current, skipping update")
                return Result.success()
            }

            performTomorrowWaveForecastUpdate(metadata)

        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Wave forecast worker failed: ${e.message}", e)
            Result.failure()
        }
    }

    private suspend fun performTomorrowWaveForecastUpdate(metadata: WidgetTileMetadata): Result {
        Logger.log(TAG, LogLevel.INFO, "Processing TOMORROW wave forecast...")

        val terrainBitmap = loadTerrainBackground(applicationContext)
        if (terrainBitmap == null) {
            Logger.log(TAG, LogLevel.WARN, "Missing terrain data")
            return Result.success()
        }

        // Download and process TOMORROW's wave forecast
        val tomorrowResult = downloadAndExtractWaveForecast(metadata, isTomorrow = true)
        if (tomorrowResult.success) {
            val compositedBitmap = compositeWaveOverTerrain(terrainBitmap, tomorrowResult.bitmap)
            saveCompositedImage(applicationContext, compositedBitmap, "widget_tomorrow_overlay.png")
            markWorkDoneToday(applicationContext)
            Logger.log(TAG, LogLevel.INFO, "TOMORROW wave forecast updated successfully")
        } else {
            // Delete old overlay so widget shows terrain-only with "no wave data yet"
            deleteOverlayImage(applicationContext, "widget_tomorrow_overlay.png")
            Logger.log(TAG, LogLevel.WARN, "TOMORROW download failed - removed old overlay")
        }

        // Mark refresh as complete (this also saves timestamp)
        WaveTomorrowRepo.markRefreshComplete(applicationContext)

        // Reload image data and update the Glance widget
        WaveTomorrowRepo.loadData(applicationContext)
        WaveTomorrowGlanceWidget().updateAll(applicationContext)
        Logger.log(TAG, LogLevel.DEBUG, "Glance widget updated")

        return Result.success()
    }

    private fun getWidgetTileMetadata(context: Context): WidgetTileMetadata? {
        return try {
            val prefs = context.getSharedPreferences("widget_tile_prefs", Context.MODE_PRIVATE)
            val jsonString = prefs.getString("widget_tile_metadata", null) ?: return null
            Json.decodeFromString<WidgetTileMetadata>(jsonString)
        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Failed to load tile metadata: ${e.message}", e)
            null
        }
    }

    private fun shouldPerformUpdate(context: Context): Boolean {
        val newDay = !hasWorkBeenDoneToday(context)
        val hasFile = File(context.filesDir, "widget_images/widget_tomorrow_overlay.png").exists()
        return newDay || !hasFile
    }

    private fun hasWorkBeenDoneToday(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences("glance_wave_tomorrow_prefs", Context.MODE_PRIVATE)
            val lastCompletionTime = prefs.getLong("work_completion_timestamp", 0)
            if (lastCompletionTime == 0L) return false

            val today = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            today.set(java.util.Calendar.HOUR_OF_DAY, 0)
            today.set(java.util.Calendar.MINUTE, 0)
            today.set(java.util.Calendar.SECOND, 0)
            today.set(java.util.Calendar.MILLISECOND, 0)

            val lastCompletion = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            lastCompletion.timeInMillis = lastCompletionTime
            lastCompletion.set(java.util.Calendar.HOUR_OF_DAY, 0)
            lastCompletion.set(java.util.Calendar.MINUTE, 0)
            lastCompletion.set(java.util.Calendar.SECOND, 0)
            lastCompletion.set(java.util.Calendar.MILLISECOND, 0)

            today.timeInMillis == lastCompletion.timeInMillis
        } catch (e: Exception) {
            false
        }
    }

    private fun markWorkDoneToday(context: Context) {
        val prefs = context.getSharedPreferences("glance_wave_tomorrow_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("work_completion_timestamp", System.currentTimeMillis()).apply()
    }

    private data class WaveDownloadResult(
        val bitmap: Bitmap,
        val success: Boolean
    )

    private suspend fun downloadAndExtractWaveForecast(metadata: WidgetTileMetadata, isTomorrow: Boolean): WaveDownloadResult {
        return withContext(Dispatchers.IO) {
            try {
                val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
                val forecastDate = if (isTomorrow) today.plus(1, DateTimeUnit.DAY) else today

                val forecastDateStr = today.toString()
                val targetDateStr = forecastDate.toString()
                val hour = 12
                val pressure = 600

                val releaseTag = "arome-$forecastDateStr"
                val hourPadded = hour.toString().padStart(2, '0')
                val filename = "arome_vv_${forecastDateStr}_${targetDateStr}_${hourPadded}_${pressure}.mbtiles"

                val directUrl = "https://github.com/gabriel-briffe/arome/releases/download/$releaseTag/$filename"
                val proxyUrl = "https://edl-proxy.gabriel-briffe.workers.dev/?url=$directUrl"

                Logger.log(TAG, LogLevel.INFO, "Wave forecast URL: $directUrl")

                val cacheDir = File(applicationContext.cacheDir, "wave_forecast")
                cacheDir.mkdirs()

                val waveFile = File(cacheDir, filename)
                val downloadManager = createDownloadManager()
                val downloadRequest = DownloadRequest(
                    url = proxyUrl,
                    filePath = waveFile.absolutePath
                )

                val downloadResult = downloadManager.download(downloadRequest) { }

                if (!downloadResult.isSuccess) {
                    Logger.log(TAG, LogLevel.WARN, "Download failed: ${downloadResult.exceptionOrNull()?.message}")
                    val transparentBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                    return@withContext WaveDownloadResult(transparentBitmap, false)
                }

                Logger.log(TAG, LogLevel.INFO, "Downloaded: ${waveFile.absolutePath}")

                val waveAssemblyResult = assembleTilesFromMBTiles(applicationContext, filename, metadata.tiles)
                val waveBitmap = waveAssemblyResult?.bitmap ?: Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)

                waveFile.delete()

                WaveDownloadResult(waveBitmap, waveAssemblyResult != null)

            } catch (e: Exception) {
                Logger.log(TAG, LogLevel.ERROR, "Download/extraction failed: ${e.message}", e)
                val transparentBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                WaveDownloadResult(transparentBitmap, false)
            }
        }
    }

    private fun loadTerrainBackground(context: Context): Bitmap? {
        return try {
            val widgetDir = File(context.filesDir, "widget_images")
            val imageFile = File(widgetDir, "widget_map_snapshot.png")
            if (imageFile.exists()) {
                android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Failed to load terrain: ${e.message}", e)
            null
        }
    }

    private fun compositeWaveOverTerrain(terrain: Bitmap, wave: Bitmap): Bitmap {
        val result = terrain.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply { alpha = 255 }
        canvas.drawBitmap(wave, 0f, 0f, paint)
        return result
    }

    private fun saveCompositedImage(context: Context, bitmap: Bitmap, filename: String) {
        try {
            val widgetDir = File(context.filesDir, "widget_images")
            widgetDir.mkdirs()
            val imageFile = File(widgetDir, filename)

            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            Logger.log(TAG, LogLevel.INFO, "Saved: ${imageFile.absolutePath}")
        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Failed to save image: ${e.message}", e)
        }
    }

    private fun deleteOverlayImage(context: Context, filename: String) {
        try {
            val widgetDir = File(context.filesDir, "widget_images")
            val imageFile = File(widgetDir, filename)
            if (imageFile.exists()) {
                imageFile.delete()
                Logger.log(TAG, LogLevel.INFO, "Deleted old overlay: $filename")
            }
        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Failed to delete overlay: ${e.message}", e)
        }
    }

    private data class AssemblyResult(
        val bitmap: Bitmap,
        val successfulTiles: List<TileCoordinate>
    )

    private fun assembleTilesFromMBTiles(context: Context, filename: String, tiles: List<TileCoordinate>): AssemblyResult? {
        try {
            if (tiles.isEmpty()) return null

            val minX = tiles.minOf { it.x }
            val maxX = tiles.maxOf { it.x }
            val minY = tiles.minOf { it.y }
            val maxY = tiles.maxOf { it.y }

            val tileSize = 256
            val width = (maxX - minX + 1) * tileSize
            val height = (maxY - minY + 1) * tileSize

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint()

            val successfulTiles = mutableListOf<TileCoordinate>()

            for (tile in tiles) {
                val pixelX = (tile.x - minX) * tileSize
                val pixelY = (tile.y - minY) * tileSize

                val tileBitmap = extractTileFromMBTiles(context, filename, tile.zoom, tile.x, tile.y)
                if (tileBitmap != null) {
                    canvas.drawBitmap(tileBitmap, pixelX.toFloat(), pixelY.toFloat(), paint)
                    tileBitmap.recycle()
                    successfulTiles.add(tile)
                }
            }

            Logger.log(TAG, LogLevel.INFO, "Assembled ${successfulTiles.size}/${tiles.size} tiles")
            return AssemblyResult(bitmap, successfulTiles)

        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Failed to assemble tiles: ${e.message}", e)
            return null
        }
    }

    private fun extractTileFromMBTiles(context: Context, filename: String, zoom: Int, tileX: Int, tileY: Int): Bitmap? {
        return try {
            val mbtilesPath = File(context.cacheDir, "wave_forecast/$filename").absolutePath
            val tmsY = (1 shl zoom) - 1 - tileY

            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                mbtilesPath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )

            try {
                val cursor = db.rawQuery(
                    "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                    arrayOf(zoom.toString(), tileX.toString(), tmsY.toString())
                )

                if (cursor.moveToFirst()) {
                    val blob = cursor.getBlob(0)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(blob, 0, blob.size)
                    cursor.close()
                    return bitmap
                } else {
                    cursor.close()
                    return null
                }
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val WORK_NAME = "glance_wave_tomorrow_worker"

        fun scheduleDaily(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<GlanceWaveTomorrowWorker>(
                24, TimeUnit.HOURS,
                2, TimeUnit.HOURS
            )
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Logger.log(TAG, LogLevel.INFO, "Scheduled daily Glance wave tomorrow updates")
        }

        fun runImmediate(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<GlanceWaveTomorrowWorker>()
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
            Logger.log(TAG, LogLevel.INFO, "Scheduled immediate Glance wave tomorrow update")
        }

        fun runImmediateForceRefresh(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<GlanceWaveTomorrowWorker>()
                .setInputData(workDataOf("force_refresh" to true))
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
            Logger.log(TAG, LogLevel.INFO, "Scheduled forced Glance wave tomorrow update")
        }

        private fun calculateInitialDelay(): Long {
            val now = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            val target = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))

            target.set(java.util.Calendar.HOUR_OF_DAY, 0)
            target.set(java.util.Calendar.MINUTE, 0)
            target.set(java.util.Calendar.SECOND, 0)
            target.set(java.util.Calendar.MILLISECOND, 0)

            if (now.after(target)) {
                target.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }

            return target.timeInMillis - now.timeInMillis
        }
    }
}
