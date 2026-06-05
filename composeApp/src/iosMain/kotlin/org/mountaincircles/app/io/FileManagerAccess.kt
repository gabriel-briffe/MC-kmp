package org.mountaincircles.app.io

/**
 * iOS implementation of global FileManager access
 */

private var globalFileManager: FileManager? = null

/**
 * Get the global FileManager instance
 */
actual fun getGlobalFileManager(): FileManager {
    return globalFileManager ?: FileManager().also { globalFileManager = it }
}

/**
 * Set the global FileManager instance
 */
actual fun setGlobalFileManager(manager: FileManager) {
    globalFileManager = manager
}

/**
 * Get the global NetworkMonitor instance (Android only)
 * Returns null on non-Android platforms
 */
actual fun getGlobalNetworkMonitor(): org.mountaincircles.app.network.NetworkMonitor? {
    return null // Not available on iOS
}

/**
 * Set the global NetworkMonitor instance (called from MainActivity on Android)
 * No-op on non-Android platforms
 */
actual fun setGlobalNetworkMonitor(monitor: org.mountaincircles.app.network.NetworkMonitor) {
    // No-op on iOS
}

/**
 * Check if the app is currently in the background
 * Returns false on non-Android platforms
 */
actual fun isAppInBackground(): Boolean {
    return false // Not applicable on iOS
}
