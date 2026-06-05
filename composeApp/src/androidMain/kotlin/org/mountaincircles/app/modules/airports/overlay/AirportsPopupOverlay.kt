package org.mountaincircles.app.modules.airports.overlay

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.io.getGlobalFileManager
import java.io.File
import java.io.FileInputStream

/**
 * Android implementation of airport picture loading
 */
actual suspend fun loadAirportPicture(filePath: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        Logger.log("AIRPORTS_PICTURE", LogLevel.DEBUG, "Loading airport picture: $filePath")

        val file = File(filePath)
        if (!file.exists()) {
            Logger.log("AIRPORTS_PICTURE", LogLevel.WARN, "Picture file does not exist: $filePath")
            return@withContext null
        }

        FileInputStream(file).use { fis ->
            val bitmap = BitmapFactory.decodeStream(fis)
            if (bitmap != null) {
                Logger.log("AIRPORTS_PICTURE", LogLevel.INFO, "Successfully loaded picture: ${file.name} (${bitmap.width}x${bitmap.height})")
                bitmap.asImageBitmap()
            } else {
                Logger.log("AIRPORTS_PICTURE", LogLevel.WARN, "Failed to decode bitmap for: $filePath")
                null
            }
        }
    } catch (e: Exception) {
        Logger.log("AIRPORTS_PICTURE", LogLevel.ERROR, "Error loading airport picture $filePath: ${e.message}", e)
        null
    }
}

/**
 * Convert relative airport picture path to absolute file system path
 */
actual fun getAirportPictureAbsolutePath(relativePath: String): String {
    val fileManager = getGlobalFileManager()
    val airportsDir = "${fileManager.getAppDataDirectory()}/airports"
    return "$airportsDir/$relativePath"
}
