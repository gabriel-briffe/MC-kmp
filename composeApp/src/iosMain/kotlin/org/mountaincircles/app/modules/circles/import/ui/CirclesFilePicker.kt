package org.mountaincircles.app.modules.circles.import.ui

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import kotlin.coroutines.resume

/**
 * iOS implementation of CirclesFilePicker
 * Uses the global CirclesFilePickerManager that's initialized in the iOS app
 */
actual class CirclesFilePicker {

    /**
     * Launch file picker to select a zip file for circles import
     */
    actual suspend fun launchZipImport(onResult: suspend (Boolean) -> Unit) = suspendCancellableCoroutine<Unit> { continuation ->
        try {
            val manager = org.mountaincircles.app.modules.circles.getCirclesFilePickerManager()
            if (manager != null) {
                manager.launchZipImport { success ->
                    CoroutineScope(Dispatchers.Main).launch {
                        onResult(success)
                        continuation.resume(Unit)
                    }
                }
            } else {
                Logger.log("CIRCLES_FILE_PICKER", LogLevel.ERROR, "Circles file picker manager not initialized")
                CoroutineScope(Dispatchers.Main).launch {
                    onResult(false)
                    continuation.resume(Unit)
                }
            }
        } catch (e: Exception) {
            Logger.log("CIRCLES_FILE_PICKER", LogLevel.ERROR, "Failed to launch file picker: ${e.message}", e)
            CoroutineScope(Dispatchers.Main).launch {
                onResult(false)
                continuation.resume(Unit)
            }
        }
    }
}
