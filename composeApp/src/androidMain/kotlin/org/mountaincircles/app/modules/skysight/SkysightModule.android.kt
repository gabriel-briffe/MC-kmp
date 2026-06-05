package org.mountaincircles.app.modules.skysight

import android.content.Context
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.modules.skysight.logic.SkysightStorage
import org.mountaincircles.app.network.createDownloadManager
import kotlinx.coroutines.runBlocking

/**
 * Android-specific extensions for SkysightModule
 */

// Global context for Skysight Android operations
private var skysightAppContext: Context? = null

/**
 * Initialize the Skysight system with Android context
 * Call this from MainActivity.onCreate()
 * This sets up the context for file operations and performs startup cleanup
 */
fun initializeAndroidSkysight(context: Context) {
    skysightAppContext = context.applicationContext

    // Perform startup cleanup of old files using storage
    runBlocking {
        try {
            val fileManager = getGlobalFileManager()
            val downloadManager = createDownloadManager()
            val storage = SkysightStorage(fileManager, downloadManager)
            val cleanedCount = storage.cleanupOldFiles()
            org.mountaincircles.app.logger.Logger.log("SKYSIGHT_MODULE", org.mountaincircles.app.logger.LogLevel.INFO, "Startup cleanup completed: removed $cleanedCount old files")
        } catch (e: Exception) {
            org.mountaincircles.app.logger.Logger.log("SKYSIGHT_MODULE", org.mountaincircles.app.logger.LogLevel.ERROR, "Failed to perform startup cleanup: ${e.message}")
        }
    }

    org.mountaincircles.app.logger.Logger.log("SKYSIGHT_MODULE", org.mountaincircles.app.logger.LogLevel.INFO, "Initialized Android context for Skysight file operations with startup cleanup")
}


/**
 * Get the Android context for Skysight operations
 */
internal fun getSkysightContext(): Context {
    return skysightAppContext ?: throw IllegalStateException(
        "Skysight Android context not initialized. Call initializeAndroidSkysight() from MainActivity."
    )
}