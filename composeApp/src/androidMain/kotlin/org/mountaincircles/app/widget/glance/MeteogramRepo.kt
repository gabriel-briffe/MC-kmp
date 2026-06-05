package org.mountaincircles.app.widget.glance

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.ui.WindyMeteogramMetadata
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "GlanceWidget_Repo"

/**
 * Singleton repository for meteogram widget data.
 * 
 * Uses StateFlow to enable reactive updates - when data changes,
 * the widget automatically recomposes.
 * 
 * This follows the pattern from Android Glance samples where:
 * 1. Repository exposes a StateFlow with data state
 * 2. Widget observes the flow with collectAsState()
 * 3. Data fetching updates the flow, triggering recomposition
 */
object MeteogramRepo {
    
    private val _state = MutableStateFlow<MeteogramState>(MeteogramState.Loading)
    val state: StateFlow<MeteogramState> get() = _state
    
    private val mutex = Mutex()
    private var dataRepository: MeteogramDataRepository? = null
    
    /**
     * Initialize with context (call once when widget starts).
     */
    fun init(context: Context) {
        if (dataRepository == null) {
            dataRepository = MeteogramDataRepository(context.applicationContext)
        }
    }
    
    /**
     * Get the data repository instance.
     */
    fun getRepository(): MeteogramDataRepository? = dataRepository
    
    /**
     * Load data from SharedPreferences and update the state.
     * Call this to load existing cached data.
     */
    suspend fun loadData(context: Context) {
        mutex.withLock {
            Logger.log(TAG, LogLevel.INFO, "Loading meteogram data from cache...")
            
            // Ensure repository is initialized
            if (dataRepository == null) {
                dataRepository = MeteogramDataRepository(context.applicationContext)
            }
            
            val repo = dataRepository!!
            val data = repo.loadData()
            val locationText = repo.getLocationText(data)
            
            if (data.hasData) {
                Logger.log(TAG, LogLevel.INFO, "Data loaded successfully from cache")
                _state.value = MeteogramState.Available(
                    data = data,
                    locationText = locationText
                )
            } else {
                Logger.log(TAG, LogLevel.INFO, "No cached data available")
                _state.value = MeteogramState.Unavailable(
                    message = "No data - tap refresh"
                )
            }
        }
    }
    
    /**
     * Set loading state (call before starting data fetch).
     */
    fun setLoading() {
        Logger.log(TAG, LogLevel.DEBUG, "Setting loading state")
        _state.value = MeteogramState.Loading
    }
    
    /**
     * Navigate to the previous table.
     */
    fun navigatePrevious() {
        val current = _state.value
        if (current is MeteogramState.Available && current.currentTableIndex > 0) {
            Logger.log(TAG, LogLevel.DEBUG, "Navigate to table ${current.currentTableIndex - 1}")
            _state.value = current.copy(currentTableIndex = current.currentTableIndex - 1)
        }
    }
    
    /**
     * Navigate to the next table.
     */
    fun navigateNext() {
        val current = _state.value
        if (current is MeteogramState.Available && current.currentTableIndex < 2) {
            Logger.log(TAG, LogLevel.DEBUG, "Navigate to table ${current.currentTableIndex + 1}")
            _state.value = current.copy(currentTableIndex = current.currentTableIndex + 1)
        }
    }
    
    /**
     * Clear SkySight data from SharedPreferences.
     * Used at start of refresh to show empty widget while data loads progressively.
     */
    fun clearSkySightData(context: Context) {
        Logger.log(TAG, LogLevel.INFO, "Clearing SkySight data for progressive refresh")
        
        val prefs = context.getSharedPreferences("windy_widget_prefs", Context.MODE_PRIVATE)
        val metadataJson = prefs.getString("windy_meteogram_metadata", null) ?: return
        
        try {
            val metadata = Json.decodeFromString<WindyMeteogramMetadata>(metadataJson)
            val clearedMetadata = metadata.copy(
                pfdtotMaxByDay = mutableMapOf(),
                hwcritMaxByDay = mutableMapOf(),
                wstarBsratioMaxByDay = mutableMapOf(),
                w4000MaxByDayAndHour = mutableMapOf()
            )
            val clearedJson = Json.encodeToString(clearedMetadata)
            prefs.edit().putString("windy_meteogram_metadata", clearedJson).apply()
            Logger.log(TAG, LogLevel.DEBUG, "SkySight data cleared")
        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Error clearing SkySight data: ${e.message}")
        }
    }
    
    /**
     * Fetch fresh weather and SkySight data from APIs.
     * This is the main refresh function that:
     * 1. Fetches weather data from Open-Meteo
     * 2. Fetches SkySight data if credentials are available
     * 3. Saves to SharedPreferences
     * 4. Updates the StateFlow to trigger widget recomposition
     */
    suspend fun fetchFreshData(context: Context) {
        val appContext = context.applicationContext
        
        Logger.log(TAG, LogLevel.INFO, "Starting fresh data fetch...")
        _state.value = MeteogramState.Loading
        
        withContext(Dispatchers.IO) {
            try {
                // Load metadata to get coordinates and credentials
                val prefs = appContext.getSharedPreferences("windy_widget_prefs", Context.MODE_PRIVATE)
                val metadataJson = prefs.getString("windy_meteogram_metadata", null)
                
                if (metadataJson == null) {
                    Logger.log(TAG, LogLevel.WARN, "No metadata found - cannot fetch data")
                    _state.value = MeteogramState.Unavailable("Set meteogram location in app")
                    return@withContext
                }
                
                val metadata = Json.decodeFromString<WindyMeteogramMetadata>(metadataJson)
                Logger.log(TAG, LogLevel.INFO, "Fetching data for: ${metadata.latitude}, ${metadata.longitude}")
                
                // Fetch weather data from Open-Meteo
                val weatherDeferred = async {
                    fetchWeatherFromApi(metadata.latitude, metadata.longitude)
                }
                
                // Fetch SkySight data if credentials available
                val skysightDeferred = async {
                    if (!metadata.skysightEmail.isNullOrBlank() && !metadata.skysightPassword.isNullOrBlank()) {
                        Logger.log(TAG, LogLevel.INFO, "SkySight credentials found - fetching data...")
                        fetchSkySightData(metadata, appContext)
                    } else {
                        Logger.log(TAG, LogLevel.INFO, "No SkySight credentials - skipping")
                        null
                    }
                }
                
                // Wait for weather data
                val weatherResponse = weatherDeferred.await()
                
                if (weatherResponse != null) {
                    // Save weather data to SharedPreferences
                    val meteogramPrefs = appContext.getSharedPreferences("meteogram_data", Context.MODE_PRIVATE)
                    meteogramPrefs.edit()
                        .putString("weather_json", weatherResponse)
                        .putLong("last_refresh_timestamp", System.currentTimeMillis())
                        .apply()
                    
                    Logger.log(TAG, LogLevel.INFO, "Weather data saved to SharedPreferences")
                }
                
                // Wait for SkySight data (if any)
                val skysightResult = skysightDeferred.await()
                if (skysightResult != null) {
                    Logger.log(TAG, LogLevel.INFO, "SkySight data fetch completed: $skysightResult")
                }
                
                // Update state from SharedPreferences
                // (Caller will trigger updateAll after this function returns)
                loadData(appContext)
                Logger.log(TAG, LogLevel.INFO, "State updated from cache")
                
            } catch (e: Exception) {
                Logger.log(TAG, LogLevel.ERROR, "Error fetching data: ${e.message}", e)
                _state.value = MeteogramState.Unavailable("Error: ${e.message}")
            }
        }
    }
    
    /**
     * Fetch weather data from Open-Meteo API.
     */
    private suspend fun fetchWeatherFromApi(latitude: Double, longitude: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$latitude&longitude=$longitude" +
                    "&hourly=wind_speed_500hPa,wind_direction_500hPa," +
                    "wind_speed_600hPa,wind_direction_600hPa," +
                    "wind_speed_700hPa,wind_direction_700hPa," +
                    "precipitation,weather_code,freezing_level_height," +
                    "temperature_2m,pressure_msl" +
                    "&models=best_match&forecast_days=9&wind_speed_unit=ms&cell_selection=nearest"
                
                Logger.log(TAG, LogLevel.DEBUG, "Fetching weather from: $url")
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                Logger.log(TAG, LogLevel.DEBUG, "Weather API response code: $responseCode")
                
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    Logger.log(TAG, LogLevel.INFO, "Weather data fetched successfully")
                    response
                } else {
                    Logger.log(TAG, LogLevel.ERROR, "Weather API failed with code: $responseCode")
                    connection.disconnect()
                    null
                }
            } catch (e: Exception) {
                Logger.log(TAG, LogLevel.ERROR, "Weather fetch error: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Fetch SkySight data using the dedicated GlanceSkySightHandler.
     * Completely isolated from legacy widget infrastructure.
     */
    private suspend fun fetchSkySightData(metadata: WindyMeteogramMetadata, context: Context): String {
        return withContext(Dispatchers.IO) {
            try {
                // Prepare for fresh fetch
                GlanceSkySightHandler.prepareForRefresh()
                
                // Download SkySight data for multiple days (9 days to cover full grid)
                // GlanceSkySightHandler saves data to SharedPreferences incrementally
                val downloadResult = GlanceSkySightHandler.downloadDataForMultipleDays(
                    metadata, context, 9
                )
                
                if (downloadResult.isSuccess) {
                    Logger.log(TAG, LogLevel.INFO, "SkySight data downloaded successfully")
                    
                    val pfdtotData = GlanceSkySightHandler.getPfdtotMaxByDay()
                    val hwcritData = GlanceSkySightHandler.getHwcritMaxByDay()
                    val wstarData = GlanceSkySightHandler.getWstarBsratioMaxByDay()
                    val w4000Data = GlanceSkySightHandler.getW4000MaxByDayAndHour()
                    
                    Logger.log(TAG, LogLevel.INFO, "SkySight data: " +
                        "pfdtot=${pfdtotData.size}, hwcrit=${hwcritData.size}, " +
                        "wstar=${wstarData.size}, w4000=${w4000Data.size}")
                    
                    "success"
                } else {
                    Logger.log(TAG, LogLevel.ERROR, "SkySight download failed: ${downloadResult.exceptionOrNull()?.message}")
                    "failed"
                }
            } catch (e: Exception) {
                Logger.log(TAG, LogLevel.ERROR, "SkySight fetch error: ${e.message}", e)
                "error"
            }
        }
    }
}

/**
 * Sealed class representing the widget data state.
 */
sealed class MeteogramState {
    /**
     * Data is being loaded.
     */
    data object Loading : MeteogramState()
    
    /**
     * Data is available and ready to display.
     */
    data class Available(
        val data: MeteogramData,
        val locationText: String,
        val currentTableIndex: Int = 0  // 0, 1, or 2 for 3 tables
    ) : MeteogramState()
    
    /**
     * Data is not available (no location set, or fetch failed).
     */
    data class Unavailable(
        val message: String
    ) : MeteogramState()
}
