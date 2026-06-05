package org.mountaincircles.app.modules.airports

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.modules.airports.logic.AirportsConstants
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.modules.airports.logic.business.isCupxFile
import org.mountaincircles.app.modules.airports.logic.business.splitCupxFile

/**
 * Global file picker manager for CUP outlanding file import
 * This must be initialized early in MainActivity before the STARTED state
 */
class CupFilePickerManager(activity: ComponentActivity) {

    private var currentSaveCallback: ((Boolean) -> Unit)? = null
    private var currentSaveModule: AirportsModule? = null

    private val saveLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val callback = currentSaveCallback
        val module = currentSaveModule
        currentSaveCallback = null
        currentSaveModule = null

        if (uris.isNotEmpty()) {
            Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "${uris.size} CUP files selected for saving")

            // Save all CUP files in IO thread (no processing)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    var allSuccessful = true

                    for (uri in uris) {
                        try {
                            activity.contentResolver.openInputStream(uri)?.use { inputStream ->
                                // Read file content
                                val fileContent = inputStream.readBytes()

                                // Get file name from URI
                                val fileName = activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val nameIndex = cursor.getColumnIndex("_display_name")
                                        if (nameIndex >= 0) cursor.getString(nameIndex) else null
                                    } else null
                                }

                                if (fileName != null && (fileName.lowercase().endsWith(".cup") || fileName.lowercase().endsWith(".cupx")) && module != null) {
                                    val fileManager = getGlobalFileManager()
                                    val airportsDir = module.airportsBusinessService.getAirportsDirectory()
                                    val baseName = fileName.substringBeforeLast(".")

                                    if (isCupxFile(fileContent)) {
                                        Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "Detected CUPX file: $fileName, splitting...")
                                        val splitResult = splitCupxFile(fileContent)
                                        if (splitResult != null) {
                                            val (picsData, pointsData) = splitResult
                                            val picsFileName = "${baseName}_pics.zip"
                                            val pointsFileName = "${baseName}_points.zip"

                                            // Save ZIP files
                                            fileManager.writeBytes("$airportsDir/$picsFileName", picsData)
                                            Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "CUPX pics saved to: $airportsDir/$picsFileName")

                                            fileManager.writeBytes("$airportsDir/$pointsFileName", pointsData)
                                            Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "CUPX points saved to: $airportsDir/$pointsFileName")

                                            // Extract pics.zip to subdirectory for images
                                            val picsExtractDir = "$airportsDir/${baseName}_pics"
                                            val picsExtracted = extractZipToDirectory(picsData, picsExtractDir)
                                            if (picsExtracted) {
                                                Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "CUPX pics extracted to: $picsExtractDir")
                                                // Remove the ZIP file after successful extraction
                                                try {
                                                    val picsZipDeleted = fileManager.delete("$airportsDir/$picsFileName")
                                                    if (picsZipDeleted) {
                                                        Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "Removed ZIP file after extraction: $picsFileName")
                                                    } else {
                                                        Logger.log("CUP_FILE_PICKER", LogLevel.WARN, "Failed to remove ZIP file: $picsFileName")
                                                    }
                                                } catch (e: Exception) {
                                                    Logger.log("CUP_FILE_PICKER", LogLevel.WARN, "Exception removing ZIP file $picsFileName: ${e.message}")
                                                }
                                            } else {
                                                Logger.log("CUP_FILE_PICKER", LogLevel.WARN, "Failed to extract CUPX pics to: $picsExtractDir")
                                            }

                                            // Extract points.zip to temp directory, then save POINTS.CUP as .cup file
                                            val tempPointsDir = "$airportsDir/temp_${baseName}_points"
                                            val pointsExtracted = extractZipToDirectory(pointsData, tempPointsDir)
                                            if (pointsExtracted) {
                                                // Find POINTS.CUP file (could be in root or subdirectory)
                                                val tempDir = java.io.File(tempPointsDir)
                                                val pointsCupFile = findPointsCupFile(tempDir)

                                                if (pointsCupFile != null) {
                                                    val cupFileName = "${baseName}.cup"
                                                    val cupFilePath = "$airportsDir/$cupFileName"
                                                    try {
                                                        pointsCupFile.copyTo(java.io.File(cupFilePath), overwrite = true)
                                                        Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "POINTS.CUP saved as .cup file: $cupFilePath")
                                                    } catch (e: Exception) {
                                                        Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Failed to copy POINTS.CUP to .cup file: ${e.message}", e)
                                                    }
                                                } else {
                                                    Logger.log("CUP_FILE_PICKER", LogLevel.WARN, "POINTS.CUP not found in extracted points directory")
                                                }

                                                // Clean up temp directory and ZIP file
                                                try {
                                                    java.io.File(tempPointsDir).deleteRecursively()
                                                    Logger.log("CUP_FILE_PICKER", LogLevel.DEBUG, "Cleaned up temp directory: $tempPointsDir")

                                                    // Remove the points ZIP file after successful extraction
                                                    val pointsZipDeleted = fileManager.delete("$airportsDir/$pointsFileName")
                                                    if (pointsZipDeleted) {
                                                        Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "Removed ZIP file after extraction: $pointsFileName")
                                                    } else {
                                                        Logger.log("CUP_FILE_PICKER", LogLevel.WARN, "Failed to remove ZIP file: $pointsFileName")
                                                    }
                                                } catch (e: Exception) {
                                                    Logger.log("CUP_FILE_PICKER", LogLevel.DEBUG, "Failed to clean up temp directory: ${e.message}")
                                                }
                                            } else {
                                                Logger.log("CUP_FILE_PICKER", LogLevel.WARN, "Failed to extract CUPX points to temp directory")
                                            }
                                        } else {
                                            Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Failed to split CUPX file: $fileName")
                                            allSuccessful = false
                                        }
                                    } else {
                                        // Regular CUP file - save as-is
                                        val cupFilePath = "$airportsDir/$fileName"
                                        fileManager.writeBytes(cupFilePath, fileContent)
                                        Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "CUP file saved to: $cupFilePath")
                                    }
                                } else {
                                    Logger.log("CUP_FILE_PICKER", LogLevel.WARN, "Skipping invalid file: $fileName")
                                    allSuccessful = false
                                }
                            } ?: run {
                                Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Failed to open input stream for file: $uri")
                                allSuccessful = false
                            }
                        } catch (e: Exception) {
                            Logger.log("CUP_FILE_PICKER", LogLevel.ERROR, "Failed to save file $uri: ${e.message}", e)
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
            Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "CUP file selection cancelled")
            callback?.invoke(false)
        }
    }

    fun launchCupSave(module: AirportsModule, onResult: (Boolean) -> Unit) {
        try {
            currentSaveCallback = onResult
            currentSaveModule = module

            // Launch file picker for CUP/CUPX files (save only)
            saveLauncher.launch(arrayOf(
                "application/octet-stream",  // Generic MIME type for .cup files
                "application/zip",          // ZIP MIME type for .cupx files
                "application/x-zip-compressed", // Alternative ZIP MIME type
                "text/plain"                // Fallback
            ))
            Logger.log("CUP_FILE_PICKER", LogLevel.INFO, "CUP file save picker launched")
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

/**
 * Recursively search for POINTS.CUP file in directory
 */
fun findPointsCupFile(directory: java.io.File): java.io.File? {
    // First check if it exists directly in this directory
    val directFile = java.io.File(directory, "POINTS.CUP")
    if (directFile.exists() && directFile.isFile) {
        return directFile
    }

    // Recursively search subdirectories
    val files = directory.listFiles()
    if (files != null) {
        for (file in files) {
            if (file.isDirectory) {
                val found = findPointsCupFile(file)
                if (found != null) {
                    return found
                }
            }
        }
    }

    return null
}

/**
 * Generic ZIP file extraction utility
 * Extracts a ZIP file from ByteArray to a specified directory
 */
suspend fun extractZipToDirectory(zipData: ByteArray, targetDirectory: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        Logger.log("AIRPORTS_ZIP", LogLevel.INFO, "Starting ZIP extraction to directory: $targetDirectory")

        // Ensure target directory exists
        val targetDir = java.io.File(targetDirectory)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
            Logger.log("AIRPORTS_ZIP", LogLevel.DEBUG, "Created target directory: $targetDirectory")
        }

        var filesExtracted = 0
        java.io.ByteArrayInputStream(zipData).use { bais ->
            java.util.zip.ZipInputStream(bais).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = java.io.File(targetDir, entry.name)

                    if (entry.isDirectory) {
                        if (!outFile.exists()) {
                            outFile.mkdirs()
                            Logger.log("AIRPORTS_ZIP", LogLevel.DEBUG, "Created directory: ${outFile.absolutePath}")
                        }
                    } else {
                        // Ensure parent directories exist
                        if (!outFile.parentFile.exists()) {
                            outFile.parentFile.mkdirs()
                        }

                        // Extract file
                        java.io.FileOutputStream(outFile).use { fos ->
                            val buffer = ByteArray(64 * 1024) // 64KB buffer
                            var bytesRead: Int
                            while (zis.read(buffer).also { bytesRead = it } > 0) {
                                fos.write(buffer, 0, bytesRead)
                            }
                        }

                        filesExtracted++
                        Logger.log("AIRPORTS_ZIP", LogLevel.INFO,
                            "Extracted ${outFile.name} (${outFile.length()} bytes) to ${outFile.absolutePath}")
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        Logger.log("AIRPORTS_ZIP", LogLevel.INFO, "Successfully extracted $filesExtracted files to $targetDirectory")
        true

    } catch (e: Exception) {
        Logger.log("AIRPORTS_ZIP", LogLevel.ERROR, "Failed to extract ZIP to $targetDirectory: ${e.message}", e)
        false
    }
}
