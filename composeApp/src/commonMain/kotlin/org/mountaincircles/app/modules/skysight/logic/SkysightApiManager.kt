package org.mountaincircles.app.modules.skysight.logic

import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeout.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.logic.data.SkysightLoginResponse
import org.mountaincircles.app.modules.skysight.logic.data.SkysightLayer
import org.mountaincircles.app.modules.skysight.logic.data.SkysightDataUrl

/**
 * Dedicated API manager for Skysight services
 * Handles all HTTP communication with the Skysight API
 */
class SkysightApiManager {

    companion object {
        private const val BASE_URL = "https://skysight.io/api"
        private const val API_KEY = "MountainCircles"
        private const val USER_AGENT = "MountainCircles"
    }

    // Reactive state observation
    private var module: org.mountaincircles.app.modules.skysight.SkysightModule? = null

    /**
     * Set the module to observe for reactive state access
     */
    fun setModule(module: org.mountaincircles.app.modules.skysight.SkysightModule) {
        this.module = module
    }

    /**
     * Get an API key, using cached key if still valid, otherwise authenticate fresh
     */
    suspend fun getApiKey(): Result<String> {
        val currentState = module?.currentState ?: return Result.failure(Exception("Module not set"))

        Logger.log("SKYSIGHT_API", LogLevel.DEBUG, "getApiKey called with apiKey=${currentState.apiKey}, validUntil=${currentState.apiKeyValidUntil}")

        // Check if cached API key is still valid (at least 2 minutes before expiration)
        if (isApiKeyValid(currentState.apiKey, currentState.apiKeyValidUntil)) {
            Logger.log("SKYSIGHT_API", LogLevel.DEBUG, "Using cached API key (expires at ${currentState.apiKeyValidUntil})")
            return Result.success(currentState.apiKey!!)
        }

        // Cached key is invalid or missing, get fresh authentication
        Logger.log("SKYSIGHT_API", LogLevel.DEBUG, "Cached API key invalid or expired, getting fresh authentication")
        val loginResult = login(currentState.email, currentState.password)
        if (loginResult.isSuccess) {
            val loginData = loginResult.getOrThrow()
            // Update module state with fresh API key and expiration
            module?.updateState { it.copy(
                apiKey = loginData.key,
                apiKeyValidUntil = loginData.valid_until
            )}
            Logger.log("SKYSIGHT_API", LogLevel.DEBUG, "Updated module state with fresh API key (expires at ${loginData.valid_until})")
            return Result.success(loginData.key)
        } else {
            return Result.failure(loginResult.exceptionOrNull() ?: Exception("Login failed"))
        }
    }

    /**
     * Check if the cached API key is still valid (at least 2 minutes before expiration)
     */
    fun isApiKeyValid(apiKey: String?, validUntil: Long?): Boolean {
        if (apiKey.isNullOrEmpty() || validUntil == null) {
            Logger.log("SKYSIGHT_API", LogLevel.DEBUG, "API key validation failed: key=${apiKey?.take(10)}, validUntil=$validUntil")
            return false
        }

        val currentTime = kotlinx.datetime.Clock.System.now().epochSeconds
        val twoMinutesFromNow = currentTime + 120 // 2 minutes = 120 seconds

        val timeDiff = validUntil - currentTime
        val isValid = validUntil > twoMinutesFromNow
        Logger.log("SKYSIGHT_API", LogLevel.DEBUG, "API key validation: current=$currentTime, expires=$validUntil, time_diff=${timeDiff}s (${timeDiff/3600}h), 2min_buffer=$twoMinutesFromNow, valid=$isValid")
        return isValid
    }

    /**
     * Perform login authentication with Skysight API
     */
    suspend fun login(email: String, password: String): Result<SkysightLoginResponse> {
        return try {
            Logger.log("SKYSIGHT_API", LogLevel.INFO, "Attempting login for email: $email")

            val httpClient = createHttpClient()

            val response: HttpResponse = httpClient.post("$BASE_URL/auth") {
                header("X-API-Key", API_KEY)
                header("Content-Type", "application/json")
                header("User-Agent", USER_AGENT)
                setBody("""{"username":"$email","password":"$password"}""")
            }

            val responseText = response.bodyAsText()
            Logger.log("SKYSIGHT_API", LogLevel.DEBUG, "Login response: $responseText")

            // Parse JSON response
            val jsonElement = Json.parseToJsonElement(responseText)
            val jsonObject = jsonElement.jsonObject

            val key = jsonObject["key"]?.jsonPrimitive?.content ?: throw Exception("Missing key in response")
            val validUntilStr = jsonObject["valid_until"]?.jsonPrimitive?.content ?: throw Exception("Missing valid_until in response")
            val validUntil = validUntilStr.toLongOrNull() ?: throw Exception("Invalid valid_until format")
            val allowedRegionsJson = jsonObject["allowed_regions"]?.jsonArray ?: throw Exception("Missing allowed_regions in response")
            val allowedRegions = allowedRegionsJson.map { it.jsonPrimitive.content }

            val loginResponse = SkysightLoginResponse(
                key = key,
                valid_until = validUntil,
                allowed_regions = allowedRegions
            )

            httpClient.close()
            Result.success(loginResponse)

        } catch (e: Exception) {
            Logger.log("SKYSIGHT_API", LogLevel.ERROR, "Login request failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get available layers for a specific region
     * This authenticates first to get an API key, then requests the available layers
     */
    /**
     * Consolidated method to load available layers - handles auth, HTTP, parsing, state updates, and persistence
     */
    suspend fun loadAvailableLayers(module: org.mountaincircles.app.modules.skysight.SkysightModule, region: String): Result<List<org.mountaincircles.app.modules.skysight.logic.data.SkysightLayer>> {
        return try {
            Logger.log("SKYSIGHT_API", LogLevel.INFO, "Loading available layers for region: $region")

            val currentEmail = module.currentState.email
            val currentPassword = module.currentState.password

            if (currentEmail.isEmpty() || currentPassword.isEmpty()) {
                Logger.log("SKYSIGHT", LogLevel.ERROR, "Cannot load layers: missing credentials")
                module.updateState { it.copy(isLoadingLayers = false, hasError = true, errorMessage = "Missing credentials") }
                return Result.failure(Exception("Missing credentials"))
            }

            // Get API key (cached if valid, fresh if needed)
            val apiKeyResult = getApiKey()
            if (apiKeyResult.isFailure) {
                module.updateState { it.copy(isLoadingLayers = false, hasError = true, errorMessage = "Authentication failed") }
                return Result.failure(apiKeyResult.exceptionOrNull() ?: Exception("Failed to get API key"))
            }

            val apiKey = apiKeyResult.getOrThrow()

            // Now request available layers for the region
            val httpClient = createHttpClient()

            val response: HttpResponse = httpClient.get("$BASE_URL/layers") {
                header("X-API-Key", apiKey)
                parameter("region_id", region) // Updated parameter name
                header("User-Agent", USER_AGENT)
            }

            val responseText = response.bodyAsText()
            Logger.log("SKYSIGHT_API", LogLevel.DEBUG, "Layers response for $region: ${responseText.take(500)}...")

            // Parse JSON response - array of layer objects
            val jsonElement = Json.parseToJsonElement(responseText)
            val layersArray = jsonElement.jsonArray

            val layers = layersArray.map { layerElement ->
                val layerObj = layerElement.jsonObject
                val legendObj = layerObj["legend"]?.jsonObject ?: throw Exception("Missing legend in layer")

                // Parse colors array
                val colorsArray = legendObj["colors"]?.jsonArray ?: emptyList()
                val colors = colorsArray.map { colorElement ->
                    val colorObj = colorElement.jsonObject
                    org.mountaincircles.app.modules.skysight.logic.data.LayerColorStop(
                        name = colorObj["name"]?.jsonPrimitive?.content ?: "",
                        value = colorObj["value"]?.jsonPrimitive?.content ?: "",
                        color = colorObj["color"]?.jsonArray?.map { it.jsonPrimitive.content.toIntOrNull() ?: 0 } ?: emptyList()
                    )
                }

                val legend = org.mountaincircles.app.modules.skysight.logic.data.LayerLegend(
                    color_mode = legendObj["color_mode"]?.jsonPrimitive?.content ?: "",
                    units = legendObj["units"]?.jsonPrimitive?.content ?: "",
                    unit_type = legendObj["unit_type"]?.jsonPrimitive?.content ?: "",
                    units_scale_factor = legendObj["units_scale_factor"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                    colors = colors
                )

                org.mountaincircles.app.modules.skysight.logic.data.SkysightLayer(
                    name = layerObj["name"]?.jsonPrimitive?.content ?: "",
                    legend = legend,
                    projection = layerObj["projection"]?.jsonPrimitive?.content ?: "",
                    data_type = layerObj["data_type"]?.jsonPrimitive?.content ?: "",
                    id = layerObj["id"]?.jsonPrimitive?.content ?: "",
                    description = layerObj["description"]?.jsonPrimitive?.content ?: ""
                )
            }

            httpClient.close()

            // Success - update state and persist
            Logger.log("SKYSIGHT", LogLevel.INFO, "Loaded ${layers.size} layers for region $region: ${layers.joinToString(", ") { it.name }}")

            module.updateState { it.copy(
                availableLayers = layers,
                isLoadingLayers = false,
                hasError = false,
                errorMessage = null
            )}

            // Persist full layer objects as JSON
            try {
                val layersJson = Json.encodeToString(layers)
                module.settingPersistence.saveString("availableLayers", layersJson)
                Logger.log("SKYSIGHT", LogLevel.DEBUG, "Persisted ${layers.size} full layer objects")
            } catch (e: Exception) {
                Logger.log("SKYSIGHT", LogLevel.WARN, "Failed to persist full layer objects, falling back to names: ${e.message}")
                try {
                    module.settingPersistence.saveString("availableLayers", layers.joinToString(",") { it.name })
                } catch (e2: Exception) {
                    Logger.log("SKYSIGHT", LogLevel.ERROR, "Failed to persist layer names too: ${e2.message}")
                }
            }

            Result.success(layers)

        } catch (e: Exception) {
            Logger.log("SKYSIGHT_API", LogLevel.ERROR, "Failed to load layers for region $region: ${e.message}", e)
            module.updateState { it.copy(isLoadingLayers = false, hasError = true, errorMessage = e.message) }
            Result.failure(e)
        }
    }


    /**
     * Get layer data file URLs for a specific region, layer, and date
     *
     * @param email User email
     * @param password User password
     * @param regionId Region identifier (e.g., "EUROPE")
     * @param layerId Layer identifier
     * @param date Date in YYYY-MM-DD format
     * @return Result containing list of layer data URLs for the specified date
     */
    suspend fun getLayerDataUrls(
        email: String,
        password: String,
        regionId: String,
        layerId: String,
        date: String,
        cachedApiKey: String? = null,
        cachedValidUntil: Long? = null
    ): Result<List<SkysightDataUrl>> {
        return try {
            // Get API key (cached if valid, fresh if needed)
            val apiKeyResult = getApiKey()
            if (apiKeyResult.isFailure) {
                return Result.failure(apiKeyResult.exceptionOrNull() ?: Exception("Failed to get API key"))
            }

            val apiKey = apiKeyResult.getOrThrow()

            // Convert date string to Unix timestamp (seconds since epoch)
            Logger.log("MC_SKYSIGHT_API", LogLevel.INFO, "API Manager constructing timestamp from date: $date")
            val localDate = LocalDate.parse(date)
            val dateTime = LocalDateTime(localDate, LocalTime(0, 0, 0))
            Logger.log("MC_SKYSIGHT_API", LogLevel.DEBUG, "API Manager LocalDateTime: $dateTime")
            val instant = dateTime.toInstant(TimeZone.UTC)
            val timestamp = instant.epochSeconds
            Logger.log("MC_SKYSIGHT_API", LogLevel.INFO, "API Manager UTC timestamp for date lookup: $timestamp (start of day)")

            val httpClient = createHttpClient()

            val requestUrl = "$BASE_URL/data?region_id=$regionId&layer_ids=$layerId&from_time=${timestamp}"
            Logger.log("MC_SKYSIGHT_API", LogLevel.DEBUG, "Requesting layer data URLs: $requestUrl")

            val response = httpClient.get("$BASE_URL/data") {
                parameter("region_id", regionId)
                parameter("layer_ids", layerId)
                parameter("from_time", timestamp.toString())
                header("X-API-Key", apiKey)
                header("User-Agent", USER_AGENT)
            }

            if (response.status.value == 200) {
                val responseText = response.bodyAsText()
                Logger.log("MC_SKYSIGHT_API", LogLevel.DEBUG, "Layer data API response: $responseText")
                try {
                    val dataUrls = Json.decodeFromString<List<SkysightDataUrl>>(responseText)
                    Logger.log("MC_SKYSIGHT_API", LogLevel.INFO, "Successfully parsed ${dataUrls.size} layer data URLs")
                    Result.success(dataUrls)
                } catch (e: Exception) {
                    Result.failure(Exception("Failed to parse layer data response: ${e.message}"))
                }
            } else {
                val errorText = response.bodyAsText()
                Logger.log("MC_SKYSIGHT_API", LogLevel.ERROR, "API request failed with status ${response.status.value}: $errorText from $requestUrl")
                Result.failure(Exception("API request failed with status ${response.status.value}: $errorText"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download a NetCDF file from the provided URL
     * Follows the same authentication pattern as other API functions
     */
    suspend fun downloadNetCDFFile(
        email: String,
        password: String,
        storage: org.mountaincircles.app.modules.skysight.logic.SkysightStorage,
        fileKey: String,
        url: String,
        cachedApiKey: String? = null,
        cachedValidUntil: Long? = null
    ): Result<Unit> {
        return try {
            // Get API key (cached if valid, fresh if needed)
            val apiKeyResult = getApiKey()
            if (apiKeyResult.isFailure) {
                return Result.failure(apiKeyResult.exceptionOrNull() ?: Exception("Failed to get API key"))
            }

            // API key obtained successfully, proceed with download
            // The URL should already be pre-authenticated, but we ensure API key validity
            storage.downloadFile(fileKey, url)
            Logger.log("SKYSIGHT_API", LogLevel.INFO, "NetCDF file downloaded successfully: $fileKey from $url")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_API", LogLevel.ERROR, "NetCDF file download failed: ${e.message} from $url")
            Result.failure(e)
        }
    }

    /**
     * Download tile image data for any layer type (satellite, rain, etc.)
     */

    suspend fun downloadTileImage(
        layerType: String,
        zoom: Int,
        x: Int,
        y: Int,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        useRealtimeTimeouts: Boolean = false
    ): Result<ByteArray> {
        // Construct URL based on layer type
        val baseUrl = "https://skysight.io/api/$layerType/$zoom/$x/$y/$year/${month.toString().padStart(2, '0')}/${day.toString().padStart(2, '0')}/${hour.toString().padStart(2, '0')}${minute.toString().padStart(2, '0')}"

        // Add timestamp parameter for realtime layers to bust cache
        val url = if (layerType == "satellite" || layerType == "rain") {
            val timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            "$baseUrl?t=$timestamp"
        } else {
            baseUrl
        }

        return try {
            Logger.log("SKYSIGHT_API", LogLevel.DEBUG, "Downloading $layerType tile: zoom=$zoom, x=$x, y=$y, time=${year}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')} ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}")

            // Get API key
            val apiKeyResult = getApiKey()
            if (apiKeyResult.isFailure) {
                return Result.failure(apiKeyResult.exceptionOrNull() ?: Exception("Failed to get API key"))
            }
            val apiKey = apiKeyResult.getOrNull()!!

            Logger.log("SKYSIGHT_API", LogLevel.DEBUG, "$layerType tile URL: $url")

            // Create HTTP client and make request
            val httpClient = createHttpClient(useRealtimeTimeouts)
            val response = httpClient.get(url) {
                header("X-API-Key", apiKey)
                header("User-Agent", "MountainCircles/1.0")
                // Disable caching for realtime layers (satellite and rain)
                if (layerType == "satellite" || layerType == "rain") {
                    header("Cache-Control", "no-cache, no-store")
                    header("Pragma", "no-cache")
                }
            }

            if (response.status.value == 200) {
                val bytes = response.readBytes()
                Logger.log("SKYSIGHT_API", LogLevel.INFO, "$layerType tile downloaded successfully: ${bytes.size} bytes from $url")
                Result.success(bytes)
            } else {
                Logger.log("SKYSIGHT_API", LogLevel.ERROR, "$layerType tile download failed: HTTP ${response.status.value} from $url")
                Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}"))
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_API", LogLevel.ERROR, "$layerType tile download failed: ${e.message} from $url")
            Result.failure(e)
        }
    }

    /**
     * Download satellite image data directly as bytes (for local_satellite layer)
     */
    suspend fun downloadSatelliteImage(
        zoom: Int,
        x: Int,
        y: Int,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        useRealtimeTimeouts: Boolean = false
    ): Result<ByteArray> = downloadTileImage("satellite", zoom, x, y, year, month, day, hour, minute, useRealtimeTimeouts)

    /**
     * Create HTTP client with standard configuration
     * @param useRealtimeTimeouts Whether to use shorter timeouts for realtime layers (1s socket, 1s connect, 2s request)
     */
    private fun createHttpClient(useRealtimeTimeouts: Boolean = false): HttpClient {
        return HttpClient {
            install(HttpTimeout) {
                if (useRealtimeTimeouts) {
                    // Realtime timeouts: socket 1s, connect 1s, request 2s
                    socketTimeoutMillis = 10000
                    connectTimeoutMillis = 2000
                    requestTimeoutMillis = 30000
                } else {
                    // Standard timeouts: socket 10s, connect 1s, request 30s
                    socketTimeoutMillis = 10000
                    connectTimeoutMillis = 2000
                    requestTimeoutMillis = 30000
                }
            }
        }
    }
}