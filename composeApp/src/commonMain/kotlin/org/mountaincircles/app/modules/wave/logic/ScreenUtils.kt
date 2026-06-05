package org.mountaincircles.app.modules.wave.logic

/**
 * Screen utilities for wind vector downsampling
 */
object ScreenUtils {
    /**
     * Desired physical spacing between wind vectors in millimeters
     */
    const val LENGTH_MILLIMETER_INTERVAL_BETWEEN_POINTS = 10.0

    /**
     * Calculate geographic spacing for wind vectors based on viewport extent and screen size
     *
     * @param west Western boundary of viewport in degrees longitude
     * @param south Southern boundary of viewport in degrees latitude
     * @param east Eastern boundary of viewport in degrees longitude
     * @param north Northern boundary of viewport in degrees latitude
     * @param screenWidthMm Physical screen width in millimeters
     * @param screenHeightMm Physical screen height in millimeters
     * @param barbInterval Desired physical spacing between wind vectors in millimeters
     * @return Pair(geographicSpacingLng, geographicSpacingLat) - degrees between points
     */
    fun calculateGeographicSpacing(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        screenWidthMm: Double,
        screenHeightMm: Double,
        barbInterval: Double = LENGTH_MILLIMETER_INTERVAL_BETWEEN_POINTS
    ): Pair<Double, Double> {
        // Calculate geographic extent
        val lngSpan = east - west
        val latSpan = north - south

        // Calculate how many vectors fit on screen
        val maxVectorsHorizontal = (screenWidthMm / barbInterval).toInt()
        val maxVectorsVertical = (screenHeightMm / barbInterval).toInt()

        // Ensure at least 1 vector
        val safeMaxHorizontal = maxOf(1, maxVectorsHorizontal)
        val safeMaxVertical = maxOf(1, maxVectorsVertical)

        // Calculate geographic spacing (degrees between points)
        val lngSpacing = lngSpan / safeMaxHorizontal
        val latSpacing = latSpan / safeMaxVertical

        return Pair(lngSpacing, latSpacing)
    }
}
