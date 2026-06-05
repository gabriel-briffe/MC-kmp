package org.mountaincircles.app.modules.skysight.logic

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import kotlinx.datetime.*

/**
 * Utility functions for Skysight module operations
 */
object SkysightUtils {

    /**
     * Get the timestamp floored to 10 minutes before the current time.
     * This represents the latest available realtime data (10 minutes ago, rounded down to 10-minute intervals).
     *
     * @return Instant representing the floored 10 minutes ago timestamp
     */
    fun getFlooredTenMinutesAgoTimestamp(): Instant {
        // Get current UTC time
        val now = Clock.System.now()

        // Calculate 10 minutes ago
        val tenMinutesAgo = now.minus(10, DateTimeUnit.MINUTE, TimeZone.UTC)
        val tenMinutesAgoDateTime = tenMinutesAgo.toLocalDateTime(TimeZone.UTC)

        // Floor minutes to nearest 10-minute interval
        val flooredMinute = (tenMinutesAgoDateTime.minute / 10) * 10

        // Create floored datetime
        val flooredDateTime = LocalDateTime(
            year = tenMinutesAgoDateTime.year,
            monthNumber = tenMinutesAgoDateTime.monthNumber,
            dayOfMonth = tenMinutesAgoDateTime.dayOfMonth,
            hour = tenMinutesAgoDateTime.hour,
            minute = flooredMinute,
            second = 0,
            nanosecond = 0
        )

        return flooredDateTime.toInstant(TimeZone.UTC)
    }

    /**
     * Extract viewport bounds from camera state
     */
    fun extractViewportBounds(cameraState: org.maplibre.compose.camera.CameraState): List<Double> {
        try {
            val projection = cameraState.projection
            if (projection == null) {
                Logger.log("SKYSIGHT_MODULE", LogLevel.WARN, "Camera projection not available")
                // Fallback to small bounds around camera center
                val centerLat = cameraState.position.target.latitude
                val centerLng = cameraState.position.target.longitude
                return listOf(centerLng - 0.1, centerLat - 0.1, centerLng + 0.1, centerLat + 0.1)
            }

            // Get the exact visible region from MapLibre
            val visibleRegion = projection.queryVisibleRegion()

            // For skysight data, we'll use a bounding box that encompasses all four corners
            val lngs = listOf(
                visibleRegion.farLeft.longitude,
                visibleRegion.farRight.longitude,
                visibleRegion.nearLeft.longitude,
                visibleRegion.nearRight.longitude
            )
            val lats = listOf(
                visibleRegion.farLeft.latitude,
                visibleRegion.farRight.latitude,
                visibleRegion.nearLeft.latitude,
                visibleRegion.nearRight.latitude
            )

            val west = lngs.minOrNull() ?: visibleRegion.farLeft.longitude
            val east = lngs.maxOrNull() ?: visibleRegion.farRight.longitude
            val south = lats.minOrNull() ?: visibleRegion.nearLeft.latitude
            val north = lats.maxOrNull() ?: visibleRegion.farLeft.latitude

            return listOf(west, south, east, north)

        } catch (e: Exception) {
            Logger.log("SKYSIGHT_MODULE", LogLevel.ERROR, "Failed to get visible region: ${e.message}", e)
            // Fallback to small bounds around camera center
            val centerLat = cameraState.position.target.latitude
            val centerLng = cameraState.position.target.longitude
            return listOf(centerLng - 0.1, centerLat - 0.1, centerLng + 0.1, centerLat + 0.1)
        }
    }

    /**
     * Get color for a value based on color stops
     */
    fun getColorForValue(value: Float, colorStops: List<org.mountaincircles.app.modules.skysight.logic.data.LayerColorStop>): androidx.compose.ui.graphics.Color {
        if (colorStops.isEmpty()) return androidx.compose.ui.graphics.Color.Black
        if (colorStops.size == 1) {
            val color = colorStops[0].color
            return androidx.compose.ui.graphics.Color(color[0], color[1], color[2])
        }

        // Parse color stop values and sort by value
        val stops = colorStops.map { stop ->
            val stopValue = stop.value.toFloatOrNull() ?: 0f
            val color = stop.color
            Pair(stopValue, androidx.compose.ui.graphics.Color(color[0], color[1], color[2]))
        }.sortedBy { it.first }

        val floatValue = value.toFloat()

        // For values greater than or equal to the last color value, use the last color
        if (floatValue >= stops.last().first) {
            return stops.last().second
        }

        // Find the appropriate color: value between this color value and the next gets this color
        for (i in 0 until stops.size - 1) {
            val currentStop = stops[i]
            val nextStop = stops[i + 1]

            // If value is between current stop value and next stop value (inclusive of current, exclusive of next)
            if (floatValue >= currentStop.first && floatValue < nextStop.first) {
                return currentStop.second
            }
        }

        // Fallback to transparent for values below the first stop (out of range)
        return androidx.compose.ui.graphics.Color(0x00000000) // Fully transparent
    }


    /**
     * Clean up old layer URLs that are older than 1 day
     */
    fun cleanupOldLayerUrls(layers: List<org.mountaincircles.app.modules.skysight.logic.data.SkysightLayer>): List<org.mountaincircles.app.modules.skysight.logic.data.SkysightLayer> {
        val currentDate = java.time.LocalDate.now()
        val cutoffDate = currentDate.minusDays(1)
        val dateFormatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

        org.mountaincircles.app.logger.Logger.log("SKYSIGHT", org.mountaincircles.app.logger.LogLevel.INFO, "URL cleanup: current date=$currentDate, cutoff date=$cutoffDate")

        var totalUrlsBefore = 0
        var totalUrlsAfter = 0
        var cleanedUrlsCount = 0

        val cleanedLayers = layers.map { layer ->
            org.mountaincircles.app.logger.Logger.log("SKYSIGHT", org.mountaincircles.app.logger.LogLevel.DEBUG, "Layer ${layer.id}: dates available = ${layer.dataUrls.keys.joinToString(", ")}")

            totalUrlsBefore += layer.dataUrls.values.sumOf { it.size }

            val cleanedDataUrls = layer.dataUrls.filterKeys { dateString ->
                try {
                    val layerDate = java.time.LocalDate.parse(dateString, dateFormatter)
                    val keepDate = layerDate.isAfter(cutoffDate) || layerDate.isEqual(cutoffDate)
                    org.mountaincircles.app.logger.Logger.log("SKYSIGHT", org.mountaincircles.app.logger.LogLevel.DEBUG, "Layer ${layer.id}: date $dateString (${layerDate}) - keep=$keepDate")
                    if (!keepDate) {
                        cleanedUrlsCount += layer.dataUrls[dateString]?.size ?: 0
                    }
                    keepDate
                } catch (e: Exception) {
                    org.mountaincircles.app.logger.Logger.log("SKYSIGHT", org.mountaincircles.app.logger.LogLevel.DEBUG, "Invalid date format in layer URLs: $dateString, keeping it")
                    true // Keep invalid dates to avoid losing data due to format issues
                }
            }

            totalUrlsAfter += cleanedDataUrls.values.sumOf { it.size }
            layer.copy(dataUrls = cleanedDataUrls)
        }

        org.mountaincircles.app.logger.Logger.log("SKYSIGHT", org.mountaincircles.app.logger.LogLevel.INFO, "URL cleanup: $totalUrlsBefore URLs before, $totalUrlsAfter URLs after, removed $cleanedUrlsCount old URLs")

        return cleanedLayers
    }
}