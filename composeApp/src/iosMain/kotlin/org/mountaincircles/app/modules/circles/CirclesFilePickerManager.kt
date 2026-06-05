package org.mountaincircles.app.modules.circles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.import.logic.CirclesManager
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * iOS file picker manager for circles zip import
 * This must be initialized with a UIViewController reference
 */
class CirclesFilePickerManager(private val viewController: UIViewController) {

    private var currentCallback: ((Boolean) -> Unit)? = null

    private val documentPickerDelegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentAtURL: platform.Foundation.NSURL) {
            val callback = currentCallback
            currentCallback = null

            Logger.log("CIRCLES_FILE_PICKER", LogLevel.INFO, "File selected: $didPickDocumentAtURL")

            // Start unzip process in IO thread
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    // Read file data using simpler approach
                    val filePath = didPickDocumentAtURL.path
                    if (filePath != null) {
                        // Use existing FileManager to read the file
                        val fileManager = org.mountaincircles.app.io.FileManager()
                        val fileBytes = fileManager.readBytes(filePath)
                        if (fileBytes != null) {
                            val circlesManager = org.mountaincircles.app.modules.circles.import.logic.CirclesManager()
                            val success = circlesManager.unzipToCirclesDir(fileBytes)
                            callback?.invoke(success)
                        } else {
                            Logger.log("CIRCLES_FILE_PICKER", LogLevel.ERROR, "Failed to read file data from: $filePath")
                            callback?.invoke(false)
                        }
                    } else {
                        Logger.log("CIRCLES_FILE_PICKER", LogLevel.ERROR, "Invalid file path")
                        callback?.invoke(false)
                    }
                } catch (e: Exception) {
                    Logger.log("CIRCLES_FILE_PICKER", LogLevel.ERROR, "Failed to process selected file: ${e.message}", e)
                    callback?.invoke(false)
                }
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            val callback = currentCallback
            currentCallback = null
            Logger.log("CIRCLES_FILE_PICKER", LogLevel.INFO, "File selection cancelled by user")
            callback?.invoke(false)
        }
    }

    private val documentPicker = UIDocumentPickerViewController(
        forOpeningContentTypes = listOf<Any>() // Empty list - will be configured at runtime if needed
    ).apply {
        delegate = documentPickerDelegate
        allowsMultipleSelection = false
    }

    fun launchZipImport(onResult: (Boolean) -> Unit) {
        try {
            currentCallback = onResult

            // Present the document picker
            viewController.presentViewController(documentPicker, animated = true, completion = null)
            Logger.log("CIRCLES_FILE_PICKER", LogLevel.INFO, "Document picker presented")
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
