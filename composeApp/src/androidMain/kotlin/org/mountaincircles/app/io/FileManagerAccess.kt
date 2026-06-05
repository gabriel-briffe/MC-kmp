package org.mountaincircles.app.io

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Android implementation of global FileManager access functions
 */

private var globalFileManager: FileManager? = null

/**
 * Set the global FileManager instance (called from Application.onCreate())
 */
actual fun setGlobalFileManager(manager: FileManager) {
    globalFileManager = manager
    Logger.log("FILEMANAGER", LogLevel.DEBUG, "Global FileManager set")
}

/**
 * Get the global FileManager instance
 * Set in Application.onCreate() so it is available for widgets when the app is not running.
 */
actual fun getGlobalFileManager(): FileManager {
    return globalFileManager ?: throw IllegalStateException(
        "FileManager not initialized. Call setGlobalFileManager() in Application.onCreate()"
    )
}

/**
 * Android network monitor global access
 */
private var globalNetworkMonitor: org.mountaincircles.app.network.NetworkMonitor? = null

/**
 * App background state tracking
 */
private var isAppInBackgroundState = false
private var lifecycleObserverInitialized = false

private fun initializeLifecycleObserver() {
    if (!lifecycleObserverInitialized) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppInBackgroundState = false
                Logger.log("LIFECYCLE", LogLevel.INFO, "App moved to FOREGROUND")
            }

            override fun onStop(owner: LifecycleOwner) {
                isAppInBackgroundState = true
                Logger.log("LIFECYCLE", LogLevel.INFO, "App moved to BACKGROUND")
            }
        })
        lifecycleObserverInitialized = true
        Logger.log("LIFECYCLE", LogLevel.DEBUG, "App lifecycle observer initialized")
    }
}

/**
 * Set the global NetworkMonitor instance (called from MainActivity on Android)
 */
actual fun setGlobalNetworkMonitor(monitor: org.mountaincircles.app.network.NetworkMonitor) {
    globalNetworkMonitor = monitor
    Logger.log("NETWORK", LogLevel.DEBUG, "Global NetworkMonitor set")
}

/**
 * Get the global NetworkMonitor instance (Android only)
 * Returns null on non-Android platforms
 */
actual fun getGlobalNetworkMonitor(): org.mountaincircles.app.network.NetworkMonitor? {
    return globalNetworkMonitor
}

/**
 * Check if the app is currently in the background
 * Returns false on non-Android platforms
 */
actual fun isAppInBackground(): Boolean {
    initializeLifecycleObserver()
    return isAppInBackgroundState
}
