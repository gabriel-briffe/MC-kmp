package org.mountaincircles.app.widget.glance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TAG = "GlanceWidget_WaveToday"
private const val REFRESH_IN_PROGRESS_KEY = "wave_today_refresh_in_progress"

/**
 * State for the Wave Today widget.
 */
sealed class WaveTodayState {
    data object Loading : WaveTodayState()
    
    data class Refreshing(
        val terrainBitmap: Bitmap?
    ) : WaveTodayState()
    
    data class Available(
        val imageBitmap: Bitmap,
        val titleText: String,
        val lastRefreshTime: String?
    ) : WaveTodayState()
    
    data class Unavailable(
        val message: String
    ) : WaveTodayState()
}

/**
 * Singleton repository for Wave Today widget data.
 * 
 * Manages state for the wave forecast image display.
 * The widget shows a pre-composited image from the file system.
 */
object WaveTodayRepo {
    
    private val _state = MutableStateFlow<WaveTodayState>(WaveTodayState.Loading)
    val state: StateFlow<WaveTodayState> get() = _state
    
    private val mutex = Mutex()
    
    private const val PREFS_NAME = "WaveTodayWidgetProvider_data"
    private const val IMAGE_FILENAME = "widget_today_overlay.png"
    private const val TERRAIN_FILENAME = "widget_map_snapshot.png"
    
    /**
     * Load wave image from file system and update state.
     */
    suspend fun loadData(context: Context) {
        mutex.withLock {
            Logger.log(TAG, LogLevel.INFO, "Loading wave today data...")
            
            withContext(Dispatchers.IO) {
                val widgetDir = File(context.filesDir, "widget_images")
                val compositedFile = File(widgetDir, IMAGE_FILENAME)
                val terrainFile = File(widgetDir, TERRAIN_FILENAME)
                
                // Try composited image first, then terrain fallback
                val imageFile = when {
                    compositedFile.exists() -> {
                        Logger.log(TAG, LogLevel.DEBUG, "Using composited wave image")
                        compositedFile
                    }
                    terrainFile.exists() -> {
                        Logger.log(TAG, LogLevel.DEBUG, "Using terrain-only image (no wave data)")
                        terrainFile
                    }
                    else -> {
                        Logger.log(TAG, LogLevel.WARN, "No wave images available")
                        null
                    }
                }
                
                if (imageFile != null) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                        if (bitmap != null) {
                            val titleText = getTitleText(compositedFile.exists())
                            val lastRefreshTime = getLastRefreshTime(context)
                            
                            Logger.log(TAG, LogLevel.INFO, "Wave image loaded: ${bitmap.width}x${bitmap.height}")
                            _state.value = WaveTodayState.Available(
                                imageBitmap = bitmap,
                                titleText = titleText,
                                lastRefreshTime = lastRefreshTime
                            )
                        } else {
                            Logger.log(TAG, LogLevel.ERROR, "Failed to decode wave image")
                            _state.value = WaveTodayState.Unavailable("Failed to load image")
                        }
                    } catch (e: Exception) {
                        Logger.log(TAG, LogLevel.ERROR, "Error loading wave image: ${e.message}", e)
                        _state.value = WaveTodayState.Unavailable("Error: ${e.message}")
                    }
                } else {
                    _state.value = WaveTodayState.Unavailable("Set wave region in app")
                }
            }
        }
    }
    
    /**
     * Trigger a fresh wave forecast update.
     */
    suspend fun refreshData(context: Context) {
        Logger.log(TAG, LogLevel.INFO, "Triggering wave forecast refresh...")
        
        withContext(Dispatchers.IO) {
            try {
                // Load terrain bitmap for display during refresh
                val widgetDir = File(context.filesDir, "widget_images")
                val terrainFile = File(widgetDir, TERRAIN_FILENAME)
                val terrainBitmap = if (terrainFile.exists()) {
                    BitmapFactory.decodeFile(terrainFile.absolutePath)
                } else null
                
                _state.value = WaveTodayState.Refreshing(terrainBitmap)
                
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                
                // Mark refresh as in progress
                prefs.edit().putBoolean(REFRESH_IN_PROGRESS_KEY, true).apply()
                
                // Trigger the Glance-specific WaveForecastWorker for immediate update
                GlanceWaveForecastWorker.runImmediateForceRefresh(context)
                
                // Poll until worker completes (max 60 seconds)
                val startTime = System.currentTimeMillis()
                val maxWaitTime = 60_000L
                while (prefs.getBoolean(REFRESH_IN_PROGRESS_KEY, false)) {
                    if (System.currentTimeMillis() - startTime > maxWaitTime) {
                        Logger.log(TAG, LogLevel.WARN, "Refresh timeout after ${maxWaitTime}ms")
                        break
                    }
                    kotlinx.coroutines.delay(500)
                }
                
                loadData(context)
            } catch (e: Exception) {
                Logger.log(TAG, LogLevel.ERROR, "Refresh failed: ${e.message}", e)
                _state.value = WaveTodayState.Unavailable("Refresh failed: ${e.message}")
            }
        }
    }
    
    /**
     * Called by worker when refresh is complete.
     */
    fun markRefreshComplete(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(REFRESH_IN_PROGRESS_KEY, false)
            .putLong("last_refresh_timestamp", System.currentTimeMillis())
            .apply()
        Logger.log(TAG, LogLevel.DEBUG, "Refresh marked complete")
    }
    
    /**
     * Get the title text for the widget.
     */
    private fun getTitleText(hasWaveData: Boolean): String {
        return if (hasWaveData) {
            val today = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val todayStr = dateFormat.format(today.time)
            "TODAY $todayStr 12h 4200m"
        } else {
            "TODAY - no wave data yet"
        }
    }
    
    /**
     * Get last refresh time formatted as HH:mm.
     */
    private fun getLastRefreshTime(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("last_refresh_timestamp", 0L)
        if (timestamp == 0L) return null
        
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}
