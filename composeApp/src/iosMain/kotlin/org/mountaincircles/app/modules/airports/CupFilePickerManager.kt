package org.mountaincircles.app.modules.airports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.io.FileManager
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.modules.airports.logic.AirportsConstants
import org.mountaincircles.app.modules.airports.AirportsModule
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * iOS file picker manager for CUP outlanding file import
 * This must be initialized with a UIViewController reference
 */
class CupFilePickerManager(private val viewController: UIViewController) {

    private var currentSaveCallback: ((Boolean) -> Unit)? = null
    private var currentSaveModule: AirportsModule? = null

    private val saveDocumentPickerDelegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentAtURL: platform.Foundation.NSURL) {
            val callback = currentSaveCallback
            val module = currentSaveModule
            currentSaveCallback = null
            currentSaveModule = null

            Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "CUP file selected for saving: $didPickDocumentAtURL")

            // Save CUP file in IO thread (no processing)
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val filePath = didPickDocumentAtURL.path
                    if (filePath != null) {
                        // Read file data
                        val fileManager = FileManager()
                        val fileBytes = fileManager.readBytes(filePath)

                        if (fileBytes != null) {
                            // Get file name from path
                            val fileName = filePath.substringAfterLast("/")
                            Logger.log("CUP_FILE_PICKER", LogLevel.DEBUG, "CUP file name: $fileName")

                            if (fileName.lowercase().endsWith(".cup") && module != null) {
                                // Copy CUP file to app storage (no processing)
                                val fileManager = getGlobalFileManager()
                                val airportsDir = module.airportsBusinessService.getAirportsDirectory()
                                val cupFilePath = "$airportsDir/$fileName"

                                try {
                                    fileManager.writeBytes(cupFilePath, fileBytes)
                                    Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "CUP file saved to: $cupFilePath")
                                    callback?.invoke(true)
                                } catch (e: Exception) {
                                    Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Failed to save CUP file to storage: ${e.message}", e)
                                    callback?.invoke(false)
                                }
                            } else if (fileName.lowercase().endsWith(".cup")) {
                                Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Module reference is null - cannot save CUP file")
                                callback?.invoke(false)
                            } else {
                                Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Selected file is not a .cup file: $fileName")
                                callback?.invoke(false)
                            }
                        } else {
                            Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Failed to read file data from: $filePath")
                            callback?.invoke(false)
                        }
                    } else {
                        Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Invalid file path")
                        callback?.invoke(false)
                    }
                } catch (e: Exception) {
                    Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Failed to save selected CUP file: ${e.message}", e)
                    callback?.invoke(false)
                }
            }
        }

        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>?) {
            val callback = currentSaveCallback
            val module = currentSaveModule
            currentSaveCallback = null
            currentSaveModule = null

            val urls = didPickDocumentsAtURLs as? List<platform.Foundation.NSURL>
            if (urls != null && urls.isNotEmpty()) {
                Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "${urls.size} CUP files selected for saving")

                // Save all CUP files in IO thread (no processing)
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        var allSuccessful = true

                        for (url in urls) {
                            try {
                                val filePath = url.path
                                if (filePath != null) {
                                    // Read file data
                                    val fileManager = FileManager()
                                    val fileBytes = fileManager.readBytes(filePath)

                                    if (fileBytes != null) {
                                        // Get file name from path
                                        val fileName = filePath.substringAfterLast("/")
                                        Logger.log("CUP_FILE_PICKER", LogLevel.DEBUG, "CUP file name: $fileName")

                                        if (fileName.lowercase().endsWith(".cup") && module != null) {
                                            // Copy CUP file to app storage (no processing)
                                            val globalFileManager = getGlobalFileManager()
                                            val airportsDir = module.airportsBusinessService.getAirportsDirectory()
                                            val cupFilePath = "$airportsDir/$fileName"

                                            globalFileManager.writeBytes(cupFilePath, fileBytes)
                                            Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "CUP file saved to: $cupFilePath")
                                        } else {
                                            Logger.log("CUP_FILE_PICKER", LogLevel.WARN, "Skipping invalid file: $fileName")
                                            allSuccessful = false
                                        }
                                    } else {
                                        Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Failed to read file data from: $filePath")
                                        allSuccessful = false
                                    }
                                } else {
                                    Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Invalid file path for URL: $url")
                                    allSuccessful = false
                                }
                            } catch (e: Exception) {
                                Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Failed to save CUP file $url: ${e.message}", e)
                                allSuccessful = false
                            }
                        }

                        callback?.invoke(allSuccessful)
                    } catch (e: Exception) {
                        Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Failed to save selected CUP files: ${e.message}", e)
                        callback?.invoke(false)
                    }
                }
            } else {
                Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "CUP file selection cancelled or no files selected")
                callback?.invoke(false)
            }
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            val callback = currentSaveCallback
            currentSaveCallback = null
            currentSaveModule = null
            Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "CUP file save selection cancelled by user")
            callback?.invoke(false)
        }
    }

    private val saveDocumentPicker = UIDocumentPickerViewController(
        forOpeningContentTypes = listOf<Any>() // Empty list - will be configured at runtime if needed
    ).apply {
        delegate = saveDocumentPickerDelegate
        allowsMultipleSelection = true
    }

    fun launchCupSave(module: AirportsModule, onResult: (Boolean) -> Unit) {
        try {
            currentSaveCallback = onResult
            currentSaveModule = module

            // Present the save document picker
            viewController.presentViewController(saveDocumentPicker, animated = true, completion = null)
            Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "CUP save document picker presented")
        } catch (e: Exception) {
            Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Failed to launch CUP file save picker: ${e.message}", e)
            currentSaveCallback = null
            currentSaveModule = null
            onResult(false)
        }
    }
}




/**
 * Parse CUP coordinate format (DDMM.mmmN/E) to decimal degrees
 */

/**
 * Static holder for the global CUP file picker manager
 */
private var globalCupFilePickerManager: CupFilePickerManager? = null

fun setCupFilePickerManager(manager: CupFilePickerManager) {
    globalCupFilePickerManager = manager
    Logger.log("CUP_FILE_PICKER", LogLevel.DEBUG, "CUP file picker manager set")
}

fun getCupFilePickerManager(): CupFilePickerManager? {
    return globalCupFilePickerManager
}
