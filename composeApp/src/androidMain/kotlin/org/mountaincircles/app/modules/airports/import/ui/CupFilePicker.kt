package org.mountaincircles.app.modules.airports.import.ui

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airports.getCupFilePickerManager
import org.mountaincircles.app.modules.airports.AirportsModule
import kotlin.coroutines.resume

/**
 * Android implementation of CupFilePicker
 * Uses the global CupFilePickerManager that's initialized in MainActivity
 */
actual class CupFilePicker {

    /**
     * Launch file picker to just save a .cup file to filesystem (no processing)
     */
    actual suspend fun launchCupSave(module: AirportsModule, onResult: suspend (Boolean) -> Unit) = suspendCancellableCoroutine<Unit> { continuation ->
        try {
            val manager = getCupFilePickerManager()
            if (manager != null) {
                manager.launchCupSave(module) { success ->
                    CoroutineScope(Dispatchers.Main).launch {
                        onResult(success)
                        continuation.resume(Unit)
                    }
                }
            } else {
                Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "CUP file picker manager not initialized")
                CoroutineScope(Dispatchers.Main).launch {
                    onResult(false)
                    continuation.resume(Unit)
                }
            }
        } catch (e: Exception) {
            Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Failed to launch CUP file save picker: ${e.message}", e)
            CoroutineScope(Dispatchers.Main).launch {
                onResult(false)
                continuation.resume(Unit)
            }
        }
    }
}
