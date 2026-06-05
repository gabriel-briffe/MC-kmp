package org.mountaincircles.app.modules.circles

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.import.logic.CirclesManager

/**
 * Global file picker manager for circles zip import
 * This must be initialized early in MainActivity before the STARTED state
 */
class CirclesFilePickerManager(activity: ComponentActivity) {
    
    private var currentCallback: ((Boolean) -> Unit)? = null
    
    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val callback = currentCallback
        currentCallback = null
        
        if (uri != null) {
            Logger.log("CIRCLES_FILE_PICKER", LogLevel.INFO, "File selected: $uri")
            
            // Start unzip process in IO thread
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    activity.contentResolver.openInputStream(uri)?.use { inputStream ->
                        // Convert InputStream to ByteArray for cross-platform compatibility
                        val fileBytes = inputStream.readBytes()
                        val circlesManager = CirclesManager()
                        val success = circlesManager.unzipToCirclesDir(fileBytes)
                        callback?.invoke(success)
                    } ?: run {
                        Logger.log("CIRCLES_FILE_PICKER", LogLevel.ERROR, "Failed to open input stream for URI: $uri")
                        callback?.invoke(false)
                    }
                } catch (e: Exception) {
                    Logger.log("CIRCLES_FILE_PICKER", LogLevel.ERROR, "Failed to process zip file: ${e.message}", e)
                    callback?.invoke(false)
                }
            }
        } else {
            Logger.log("CIRCLES_FILE_PICKER", LogLevel.INFO, "File selection cancelled")
            callback?.invoke(false)
        }
    }
    
    fun launchZipImport(onResult: (Boolean) -> Unit) {
        try {
            currentCallback = onResult
            
            // Launch file picker for zip files
            launcher.launch(arrayOf(
                "application/zip",
                "application/x-zip-compressed", 
                "application/octet-stream"
            ))
            Logger.log("CIRCLES_FILE_PICKER", LogLevel.INFO, "File picker launched")
        } catch (e: Exception) {
            Logger.log("CIRCLES_FILE_PICKER", LogLevel.ERROR, "Failed to launch file picker: ${e.message}", e)
            currentCallback = null
            onResult(false)
        }
    }
}

/**
 * Static holder for the global circles file picker manager
 */
private var globalCirclesFilePickerManager: CirclesFilePickerManager? = null

fun setCirclesFilePickerManager(manager: CirclesFilePickerManager) {
    globalCirclesFilePickerManager = manager
    Logger.log("CIRCLES_FILE_PICKER", LogLevel.DEBUG, "Circles file picker manager set")
}

fun getCirclesFilePickerManager(): CirclesFilePickerManager? {
    return globalCirclesFilePickerManager
}
