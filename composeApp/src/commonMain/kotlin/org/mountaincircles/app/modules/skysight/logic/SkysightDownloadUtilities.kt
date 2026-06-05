package org.mountaincircles.app.modules.skysight.logic

import kotlinx.datetime.LocalDate
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.controllers.SkysightDataControllerV2
import org.mountaincircles.app.modules.skysight.logic.data.SkysightDataUrl

/**
 * Utility functions for Skysight URL management and downloading
 */
object SkysightDownloadUtilities {

    /**
     * Ensure data URLs are available for a layer and date
     */
    suspend fun ensureDataUrlsAvailable(
        module: SkysightModule,
        layerId: String,
        date: LocalDate
    ) {
        if (!module.hasLayerDataUrls(layerId, date.toString())) {
            Logger.log("SKYSIGHT_URL", LogLevel.INFO, "Fetching URLs for layer $layerId on ${date.toString()}")
            SkysightDataControllerV2.fetchLayerDataUrls(module, layerId, date.toString())
        } else {
            Logger.log("SKYSIGHT_URL", LogLevel.DEBUG, "URLs already cached for layer $layerId on ${date.toString()}")
        }
    }

    /**
     * Find data URL for specific timestamp - exact match only, no fallbacks
     */
    fun findDataUrlForTimestamp(
        module: SkysightModule,
        layerId: String,
        date: LocalDate,
        timestamp: Long
    ): SkysightDataUrl? {
        val urls = module.getLayerDataUrls(layerId, date.toString())

        val exactMatch = urls.find { it.time == timestamp }

        if (exactMatch == null) {
            Logger.log("SKYSIGHT_URL", LogLevel.ERROR, "No exact URL match found for timestamp $timestamp")
            Logger.log("SKYSIGHT_URL", LogLevel.ERROR, "Available timestamps: ${urls.map { it.time }.sorted()}")
            Logger.log("SKYSIGHT_URL", LogLevel.ERROR, "Available filenames: ${urls.map { it.link.substringAfterLast('/').substringBefore('?') }}")
            return null // No fallback - fail if no exact match
        }

        Logger.log("SKYSIGHT_URL", LogLevel.INFO, "Found exact match: time=${exactMatch.time}, filename=${exactMatch.link.substringAfterLast('/').substringBefore('?')}")
        return exactMatch
    }

    /**
     * Download NetCDF file and return file path on success
     */
    suspend fun downloadNetCDFFile(
        module: SkysightModule,
        fileKey: String,
        url: String
    ): Result<String> {
        return try {
            Logger.log("SKYSIGHT_DOWNLOAD", LogLevel.INFO, "Downloading file: $fileKey from $url")

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
                val filePath = module.getLocalFilePath(fileKey)
                Logger.log("SKYSIGHT_DOWNLOAD", LogLevel.INFO, "Download successful: $fileKey")
                Result.success(filePath)
            } else {
                Logger.log("SKYSIGHT_DOWNLOAD", LogLevel.ERROR, "Download failed for $fileKey: ${result.exceptionOrNull()?.message}")
                Result.failure(result.exceptionOrNull() ?: Exception("Download failed"))
            }
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_DOWNLOAD", LogLevel.ERROR, "Download error for $fileKey: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Validate downloaded file integrity
     */
    fun validateDownloadedFile(filePath: String): Boolean {
        return try {
            val fileData = org.mountaincircles.app.io.getGlobalFileManager().readBytes(filePath)
            fileData != null && fileData.isNotEmpty() && fileData.size > 100 // Minimum size check
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_DOWNLOAD", LogLevel.ERROR, "File validation failed for $filePath: ${e.message}")
            false
        }
    }
}