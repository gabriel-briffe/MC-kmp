package org.mountaincircles.app.widget.glance

import android.content.Context
import androidx.glance.appwidget.updateAll
import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.encodeToString
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.logic.data.SkysightLoginResponse
import org.mountaincircles.app.modules.skysight.logic.data.SkysightDataUrl
import org.mountaincircles.app.modules.skysight.logic.data.LayerColorStop
import org.mountaincircles.app.modules.skysight.logic.data.LayerLegend
import org.mountaincircles.app.modules.skysight.logic.data.SkysightLayer
import org.mountaincircles.app.modules.skysight.logic.NetCDFReaderV2
import org.mountaincircles.app.modules.skysight.logic.controllers.SkysightTilingControllerV2Tiles
import org.mountaincircles.app.ui.WindyMeteogramMetadata
import java.io.File

private const val TAG = "GlanceWidget_SkySight"
private const val TAG_API = "GlanceWidget_SkySightAPI"

/**
 * SkySight handler dedicated to the Glance widget.
 * Completely isolated from legacy widget infrastructure.
 * 
 * Downloads NetCDF data and extracts max values for meteogram display.
 */
object GlanceSkySightHandler {

    private const val BASE_URL = "https://skysight.io/api"
    private const val API_KEY = "MountainCircles"
    private const val USER_AGENT = "MountainCircles"

    // Cache for API key to avoid repeated logins during widget refresh
    private var cachedApiKey: String? = null
    private var cachedApiKeyValidUntil: Long? = null

    // Store SkySight max values by day offset
    private var pfdtotMaxByDay: MutableMap<Int, Float> = mutableMapOf()
    private var hwcritMaxByDay: MutableMap<Int, Float> = mutableMapOf()
    private var wstarBsratioMaxByDay: MutableMap<Int, Float> = mutableMapOf()
    private var w4000MaxByDayAndHour: MutableMap<String, Float> = mutableMapOf()

    // Store color stops for each layer
    private var pfdtotColorStops: List<LayerColorStop>? = null
    private var hwcritColorStops: List<LayerColorStop>? = null
    private var wstarBsratioColorStops: List<LayerColorStop>? = null
    private var w4000ColorStops: List<LayerColorStop>? = null

    /**
     * Clear all cached data to prepare for fresh fetch.
     */
    fun prepareForRefresh() {
        cachedApiKey = null
        cachedApiKeyValidUntil = null
        pfdtotMaxByDay.clear()
        hwcritMaxByDay.clear()
        wstarBsratioMaxByDay.clear()
        w4000MaxByDayAndHour.clear()
        pfdtotColorStops = null
        hwcritColorStops = null
        wstarBsratioColorStops = null
        w4000ColorStops = null
        Logger.log(TAG, LogLevel.DEBUG, "Cleared all caches for fresh fetch")
    }

    // Getters for extracted data
    fun getPfdtotMaxByDay(): Map<Int, Float> = pfdtotMaxByDay.toMap()
    fun getHwcritMaxByDay(): Map<Int, Float> = hwcritMaxByDay.toMap()
    fun getWstarBsratioMaxByDay(): Map<Int, Float> = wstarBsratioMaxByDay.toMap()
    fun getW4000MaxByDayAndHour(): Map<String, Float> = w4000MaxByDayAndHour.toMap()
    fun getPfdtotColorStops(): List<LayerColorStop>? = pfdtotColorStops
    fun getHwcritColorStops(): List<LayerColorStop>? = hwcritColorStops
    fun getWstarBsratioColorStops(): List<LayerColorStop>? = wstarBsratioColorStops
    fun getW4000ColorStops(): List<LayerColorStop>? = w4000ColorStops

    /**
     * Download and extract SkySight data for multiple days.
     * This is the main entry point for fetching SkySight data.
     */
    suspend fun downloadDataForMultipleDays(
        metadata: WindyMeteogramMetadata,
        context: Context,
        daysToProcess: Int = 9
    ): Result<Unit> {
        Logger.log(TAG, LogLevel.INFO, "Starting SkySight data extraction for $daysToProcess days")

        try {
            // First, fetch layer information to get color stops
            Logger.log(TAG, LogLevel.INFO, "Fetching layer color information")
            val layersResult = fetchLayers(metadata)
            if (layersResult.isSuccess) {
                val layers = layersResult.getOrThrow()
                layers.forEach { layer ->
                    when (layer.id) {
                        "pfdtot" -> layer.legend?.colors?.let { pfdtotColorStops = it }
                        "hwcrit" -> layer.legend?.colors?.let { hwcritColorStops = it }
                        "wstar_bsratio" -> layer.legend?.colors?.let { wstarBsratioColorStops = it }
                        "w_4000" -> layer.legend?.colors?.let { w4000ColorStops = it }
                    }
                }
                Logger.log(TAG, LogLevel.INFO, "Retrieved color stops: pfdtot=${pfdtotColorStops?.size ?: 0}, hwcrit=${hwcritColorStops?.size ?: 0}, wstar=${wstarBsratioColorStops?.size ?: 0}, w4000=${w4000ColorStops?.size ?: 0}")
            } else {
                Logger.log(TAG, LogLevel.WARN, "Failed to fetch layer colors, using fallback")
            }

            val today = java.time.LocalDate.now()

            for (dayOffset in 0 until daysToProcess) {
                val targetDate = today.plusDays(dayOffset.toLong()).toString()
                Logger.log(TAG, LogLevel.INFO, "Processing day $dayOffset: $targetDate")

                try {
                    // 1. Get pfdtot max value at noon
                    val pfdtotResult = downloadNetCdfAndExtract(metadata, "pfdtot", targetDate)
                    if (pfdtotResult.isSuccess) {
                        pfdtotMaxByDay[dayOffset] = pfdtotResult.getOrThrow()
                        Logger.log(TAG, LogLevel.INFO, "Day $dayOffset pfdtot: ${pfdtotMaxByDay[dayOffset]}")
                        savePartialData(metadata, context)
                    }

                    // 2. Get hwcrit value at 14:00
                    val hwcritValue = extractValueAtTime(metadata, targetDate, "hwcrit", 14 * 3600)
                    if (!hwcritValue.isNaN()) {
                        hwcritMaxByDay[dayOffset] = hwcritValue
                        Logger.log(TAG, LogLevel.INFO, "Day $dayOffset hwcrit: $hwcritValue")
                        savePartialData(metadata, context)
                    }

                    // 3. Get wstar_bsratio value at 14:00
                    val wstarValue = extractValueAtTime(metadata, targetDate, "wstar_bsratio", 14 * 3600)
                    if (!wstarValue.isNaN()) {
                        wstarBsratioMaxByDay[dayOffset] = wstarValue
                        Logger.log(TAG, LogLevel.INFO, "Day $dayOffset wstar: $wstarValue")
                        savePartialData(metadata, context)
                    }

                    // 4. Get w_4000 values at 6h, 12h, and 18h
                    listOf(6, 12, 18).forEach { hour ->
                        val w4000Value = extractValueAtTime(metadata, targetDate, "w_4000", hour * 3600)
                        if (!w4000Value.isNaN()) {
                            val key = "${dayOffset}_$hour"
                            w4000MaxByDayAndHour[key] = w4000Value
                            Logger.log(TAG, LogLevel.INFO, "Day $dayOffset w4000@${hour}h: $w4000Value")
                            savePartialData(metadata, context)
                        }
                    }

                } catch (e: Exception) {
                    Logger.log(TAG, LogLevel.ERROR, "Exception processing day $dayOffset: ${e.message}", e)
                    // Continue with other days
                }
            }

            Logger.log(TAG, LogLevel.INFO, "Completed extraction: pfdtot=${pfdtotMaxByDay.size}, hwcrit=${hwcritMaxByDay.size}, wstar=${wstarBsratioMaxByDay.size}, w4000=${w4000MaxByDayAndHour.size}")
            return Result.success(Unit)

        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Failed to download SkySight data: ${e.message}", e)
            return Result.failure(e)
        }
    }

    /**
     * Save partial data to SharedPreferences so widget can update incrementally.
     */
    private fun savePartialData(metadata: WindyMeteogramMetadata, context: Context) {
        try {
            val prefs = context.getSharedPreferences("windy_widget_prefs", Context.MODE_PRIVATE)
            val existingJson = prefs.getString("windy_meteogram_metadata", null)
            val existingMetadata = if (existingJson != null) {
                Json.decodeFromString<WindyMeteogramMetadata>(existingJson)
            } else {
                metadata
            }

            val updatedMetadata = existingMetadata.copy(
                pfdtotMaxByDay = pfdtotMaxByDay,
                hwcritMaxByDay = hwcritMaxByDay,
                wstarBsratioMaxByDay = wstarBsratioMaxByDay,
                w4000MaxByDayAndHour = w4000MaxByDayAndHour,
                pfdtotColorStops = pfdtotColorStops,
                hwcritColorStops = hwcritColorStops,
                wstarBsratioColorStops = wstarBsratioColorStops,
                w4000ColorStops = w4000ColorStops
            )

            val updatedJson = Json.encodeToString(updatedMetadata)
            prefs.edit().putString("windy_meteogram_metadata", updatedJson).apply()
            
            Logger.log(TAG, LogLevel.DEBUG, "Saved partial data, triggering widget update")
            
            // Trigger widget update so it displays new data progressively
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    MeteogramRepo.loadData(context)
                    MeteogramGlanceWidget().updateAll(context)
                } catch (e: Exception) {
                    Logger.log(TAG, LogLevel.ERROR, "Widget update error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Failed to save partial data: ${e.message}", e)
        }
    }

    // ========== HTTP Client ==========

    private fun createHttpClient(): HttpClient {
        return HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 30000
                requestTimeoutMillis = 30000
                socketTimeoutMillis = 30000
            }
        }
    }

    // ========== Authentication ==========

    private suspend fun login(email: String, password: String): Result<SkysightLoginResponse> {
        return try {
            Logger.log(TAG_API, LogLevel.INFO, "Attempting login for: ${email.take(3)}***")

            val httpClient = createHttpClient()
            val response: HttpResponse = httpClient.post("$BASE_URL/auth") {
                header("X-API-Key", API_KEY)
                header("Content-Type", "application/json")
                header("User-Agent", USER_AGENT)
                setBody("""{"username":"$email","password":"$password"}""")
            }

            val responseText = response.bodyAsText()
            Logger.log(TAG_API, LogLevel.DEBUG, "Login response received")

            val jsonObject = Json.parseToJsonElement(responseText).jsonObject
            val key = jsonObject["key"]?.jsonPrimitive?.content ?: throw Exception("Missing key")
            val validUntilStr = jsonObject["valid_until"]?.jsonPrimitive?.content ?: throw Exception("Missing valid_until")
            val validUntil = validUntilStr.toLongOrNull() ?: throw Exception("Invalid valid_until")
            val allowedRegions = jsonObject["allowed_regions"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            httpClient.close()
            Result.success(SkysightLoginResponse(key, validUntil, allowedRegions))

        } catch (e: Exception) {
            Logger.log(TAG_API, LogLevel.ERROR, "Login failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun getApiKey(email: String, password: String): Result<String> {
        // Check cached key
        if (isApiKeyValid(cachedApiKey, cachedApiKeyValidUntil)) {
            Logger.log(TAG_API, LogLevel.DEBUG, "Using cached API key")
            return Result.success(cachedApiKey!!)
        }

        Logger.log(TAG_API, LogLevel.DEBUG, "Getting fresh API key")
        val loginResult = login(email, password)
        if (loginResult.isSuccess) {
            val loginData = loginResult.getOrThrow()
            cachedApiKey = loginData.key
            cachedApiKeyValidUntil = loginData.valid_until
            Logger.log(TAG_API, LogLevel.DEBUG, "Cached new API key")
            return Result.success(loginData.key)
        } else {
            cachedApiKey = null
            cachedApiKeyValidUntil = null
            return Result.failure(loginResult.exceptionOrNull() ?: Exception("Login failed"))
        }
    }

    private fun isApiKeyValid(apiKey: String?, validUntil: Long?): Boolean {
        if (apiKey.isNullOrEmpty() || validUntil == null) return false
        val currentTime = Clock.System.now().epochSeconds
        return validUntil > (currentTime + 120) // 2 minute buffer
    }

    // ========== Data URLs ==========

    private suspend fun getLayerDataUrls(
        email: String,
        password: String,
        regionId: String,
        layerId: String,
        date: String
    ): Result<List<SkysightDataUrl>> {
        Logger.log(TAG_API, LogLevel.DEBUG, "Getting data URLs for $layerId on $date")

        val apiKeyResult = getApiKey(email, password)
        if (apiKeyResult.isFailure) {
            return Result.failure(apiKeyResult.exceptionOrNull() ?: Exception("API key failed"))
        }

        val apiKey = apiKeyResult.getOrThrow()
        val localDate = LocalDate.parse(date)
        val timestamp = LocalDateTime(localDate, LocalTime(0, 0, 0)).toInstant(TimeZone.UTC).epochSeconds

        val httpClient = createHttpClient()

        return try {
            val response = httpClient.get("$BASE_URL/data") {
                parameter("region_id", regionId)
                parameter("layer_ids", layerId)
                parameter("from_time", timestamp.toString())
                header("X-API-Key", apiKey)
                header("User-Agent", USER_AGENT)
            }

            if (response.status.value == 200) {
                val responseText = response.bodyAsText()
                val dataUrls = Json.decodeFromString<List<SkysightDataUrl>>(responseText)
                Logger.log(TAG_API, LogLevel.DEBUG, "Got ${dataUrls.size} URLs for $layerId")
                Result.success(dataUrls)
            } else {
                Logger.log(TAG_API, LogLevel.ERROR, "HTTP ${response.status.value} getting $layerId URLs")
                Result.failure(Exception("HTTP ${response.status.value}"))
            }
        } catch (e: ConnectTimeoutException) {
            Logger.log(TAG_API, LogLevel.ERROR, "Connection timeout for $layerId")
            Result.failure(e)
        } catch (e: SocketTimeoutException) {
            Logger.log(TAG_API, LogLevel.ERROR, "Socket timeout for $layerId")
            Result.failure(e)
        } catch (e: Exception) {
            Logger.log(TAG_API, LogLevel.ERROR, "Error getting $layerId URLs: ${e.message}")
            Result.failure(e)
        } finally {
            httpClient.close()
        }
    }

    // ========== NetCDF Download & Extraction ==========

    private suspend fun downloadNetCdfAndExtract(
        metadata: WindyMeteogramMetadata,
        layerId: String,
        date: String
    ): Result<Float> {
        Logger.log(TAG, LogLevel.DEBUG, "Downloading $layerId for $date")

        val email = metadata.skysightEmail
        val password = metadata.skysightPassword
        if (email.isNullOrEmpty() || password.isNullOrEmpty()) {
            return Result.failure(Exception("No SkySight credentials"))
        }

        val regionId = metadata.skysightRegion?.takeIf { it.isNotEmpty() } ?: "EUROPE"

        val urlsResult = getLayerDataUrls(email, password, regionId, layerId, date)
        if (urlsResult.isFailure) {
            return Result.failure(urlsResult.exceptionOrNull() ?: Exception("Failed to get URLs"))
        }

        val dataUrls = urlsResult.getOrThrow()
        if (dataUrls.isEmpty()) {
            return Result.failure(Exception("No data URLs for $layerId"))
        }

        // Find noon URL
        val noonTimestamp = 12 * 3600
        val noonUrl = dataUrls.minByOrNull { kotlin.math.abs(it.time % 86400 - noonTimestamp) }
            ?: return Result.failure(Exception("No noon URL for $layerId"))

        val downloadResult = downloadNetCdfFile(email, password, noonUrl.link, layerId, noonUrl.time)
        if (downloadResult.isFailure) {
            return Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
        }

        val file = downloadResult.getOrThrow()
        return try {
            val windowKm = if (layerId == "w_4000") metadata.waveWindow ?: 200 else metadata.thermalWindow ?: 100
            val maxValue = extractMaxValue(file.absolutePath, layerId, metadata.latitude, metadata.longitude, windowKm)
            file.delete()
            Result.success(maxValue)
        } catch (e: Exception) {
            file.delete()
            Result.failure(e)
        }
    }

    private suspend fun extractValueAtTime(
        metadata: WindyMeteogramMetadata,
        date: String,
        layerId: String,
        targetTimeSeconds: Int
    ): Float {
        Logger.log(TAG, LogLevel.DEBUG, "Extracting $layerId at ${targetTimeSeconds/3600}:00 on $date")

        try {
            val email = metadata.skysightEmail ?: return Float.NaN
            val password = metadata.skysightPassword ?: return Float.NaN
            val regionId = metadata.skysightRegion ?: "EUROPE"

            val urlsResult = getLayerDataUrls(email, password, regionId, layerId, date)
            if (urlsResult.isFailure) return Float.NaN

            val allUrls = urlsResult.getOrThrow()
            val targetUrl = allUrls.find { url ->
                val urlTimeFromDayStart = url.time % 86400
                kotlin.math.abs(urlTimeFromDayStart - targetTimeSeconds) < 1800
            } ?: return Float.NaN

            val downloadResult = downloadNetCdfFile(email, password, targetUrl.link, layerId, targetUrl.time)
            if (downloadResult.isFailure) return Float.NaN

            val file = downloadResult.getOrThrow()
            return try {
                val windowKm = if (layerId == "w_4000") metadata.waveWindow ?: 200 else metadata.thermalWindow ?: 100
                val value = extractMaxValue(file.absolutePath, layerId, metadata.latitude, metadata.longitude, windowKm)
                file.delete()
                value
            } catch (e: Exception) {
                file.delete()
                Float.NaN
            }

        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Error extracting $layerId: ${e.message}")
            return Float.NaN
        }
    }

    private suspend fun downloadNetCdfFile(
        email: String,
        password: String,
        url: String,
        layerId: String,
        timestamp: Long
    ): Result<File> {
        return try {
            val apiKeyResult = getApiKey(email, password)
            if (apiKeyResult.isFailure) {
                return Result.failure(apiKeyResult.exceptionOrNull() ?: Exception("API key failed"))
            }

            val fileKey = "${layerId}_${timestamp}"
            val tempFile = File.createTempFile(fileKey, ".nc")

            val downloadRequest = org.mountaincircles.app.network.DownloadRequest(
                url = url,
                filePath = tempFile.absolutePath,
                headers = mapOf("Authorization" to "Bearer ${apiKeyResult.getOrThrow()}")
            )

            val downloadManager = org.mountaincircles.app.network.createDownloadManager()
            val downloadResult = downloadManager.download(downloadRequest) { }

            if (downloadResult.isSuccess) {
                Logger.log(TAG, LogLevel.DEBUG, "Downloaded $layerId: ${tempFile.length()} bytes")
                Result.success(tempFile)
            } else {
                tempFile.delete()
                Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
            }

        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Download error: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun extractMaxValue(
        filePath: String,
        layerName: String,
        centerLat: Double,
        centerLon: Double,
        windowKm: Int
    ): Float {
        val windowDegrees = windowKm / 111.0

        try {
            val variables = NetCDFReaderV2.readNetCDFFile(filePath)
            val dataVariable = variables.find { it.name == layerName }
                ?: throw Exception("$layerName not found in NetCDF")

            val latVariable = variables.find { it.name == "lat" || it.name == "latitude" }
                ?: throw Exception("Latitude not found")
            val lonVariable = variables.find { it.name == "lon" || it.name == "longitude" }
                ?: throw Exception("Longitude not found")

            val dimensions = NetCDFReaderV2.readNetCDFDimensions(filePath)
            val latCoords = NetCDFReaderV2.readVariableData(filePath, latVariable, dimensions)?.map { it.toDouble() }
                ?: throw Exception("Could not read lat coords")
            val lonCoords = NetCDFReaderV2.readVariableData(filePath, lonVariable, dimensions)?.map { it.toDouble() }
                ?: throw Exception("Could not read lon coords")

            val latIndices = latCoords.withIndex()
                .filter { (_, lat) -> lat >= centerLat - windowDegrees && lat <= centerLat + windowDegrees }
                .map { it.index }
            val lonIndices = lonCoords.withIndex()
                .filter { (_, lon) -> lon >= centerLon - windowDegrees && lon <= centerLon + windowDegrees }
                .map { it.index }

            if (latIndices.isEmpty() || lonIndices.isEmpty()) {
                return Float.NaN
            }

            val submatrix = NetCDFReaderV2.readVariableDataSelective(
                filePath, dataVariable, dimensions, latIndices, lonIndices, latCoords.size, lonCoords.size
            ) ?: throw Exception("Could not read data")

            val parsedData = SkysightTilingControllerV2Tiles.parseSubmatrixData(submatrix, dataVariable.attributes)

            var maxValue = Float.NaN
            for (row in parsedData) {
                for (value in row) {
                    if (!value.isNaN() && (maxValue.isNaN() || value > maxValue)) {
                        maxValue = value
                    }
                }
            }

            return maxValue

        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Extract error: ${e.message}")
            return Float.NaN
        }
    }

    // ========== Layer Info ==========

    private suspend fun fetchLayers(metadata: WindyMeteogramMetadata): Result<List<SkysightLayer>> {
        try {
            val region = metadata.skysightRegion ?: "EUROPE"
            Logger.log(TAG, LogLevel.DEBUG, "Fetching layers for region: $region")

            val apiKeyResult = getApiKey(metadata.skysightEmail!!, metadata.skysightPassword!!)
            if (apiKeyResult.isFailure) {
                return Result.failure(apiKeyResult.exceptionOrNull() ?: Exception("API key failed"))
            }

            val httpClient = HttpClient {
                install(HttpTimeout)
                install(ContentNegotiation) { json() }
            }

            val response = httpClient.get("$BASE_URL/layers") {
                header("X-API-Key", apiKeyResult.getOrThrow())
                parameter("region_id", region)
                header("User-Agent", USER_AGENT)
            }

            if (response.status.value == 200) {
                val responseText = response.bodyAsText()
                val layersArray = Json.parseToJsonElement(responseText).jsonArray

                val layers = layersArray.map { layerElement ->
                    val layerObj = layerElement.jsonObject
                    val legendObj = layerObj["legend"]?.jsonObject

                    val colors = legendObj?.get("colors")?.jsonArray?.map { colorElement ->
                        val colorObj = colorElement.jsonObject
                        LayerColorStop(
                            name = colorObj["name"]?.jsonPrimitive?.content ?: "",
                            value = colorObj["value"]?.jsonPrimitive?.content ?: "",
                            color = colorObj["color"]?.jsonArray?.map { it.jsonPrimitive.content.toIntOrNull() ?: 0 } ?: emptyList()
                        )
                    } ?: emptyList()

                    val legend = LayerLegend(
                        color_mode = legendObj?.get("color_mode")?.jsonPrimitive?.content ?: "",
                        units = legendObj?.get("units")?.jsonPrimitive?.content ?: "",
                        unit_type = legendObj?.get("unit_type")?.jsonPrimitive?.content ?: "",
                        units_scale_factor = legendObj?.get("units_scale_factor")?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                        colors = colors
                    )

                    SkysightLayer(
                        name = layerObj["name"]?.jsonPrimitive?.content ?: "",
                        legend = legend,
                        projection = layerObj["projection"]?.jsonPrimitive?.content ?: "",
                        data_type = layerObj["data_type"]?.jsonPrimitive?.content ?: "",
                        id = layerObj["id"]?.jsonPrimitive?.content ?: "",
                        description = layerObj["description"]?.jsonPrimitive?.content ?: ""
                    )
                }

                httpClient.close()
                Logger.log(TAG, LogLevel.DEBUG, "Fetched ${layers.size} layers")
                return Result.success(layers)
            } else {
                httpClient.close()
                return Result.failure(Exception("HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Logger.log(TAG, LogLevel.ERROR, "Fetch layers error: ${e.message}")
            return Result.failure(e)
        }
    }
}
