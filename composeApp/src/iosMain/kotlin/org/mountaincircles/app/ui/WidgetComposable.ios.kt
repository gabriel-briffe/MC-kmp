package org.mountaincircles.app.ui

/**
 * iOS implementation of map view capture for widget
 * iOS doesn't support home screen widgets like Android, so this is a no-op
 */
actual suspend fun captureCurrentMapView(context: Any?) {
    // iOS doesn't support home screen widgets, so this is a no-op
    // The function exists for cross-platform compatibility
}

actual suspend fun captureWindyMeteogramPoint(context: Any?) {
    // iOS doesn't support home screen widgets, so this is a no-op
    // The function exists for cross-platform compatibility
}

actual suspend fun fetchLocationName(latitude: Double, longitude: Double): String {
    // iOS doesn't support home screen widgets, so this is a no-op
    // The function exists for cross-platform compatibility
    return "Unknown"
}

actual suspend fun handleSkySightCredentialsForWidget(context: Any?, includeSkySight: Boolean, skySightState: org.mountaincircles.app.modules.skysight.logic.data.SkysightState?) {
    // iOS doesn't support home screen widgets, so this is a no-op
    // The function exists for cross-platform compatibility
}

actual suspend fun checkSkySightCredentialsInWidgetMetadata(context: Any?): Boolean {
    // iOS doesn't support home screen widgets, so this is a no-op
    // The function exists for cross-platform compatibility
    return false
}

actual suspend fun getSkySightWindowValues(context: Any?): Pair<Int, Int> {
    // iOS doesn't support home screen widgets, so this is a no-op
    // The function exists for cross-platform compatibility
    return Pair(100, 200) // Default values
}

actual suspend fun saveSkySightWindowValues(context: Any?, thermalWindow: Int, waveWindow: Int) {
    // iOS doesn't support home screen widgets, so this is a no-op
    // The function exists for cross-platform compatibility
}

actual suspend fun getWidgetLocationInfo(context: Any?): Pair<String?, Pair<Double, Double>?> {
    // iOS doesn't support home screen widgets, so this is a no-op
    // The function exists for cross-platform compatibility
    return Pair(null, null)
}