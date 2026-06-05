package org.mountaincircles.app.io

/**
 * Global FileManager access functions for modules
 * Uses expect/actual pattern for platform-specific implementations
 */

/**
 * Get the global FileManager instance
 * Must be called after MainActivity has initialized the FileManager
 */
expect fun getGlobalFileManager(): FileManager

/**
 * Set the global FileManager instance (called from MainActivity)
 */
expect fun setGlobalFileManager(manager: FileManager)

/**
 * Get the global NetworkMonitor instance (Android only)
 * Returns null on non-Android platforms
 */
expect fun getGlobalNetworkMonitor(): org.mountaincircles.app.network.NetworkMonitor?

/**
 * Set the global NetworkMonitor instance (called from MainActivity on Android)
 */
expect fun setGlobalNetworkMonitor(monitor: org.mountaincircles.app.network.NetworkMonitor)

/**
 * Check if the app is currently in the background
 * Returns false on non-Android platforms
 */
expect fun isAppInBackground(): Boolean
