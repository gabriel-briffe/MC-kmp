package org.mountaincircles.app.modules.maps

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.network.HttpConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Global context holder for Android map operations
 * This will be set by the Android app during initialization
 */
private var appContext: Context? = null

/**
 * Initialize the maps system with Android context
 * Call this from MainActivity.onCreate()
 */
fun initializeAndroidMaps(context: Context) {
    appContext = context.applicationContext
    // Context is now available for all map operations
}

/**
 * Get the maps directory
 */
internal fun getMapsDirectory(): File {
    val context = appContext ?: throw IllegalStateException(
        "Android maps not initialized. Call initializeAndroidMaps() from MainActivity."
    )
    
    val mapsDir = File(context.filesDir, "maps")
    if (!mapsDir.exists()) {
        mapsDir.mkdirs()
    }
    return mapsDir
}



actual suspend fun getInstalledMapFileIds(): List<String> = withContext(Dispatchers.IO) {
    try {
        val mapsDir = getMapsDirectory()
        val mapFiles = mapsDir.listFiles { file -> 
            file.isFile && file.name.endsWith(".mbtiles") && file.length() > 0L 
        } ?: emptyArray()
        
        val mapIds = mapFiles.map { file ->
            file.name.removeSuffix(".mbtiles")
        }
        
        Logger.log("MAPS", LogLevel.DEBUG, "Found ${mapIds.size} installed maps: $mapIds")
        return@withContext mapIds
        
    } catch (e: Exception) {
        Logger.log("MAPS", LogLevel.ERROR, "Error getting installed maps: ${e.message}", e)
        return@withContext emptyList()
    }
}

/**
 * Android implementation: Check if a specific map is installed
 */
actual suspend fun checkMapFileExists(mapId: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val mapsDir = getMapsDirectory()
        val mapFile = File(mapsDir, "$mapId.mbtiles")
        val exists = mapFile.exists() && mapFile.length() > 0L
        
        Logger.log("MAPS", LogLevel.DEBUG, "Map $mapId exists: $exists")
        return@withContext exists
        
    } catch (e: Exception) {
        Logger.log("MAPS", LogLevel.ERROR, "Error checking map $mapId: ${e.message}", e)
        return@withContext false
    }
}

/**
 * Android implementation: Delete all map files
 */
actual suspend fun deleteAllMapFiles(): Int = withContext(Dispatchers.IO) {
    try {
        val mapsDir = getMapsDirectory()
        val mapFiles = mapsDir.listFiles { file -> 
            file.isFile && file.name.endsWith(".mbtiles") 
        } ?: emptyArray()
        
        var deletedCount = 0
        for (file in mapFiles) {
            try {
                if (file.delete()) {
                    deletedCount++
                    Logger.log("MAPS", LogLevel.DEBUG, "Deleted map file: ${file.name}")
                } else {
                    Logger.log("MAPS", LogLevel.WARN, "Failed to delete map file: ${file.name}")
                }
            } catch (e: Exception) {
                Logger.log("MAPS", LogLevel.ERROR, "Error deleting ${file.name}: ${e.message}", e)
            }
        }
        
        Logger.log("MAPS", LogLevel.INFO, "Deleted $deletedCount map files")
        return@withContext deletedCount
        
    } catch (e: Exception) {
        Logger.log("MAPS", LogLevel.ERROR, "Error deleting map files: ${e.message}", e)
        return@withContext 0
    }
}

/**
 * Android implementation: Delete a specific map file
 */
actual suspend fun deleteMapFile(mapId: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val mapsDir = getMapsDirectory()
        val mapFile = File(mapsDir, "$mapId.mbtiles")
        
        if (mapFile.exists()) {
            val deleted = mapFile.delete()
            if (deleted) {
                Logger.log("MAPS", LogLevel.DEBUG, "Deleted map file: ${mapFile.name}")
            } else {
                Logger.log("MAPS", LogLevel.WARN, "Failed to delete map file: ${mapFile.name}")
            }
            return@withContext deleted
        } else {
            Logger.log("MAPS", LogLevel.WARN, "Map file not found for deletion: $mapId")
            return@withContext false
        }
        
    } catch (e: Exception) {
        Logger.log("MAPS", LogLevel.ERROR, "Error deleting map $mapId: ${e.message}", e)
        return@withContext false
    }
}

/**
 * Android implementation: Get full file path for a map
 */
actual suspend fun getMapFilePath(mapId: String): String = withContext(Dispatchers.IO) {
    val mapsDir = getMapsDirectory()
    val mapFile = File(mapsDir, "$mapId.mbtiles")
    return@withContext mapFile.absolutePath
}
