package org.mountaincircles.app.modules.skysight.logic

import kotlinx.datetime.LocalDate
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.controllers.SkysightDataControllerV2
import org.mountaincircles.app.io.getGlobalFileManager

/**
 * Utility functions for Skysight file system operations
 */
object SkysightFileManagement {

    /**
     * Check if NetCDF file exists locally
     */
    suspend fun checkNetCDFFileExistsLocally(module: SkysightModule, fileKey: String): Boolean {
        return SkysightDataControllerV2.fileExistsLocally(module, fileKey)
    }

    /**
     * Get local NetCDF file path for a file key
     */
    fun getLocalNetCDFFilePath(module: SkysightModule, fileKey: String): String {
        return module.getLocalFilePath(fileKey)
    }

    /**
     * Ensure NetCDF file is available locally, return path if it exists
     */
    suspend fun ensureNetCDFFileAvailable(
        module: SkysightModule,
        fileKey: String,
        layerId: String,
        date: LocalDate
    ): String? {
        if (checkNetCDFFileExistsLocally(module, fileKey)) {
            Logger.log("SKYSIGHT_FILE", LogLevel.INFO, "File exists locally: $fileKey")
            return getLocalNetCDFFilePath(module, fileKey)
        }

        Logger.log("SKYSIGHT_FILE", LogLevel.INFO, "File not found locally: $fileKey")
        return null
    }

    /**
     * Validate that a file path exists and is readable
     */
    fun validateFilePath(filePath: String): Boolean {
        return try {
            val fileData = getGlobalFileManager().readBytes(filePath)
            fileData != null && fileData.isNotEmpty()
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_FILE", LogLevel.ERROR, "Failed to validate file path: $filePath", e)
            false
        }
    }
}