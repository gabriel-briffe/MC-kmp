package org.mountaincircles.app.modules.skysight.logic.controllers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.logic.SkysightUtils
import org.mountaincircles.app.state.GlobalState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * Viewport and key helpers for SkysightTilingControllerV2.
 * Extracted for clearer separation of concerns.
 */
object SkysightTilingControllerV2Viewport {

    /**
     * Get current viewport bounds from camera
     */
    fun getViewportBounds(globalState: GlobalState): List<Double> {
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Extracting viewport bounds from camera state")
        val cameraState = globalState.currentCameraState.value
        if (cameraState == null) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Camera state is null, cannot extract viewport bounds")
            return emptyList()
        }
        val viewportBounds = SkysightUtils.extractViewportBounds(cameraState)
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Viewport bounds: [${viewportBounds.joinToString(", ")}]")
        return viewportBounds
    }

    /**
     * Calculate zIndex for tile layering
     */
    fun calculateTileZIndex(tileId: String): Float {
        return try {
            val parts = tileId.split('_')
            if (parts.size != 2) return 0f
            val latPart = parts[0]
            val lonPart = parts[1]
            val latMin = latPart.dropLast(1).toIntOrNull() ?: return 0f
            val lonMin = lonPart.dropLast(1).toIntOrNull() ?: return 0f
            val decimalPart = "${latMin}${lonMin}114"
            "0.$decimalPart".toFloatOrNull() ?: 0f
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Failed to calculate zIndex for tile $tileId: ${e.message}")
            0f
        }
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
        val dateTime = LocalDateTime(date, LocalTime(hour, minute, 0))
        val instant = dateTime.toInstant(TimeZone.UTC)
        return instant.epochSeconds
    }
}
