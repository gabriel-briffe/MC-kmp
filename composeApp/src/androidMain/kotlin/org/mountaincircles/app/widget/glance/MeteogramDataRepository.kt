package org.mountaincircles.app.widget.glance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.serialization.json.*
import org.mountaincircles.app.R
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.ui.WindyMeteogramMetadata
import org.mountaincircles.app.modules.skysight.logic.SkysightUtils

private const val TAG = "GlanceWidget_DataRepo"

/**
 * Repository for loading meteogram data from SharedPreferences.
 * Provides data for the Glance-based meteogram widget.
 */
class MeteogramDataRepository(val context: Context) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Load all meteogram data needed for the widget.
     */
    fun loadData(): MeteogramData {
        val weatherJson = loadWeatherJson()
        val metadata = loadMetadata()
        
        Logger.log(TAG, LogLevel.DEBUG, "loadData: weatherJson=${weatherJson != null}, metadata=${metadata != null}")
        if (metadata != null) {
            Logger.log(TAG, LogLevel.DEBUG, "metadata: skysightEmail=${metadata.skysightEmail?.take(3)}***, " +
                "skysightRegion=${metadata.skysightRegion}, " +
                "pfdtotMaxByDay=${metadata.pfdtotMaxByDay}, " +
                "hwcritMaxByDay=${metadata.hwcritMaxByDay}, " +
                "wstarBsratioMaxByDay=${metadata.wstarBsratioMaxByDay}, " +
                "w4000MaxByDayAndHour=${metadata.w4000MaxByDayAndHour}")
        }
        
        return MeteogramData(
            weatherData = weatherJson,
            metadata = metadata,
            hasData = weatherJson != null && metadata != null
        )
    }
    
    /**
     * Get the last refresh timestamp formatted as HH:mm.
     * Returns null if no refresh has occurred.
     */
    fun getLastRefreshTime(): String? {
        val prefs = context.getSharedPreferences("meteogram_data", Context.MODE_PRIVATE)
        val timestamp = prefs.getLong("last_refresh_timestamp", 0L)
        if (timestamp == 0L) return null
        
        val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return formatter.format(java.util.Date(timestamp))
    }
    
    private fun loadWeatherJson(): JsonObject? {
        val prefs = context.getSharedPreferences("meteogram_data", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("weather_json", null) ?: return null
        
        return try {
            Json.parseToJsonElement(jsonString).jsonObject
        } catch (e: Exception) {
            null
        }
    }
    
    private fun loadMetadata(): WindyMeteogramMetadata? {
        val prefs = context.getSharedPreferences("windy_widget_prefs", Context.MODE_PRIVATE)
        val metadataJson = prefs.getString("windy_meteogram_metadata", null) ?: return null
        
        return try {
            json.decodeFromString<WindyMeteogramMetadata>(metadataJson)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get location display text.
     */
    fun getLocationText(data: MeteogramData): String {
        val metadata = data.metadata ?: return "No location set"
        val locationName = metadata.locationName ?: "Unknown"
        val lat = String.format("%.2f", metadata.latitude)
        val lon = String.format("%.2f", metadata.longitude)
        return "$locationName • ${lat}°N ${lon}°E"
    }
    
    /**
     * Check if SkySight credentials are available.
     */
    fun hasSkySightCredentials(data: MeteogramData): Boolean {
        val metadata = data.metadata ?: run {
            Logger.log(TAG, LogLevel.DEBUG, "hasSkySightCredentials: metadata is null")
            return false
        }
        val hasCredentials = !metadata.skysightEmail.isNullOrBlank() &&
               !metadata.skysightPassword.isNullOrBlank() &&
               !metadata.skysightRegion.isNullOrBlank()
        Logger.log(TAG, LogLevel.DEBUG, "hasSkySightCredentials: $hasCredentials " +
            "(email=${!metadata.skysightEmail.isNullOrBlank()}, " +
            "pass=${!metadata.skysightPassword.isNullOrBlank()}, " +
            "region=${!metadata.skysightRegion.isNullOrBlank()})")
        return hasCredentials
    }
    
    /**
     * Get hourly value from weather data.
     */
    fun getHourlyValue(data: MeteogramData, fieldName: String, timeIndex: Int): Double? {
        if (timeIndex < 0) return null
        
        return try {
            val hourly = data.weatherData?.get("hourly")?.jsonObject ?: return null
            val dataArray = hourly[fieldName]?.jsonArray ?: return null
            
            if (timeIndex < dataArray.size) {
                dataArray[timeIndex].jsonPrimitive.content.toDoubleOrNull()
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get wind speed for a pressure level.
     */
    fun getWindSpeed(data: MeteogramData, pressureLevel: String, timeIndex: Int): Float? {
        return getHourlyValue(data, "wind_speed_$pressureLevel", timeIndex)?.toFloat()
    }
    
    /**
     * Get wind direction for a pressure level.
     */
    fun getWindDirection(data: MeteogramData, pressureLevel: String, timeIndex: Int): Float? {
        return getHourlyValue(data, "wind_direction_$pressureLevel", timeIndex)?.toFloat()
    }
    
    /**
     * Get hour text from timestamp.
     */
    fun getHourText(data: MeteogramData, timeIndex: Int): HourInfo {
        if (timeIndex < 0) return HourInfo("?", false)
        
        try {
            val hourly = data.weatherData?.get("hourly")?.jsonObject ?: return HourInfo("?", false)
            val timeArray = hourly["time"]?.jsonArray ?: return HourInfo("?", false)
            
            if (timeIndex < timeArray.size) {
                val timestamp = timeArray[timeIndex].jsonPrimitive.content
                val hour = timestamp.substringAfter("T").substringBefore(":")
                val date = timestamp.substringBefore("T").substringAfterLast("-")
                val month = timestamp.substringBefore("T").split("-").getOrNull(1) ?: ""
                
                return if (hour == "00") {
                    HourInfo("$date/$month", isMidnight = true)
                } else {
                    HourInfo("${hour}h", isMidnight = false)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return HourInfo("?", false)
    }
    
    /**
     * Get day name (Monday, Tuesday, etc.) for a given day index.
     */
    fun getDayName(data: MeteogramData, dayIndex: Int): String {
        try {
            val hourly = data.weatherData?.get("hourly")?.jsonObject ?: return "?"
            val timeArray = hourly["time"]?.jsonArray ?: return "?"
            
            // Get the timestamp for the first hour of this day (midnight = timeIndex 0 of the day)
            val timeIndex = dayIndex * 24
            if (timeIndex < timeArray.size) {
                val timestamp = timeArray[timeIndex].jsonPrimitive.content
                // Parse date: "2024-01-15T00:00"
                val datePart = timestamp.substringBefore("T")
                val parts = datePart.split("-")
                if (parts.size == 3) {
                    val year = parts[0].toInt()
                    val month = parts[1].toInt()
                    val day = parts[2].toInt()
                    
                    val calendar = java.util.Calendar.getInstance()
                    calendar.set(year, month - 1, day)
                    
                    return when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
                        java.util.Calendar.SUNDAY -> "Sunday"
                        java.util.Calendar.MONDAY -> "Monday"
                        java.util.Calendar.TUESDAY -> "Tuesday"
                        java.util.Calendar.WEDNESDAY -> "Wednesday"
                        java.util.Calendar.THURSDAY -> "Thursday"
                        java.util.Calendar.FRIDAY -> "Friday"
                        java.util.Calendar.SATURDAY -> "Saturday"
                        else -> "?"
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return "?"
    }
    
    /**
     * Get precipitation in mm.
     */
    fun getPrecipitation(data: MeteogramData, timeIndex: Int): Double? {
        return getHourlyValue(data, "precipitation", timeIndex)
    }
    
    /**
     * Get freezing level height.
     */
    fun getFreezingLevel(data: MeteogramData, timeIndex: Int): Int? {
        return getHourlyValue(data, "freezing_level_height", timeIndex)?.let {
            (kotlin.math.round(it / 10.0) * 10).toInt()
        }
    }
    
    /**
     * Get temperature.
     */
    fun getTemperature(data: MeteogramData, timeIndex: Int): Double? {
        return getHourlyValue(data, "temperature_2m", timeIndex)
    }
    
    /**
     * Get pressure.
     */
    fun getPressure(data: MeteogramData, timeIndex: Int): Int? {
        return getHourlyValue(data, "pressure_msl", timeIndex)?.toInt()
    }
    
    /**
     * Get weather code.
     */
    fun getWeatherCode(data: MeteogramData, timeIndex: Int): Int? {
        return getHourlyValue(data, "weather_code", timeIndex)?.toInt()
    }
    
    /**
     * Calculate time index from hour column and day offset.
     */
    fun calculateTimeIndex(hourIndex: Int, dayIndex: Int): Int {
        // Each hour column represents 6 hours
        // dayIndex is the absolute day (0-8)
        // hourIndex is 0-3 for hours 0, 6, 12, 18
        return hourIndex * 6 + dayIndex * 24
    }
    
    /**
     * Get wind barb drawable ID based on wind speed (m/s).
     */
    fun getWindBarbDrawableId(speed: Float): Int {
        return when {
            speed < 1.0f -> R.drawable.wb_0_white
            speed < 2.5f -> R.drawable.wb_2_white
            speed < 5.0f -> R.drawable.wb_5_white
            speed < 7.5f -> R.drawable.wb_10_white
            speed < 10.0f -> R.drawable.wb_15_white
            speed < 12.5f -> R.drawable.wb_20_white
            speed < 15.0f -> R.drawable.wb_25_white
            speed < 17.5f -> R.drawable.wb_30_white
            speed < 20.0f -> R.drawable.wb_35_white
            speed < 22.5f -> R.drawable.wb_40_white
            speed < 25.0f -> R.drawable.wb_45_white
            speed < 27.5f -> R.drawable.wb_50_white
            speed < 30.0f -> R.drawable.wb_55_white
            speed < 32.5f -> R.drawable.wb_60_white
            speed < 35.0f -> R.drawable.wb_65_white
            speed < 37.5f -> R.drawable.wb_70_white
            speed < 40.0f -> R.drawable.wb_75_white
            speed < 42.5f -> R.drawable.wb_80_white
            speed < 45.0f -> R.drawable.wb_85_white
            speed < 47.5f -> R.drawable.wb_90_white
            speed < 50.0f -> R.drawable.wb_95_white
            speed < 52.5f -> R.drawable.wb_100_white
            speed < 55.0f -> R.drawable.wb_105_white
            speed < 57.5f -> R.drawable.wb_110_white
            speed < 60.0f -> R.drawable.wb_115_white
            speed < 62.5f -> R.drawable.wb_120_white
            speed < 65.0f -> R.drawable.wb_125_white
            speed < 67.5f -> R.drawable.wb_130_white
            speed < 70.0f -> R.drawable.wb_135_white
            speed < 72.5f -> R.drawable.wb_140_white
            speed < 75.0f -> R.drawable.wb_145_white
            speed < 77.5f -> R.drawable.wb_150_white
            speed < 80.0f -> R.drawable.wb_155_white
            speed < 82.5f -> R.drawable.wb_160_white
            speed < 85.0f -> R.drawable.wb_165_white
            speed < 87.5f -> R.drawable.wb_170_white
            speed < 90.0f -> R.drawable.wb_175_white
            speed < 92.5f -> R.drawable.wb_180_white
            speed < 95.0f -> R.drawable.wb_185_white
            else -> R.drawable.wb_190_white
        }
    }
    
    /**
     * Get weather icon drawable ID based on weather code.
     */
    fun getWeatherIconDrawableId(code: Int): Int {
        return when (code) {
            0 -> R.drawable.sun // Clear sky
            1 -> R.drawable.scattered // Mainly clear
            2 -> R.drawable.broken // Partly cloudy
            3 -> R.drawable.overcast // Overcast
            45, 48 -> R.drawable.smoke // Fog and depositing rime fog
            51, 53, 55 -> R.drawable.rain // Drizzle
            56, 57 -> R.drawable.snow // Freezing Drizzle
            61, 63, 65 -> R.drawable.rain // Rain
            66, 67 -> R.drawable.snow // Freezing Rain
            71, 73, 75 -> R.drawable.snow // Snow fall
            77 -> R.drawable.snow // Snow grains
            80, 81, 82 -> R.drawable.rainshower // Rain showers
            85, 86 -> R.drawable.snowshower // Snow showers
            95 -> R.drawable.tslight // Thunderstorm slight or moderate
            96, 99 -> R.drawable.tsheavy // Thunderstorm with hail
            else -> R.drawable.sun // Default to clear sky
        }
    }
    
    /**
     * Create a rotated bitmap from a drawable resource.
     * Used for wind barbs which need to show wind direction.
     * 
     * Wind barb vectors are 40x90dp. We normalize all barbs to a consistent
     * square canvas before rotation, ensuring uniform sizing regardless of angle.
     * 
     * Process:
     * 1. Draw 40x90 vector into a 90x90 square (centered)
     * 2. Rotate within the square
     * 3. Scale to final output size
     */
    fun createRotatedBitmap(drawableId: Int, rotationDegrees: Float, outputSize: Int = 20): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
            
            // Original vector dimensions
            val vectorWidth = 40
            val vectorHeight = 90
            
            // Use the larger dimension as the square canvas size
            val canvasSize = vectorHeight // 90
            
            // Create square canvas
            val squareBitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(squareBitmap)
            
            // Draw the vector centered in the square
            val originalBitmap = drawable.toBitmap(vectorWidth, vectorHeight)
            val offsetX = (canvasSize - vectorWidth) / 2f
            val offsetY = (canvasSize - vectorHeight) / 2f
            canvas.drawBitmap(originalBitmap, offsetX, offsetY, null)
            
            // Create rotated bitmap (same size square)
            val rotatedBitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
            val rotatedCanvas = Canvas(rotatedBitmap)
            
            // Rotate around center
            rotatedCanvas.translate(canvasSize / 2f, canvasSize / 2f)
            rotatedCanvas.rotate(rotationDegrees)
            rotatedCanvas.translate(-canvasSize / 2f, -canvasSize / 2f)
            rotatedCanvas.drawBitmap(squareBitmap, 0f, 0f, null)
            
            // Scale to final output size
            Bitmap.createScaledBitmap(rotatedBitmap, outputSize, outputSize, true)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get wave (w4000) data for a specific time index.
     * Wave data is stored by day and hour (6h, 12h, 18h only).
     */
    fun getWaveData(data: MeteogramData, timeIndex: Int): WaveDisplayData? {
        val metadata = data.metadata ?: run {
            Logger.log(TAG, LogLevel.DEBUG, "getWaveData[$timeIndex]: metadata null")
            return null
        }
        val w4000Map = metadata.w4000MaxByDayAndHour ?: run {
            Logger.log(TAG, LogLevel.DEBUG, "getWaveData[$timeIndex]: w4000Map null")
            return null
        }
        
        // Calculate day offset and hour from time index
        val dayOffset = timeIndex / 24
        val hourInDay = (timeIndex % 24)
        
        // Wave data only available at 6h, 12h, 18h
        if (hourInDay !in listOf(6, 12, 18)) return null
        
        val key = "${dayOffset}_$hourInDay"
        val value = w4000Map[key] ?: run {
            Logger.log(TAG, LogLevel.DEBUG, "getWaveData[$timeIndex]: no value for key=$key, available keys=${w4000Map.keys}")
            return null
        }
        
        val formattedValue = if (value > 0) {
            "+${String.format("%.1f", value)}"
        } else {
            String.format("%.1f", value)
        }
        
        val color = getColorFromStops(value, metadata.w4000ColorStops)
        Logger.log(TAG, LogLevel.DEBUG, "getWaveData[$timeIndex]: key=$key, value=$formattedValue")
        
        return WaveDisplayData(formattedValue, color)
    }
    
    /**
     * Get SkySight data for a specific time index.
     * SkySight shows pfdtot at 6h column, hwcrit at 12h, wstar at 18h.
     */
    fun getSkySightData(data: MeteogramData, timeIndex: Int): SkySightDisplayData? {
        val metadata = data.metadata ?: run {
            Logger.log(TAG, LogLevel.DEBUG, "getSkySightData[$timeIndex]: metadata null")
            return null
        }
        
        // Calculate day offset and hour from time index
        val dayOffset = timeIndex / 24
        val hourInDay = (timeIndex % 24)
        
        // SkySight data at specific hours: pfdtot@6h, hwcrit@12h, wstar@18h
        return when (hourInDay) {
            6 -> {
                val value = metadata.pfdtotMaxByDay?.get(dayOffset) ?: run {
                    Logger.log(TAG, LogLevel.DEBUG, "getSkySightData[$timeIndex]: pfdtot null for day=$dayOffset, available=${metadata.pfdtotMaxByDay?.keys}")
                    return null
                }
                val color = getColorFromStops(value, metadata.pfdtotColorStops)
                Logger.log(TAG, LogLevel.DEBUG, "getSkySightData[$timeIndex]: pfdtot=$value for day=$dayOffset")
                SkySightDisplayData(String.format("%.0f", value), "km", color)
            }
            12 -> {
                val value = metadata.hwcritMaxByDay?.get(dayOffset) ?: run {
                    Logger.log(TAG, LogLevel.DEBUG, "getSkySightData[$timeIndex]: hwcrit null for day=$dayOffset, available=${metadata.hwcritMaxByDay?.keys}")
                    return null
                }
                val color = getColorFromStops(value, metadata.hwcritColorStops)
                Logger.log(TAG, LogLevel.DEBUG, "getSkySightData[$timeIndex]: hwcrit=$value for day=$dayOffset")
                SkySightDisplayData(String.format("%.0f", value), "m", color)
            }
            18 -> {
                val value = metadata.wstarBsratioMaxByDay?.get(dayOffset) ?: run {
                    Logger.log(TAG, LogLevel.DEBUG, "getSkySightData[$timeIndex]: wstar null for day=$dayOffset, available=${metadata.wstarBsratioMaxByDay?.keys}")
                    return null
                }
                val formattedValue = if (value > 0) "+${String.format("%.1f", value)}" else String.format("%.1f", value)
                val color = getColorFromStops(value, metadata.wstarBsratioColorStops)
                Logger.log(TAG, LogLevel.DEBUG, "getSkySightData[$timeIndex]: wstar=$formattedValue for day=$dayOffset")
                SkySightDisplayData(formattedValue, "m/s", color)
            }
            else -> null
        }
    }
    
    /**
     * Get color from color stops based on value.
     * Uses the same logic as the SkySight module for consistency.
     */
    private fun getColorFromStops(
        value: Float,
        colorStops: List<org.mountaincircles.app.modules.skysight.logic.data.LayerColorStop>?
    ): androidx.compose.ui.graphics.Color {
        return SkysightUtils.getColorForValue(value, colorStops ?: emptyList())
    }
    
    companion object {
        const val HOURS_PER_DAY = 4 // 0h, 6h, 12h, 18h
        const val DAYS_PER_TABLE = 3
        const val NUM_TABLES = 3
    }
}

/**
 * Container for all meteogram data.
 */
data class MeteogramData(
    val weatherData: JsonObject?,
    val metadata: WindyMeteogramMetadata?,
    val hasData: Boolean
)

/**
 * Hour display info.
 */
data class HourInfo(
    val text: String,
    val isMidnight: Boolean
)

/**
 * Wave display data for widget.
 */
data class WaveDisplayData(
    val formattedValue: String,
    val color: androidx.compose.ui.graphics.Color
)

/**
 * SkySight display data for widget.
 */
data class SkySightDisplayData(
    val formattedValue: String,
    val unit: String,
    val color: androidx.compose.ui.graphics.Color
)
