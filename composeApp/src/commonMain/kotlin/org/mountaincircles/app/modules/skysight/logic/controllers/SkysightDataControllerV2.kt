package org.mountaincircles.app.modules.skysight.logic.controllers

import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.modules.skysight.logic.data.TimePair
import org.mountaincircles.app.modules.skysight.logic.data.SkysightDataUrl
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Clean Data Controller V2 - Handles NetCDF data management
 * Self-contained data operations for the V2 pipeline
 */
object SkysightDataControllerV2 {

    /**
     * Ensure NetCDF file is available locally (check + download if needed)
     * This is the complete data availability pipeline
     */
    suspend fun ensureNetCDFFileAvailable(
        module: SkysightModule,
        layerId: String,
        date: LocalDate,
        timePair: TimePair
    ): Unit {
        Logger.log("SKYSIGHT_DATA_V2", LogLevel.INFO, "Ensuring NetCDF file available for $layerId on $date at ${timePair.display}")

        // Step 1: Construct file key for the requested data
        val fileKey = constructDataFileKey(layerId, date, timePair.hour, timePair.minute)
        Logger.log("SKYSIGHT_DATA_V2", LogLevel.DEBUG, "Constructed file key: $fileKey")

        // Step 2: Check if file exists locally
        val exists = fileExistsLocally(module, fileKey)
        if (exists) {
            Logger.log("SKYSIGHT_DATA_V2", LogLevel.INFO, "File already exists locally")
            return // File is available
        }

        Logger.log("SKYSIGHT_DATA_V2", LogLevel.INFO, "File not found locally, initiating download")

        // Step 3: Ensure data URLs are cached
        ensureDataUrlsAvailable(module, layerId, date)

        // Step 4: Find the specific URL for this timestamp
        val dataUrl = findDataUrlForTimestamp(module, layerId, date, constructTimestamp(date, timePair.hour, timePair.minute))
        if (dataUrl == null) {
            Logger.log("SKYSIGHT_DATA_V2", LogLevel.ERROR, "No data URL found for timestamp")
            // Handle error - throw exception or return failure
            return
        }

        Logger.log("SKYSIGHT_DATA_V2", LogLevel.INFO, "Found data URL: ${dataUrl.link}")

        // Step 5: Download the file
        module.updateState { it.copy(isDownloading = true) }
        downloadNetCDFFile(module, fileKey, dataUrl.link)

        Logger.log("SKYSIGHT_DATA_V2", LogLevel.INFO, "NetCDF file download completed")
    }

    /**
     * Construct file key for NetCDF storage
     */
    fun constructDataFileKey(layerId: String, date: LocalDate, hour: Int, minute: Int): String {
        val timestamp = constructTimestamp(date, hour, minute)
        return "${layerId}_${timestamp}"
    }

    /**
     * Construct UTC timestamp from date/time components
     */
    fun constructTimestamp(date: LocalDate, hour: Int, minute: Int): Long {
        val dateTime = kotlinx.datetime.LocalDateTime(date, kotlinx.datetime.LocalTime(hour, minute, 0))
        val instant = dateTime.toInstant(TimeZone.UTC)
        return instant.epochSeconds
    }


    /**
     * Ensure data URLs are available for the date
     */
    suspend fun ensureDataUrlsAvailable(
        module: SkysightModule,
        layerId: String,
        date: LocalDate
    ): Unit {
        if (!module.hasLayerDataUrls(layerId, date.toString())) {
            Logger.log("SKYSIGHT_DATA_V2", LogLevel.INFO, "Fetching URLs for layer $layerId on ${date.toString()}")
            fetchLayerDataUrls(module, layerId, date.toString())
        } else {
            Logger.log("SKYSIGHT_DATA_V2", LogLevel.DEBUG, "URLs already cached for layer $layerId on ${date.toString()}")
        }
    }

    /**
     * Fetch layer data URLs for a specific layer and date (V2 implementation)
     */
    suspend fun fetchLayerDataUrls(
        module: SkysightModule,
        layerId: String,
        date: String
    ) {
        // Always fetch fresh URLs with fresh API authentication (no caching)
        Logger.log("SKYSIGHT_DATA_V2", LogLevel.INFO, "Fetching fresh URLs for layer $layerId on date $date")

        // Clear any cached URLs for this layer/date to ensure freshness
        module.updateState { state ->
            val updatedLayers = state.availableLayers.map { layer ->
                if (layer.id == layerId) {
                    val updatedDataUrls = layer.dataUrls.toMutableMap()
                    updatedDataUrls.remove(date) // Remove cached URLs for this date
                    layer.copy(dataUrls = updatedDataUrls)
                } else {
                    layer
                }
            }
            state.copy(availableLayers = updatedLayers)
        }

        // Set downloading state for URL fetching
        module.updateState { it.copy(isDownloading = true) }

        try {
            val email = module.state.value.email
            val password = module.state.value.password
            val regionId = module.state.value.selectedRegion

            if (email.isEmpty() || password.isEmpty() || regionId.isEmpty()) {
                Logger.log("SKYSIGHT_DATA_V2", LogLevel.ERROR, "Missing credentials or region for URL fetch")
                return
            }

            val cachedApiKey = module.state.value.apiKey
            val cachedValidUntil = module.state.value.apiKeyValidUntil
            val result = module.getApiManager().getLayerDataUrls(email, password, regionId, layerId, date, cachedApiKey, cachedValidUntil)
            if (result.isSuccess) {
                val urls = result.getOrThrow()
                Logger.log("SKYSIGHT_DATA_V2", LogLevel.INFO, "Fetched ${urls.size} URLs for layer $layerId on date $date")
                updateLayerDataUrls(module, layerId, date, urls)
            } else {
                Logger.log("SKYSIGHT_DATA_V2", LogLevel.ERROR, "Failed to fetch URLs: ${result.exceptionOrNull()?.message}")
            }
        } finally {
            // Note: isDownloading is now cleared just before map refresh in tiling controller
        }
    }

    /**
     * Update layer data URLs in module state
     */
    suspend fun updateLayerDataUrls(module: SkysightModule, layerId: String, date: String, dataUrls: List<SkysightDataUrl>) {
        module.updateState { state ->
            val updatedLayers = state.availableLayers.map { layer ->
                if (layer.id == layerId) {
                    val updatedDataUrls = layer.dataUrls.toMutableMap()
                    updatedDataUrls[date] = dataUrls
                    layer.copy(dataUrls = updatedDataUrls)
                } else {
                    layer
                }
            }
            state.copy(availableLayers = updatedLayers)
        }
    }

    /**
     * Check if a file exists locally (V2 implementation)
     */
    suspend fun fileExistsLocally(module: SkysightModule, fileKey: String): Boolean {
        return try {
            module.storage.fileExists(fileKey)
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_DATA_V2", LogLevel.DEBUG, "File check failed: ${e.message}")
            false
        }
    }


    /**
     * Find data URL for specific timestamp
     */
    fun findDataUrlForTimestamp(
        module: SkysightModule,
        layerId: String,
        date: LocalDate,
        timestamp: Long
    ): SkysightDataUrl? {
        val urls = module.getLayerDataUrls(layerId, date.toString())
        Logger.log("SKYSIGHT_DATA_V2", LogLevel.DEBUG, "Searching for URL with timestamp $timestamp among ${urls.size} available URLs")

        val exactMatch = urls.find { it.time == timestamp }

        if (exactMatch == null) {
            Logger.log("SKYSIGHT_DATA_V2", LogLevel.ERROR, "No exact URL match found for timestamp $timestamp. Available timestamps: ${urls.map { it.time }}")
            return null
        }

        Logger.log("SKYSIGHT_DATA_V2", LogLevel.INFO, "Found exact match: ${exactMatch.link.substringAfterLast('/')}")
        return exactMatch
    }

    /**
     * Download NetCDF file from URL
     */
    suspend fun downloadNetCDFFile(
        module: SkysightModule,
        fileKey: String,
        url: String
    ): Unit {
        Logger.log("SKYSIGHT_DATA_V2", LogLevel.INFO, "Downloading file: $fileKey from $url")

        try {
            val result = module.getApiManager().downloadNetCDFFile(
                email = module.state.value.email,
                password = module.state.value.password,
                storage = module.storage,
                fileKey = fileKey,
                url = url,
                cachedApiKey = module.state.value.apiKey,
                cachedValidUntil = module.state.value.apiKeyValidUntil
            )

            if (result.isSuccess) {
                Logger.log("SKYSIGHT_DATA_V2", LogLevel.INFO, "Download successful: $fileKey")
            } else {
                Logger.log("SKYSIGHT_DATA_V2", LogLevel.ERROR, "Download failed for $fileKey: ${result.exceptionOrNull()?.message}")
                throw result.exceptionOrNull() ?: Exception("Download failed")
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_DATA_V2", LogLevel.ERROR, "Download error for $fileKey: ${e.message}")
            throw e
        }
    }
}