package org.mountaincircles.app.modules.circles.logic.controllers

import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.logic.data.CirclesState
import org.mountaincircles.app.modules.circles.logic.data.DownloadProgress
import org.mountaincircles.app.modules.circles.logic.data.PackConfig
import org.mountaincircles.app.modules.circles.import.logic.CirclesManager
import org.mountaincircles.app.modules.circles.import.ui.CirclesFilePicker
import org.mountaincircles.app.network.DownloadManager
import org.mountaincircles.app.network.DownloadRequest
import org.mountaincircles.app.network.createDownloadManager
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.utils.ioDispatcher

/**
 * Circles File Operations Manager
 * Handles all file-related operations (import, download, clear) with callback pattern
 * to minimize state flow coupling
 */
class CirclesFileOperations(
    private val circlesManager: CirclesManager,
    private val filePicker: CirclesFilePicker,
    private val onStateUpdate: (CirclesState) -> Unit,
    private val onProgressUpdate: (DownloadProgress) -> Unit,
    private val onRescanPacks: suspend () -> Unit,
    private val onSelectPackConfig: suspend (String, String) -> Unit,
    private val onGetCurrentState: () -> CirclesState,
    private val onCheckPackInstalled: suspend (String, String) -> Boolean
) {

    /**
     * Clear all circles files
     */
    suspend fun clearAllFiles() {
        try {
            val deletedCount = circlesManager.clearAllFiles()
            Logger.log("CIRCLES", LogLevel.INFO, "Cleared $deletedCount circles files")

            // Reset to empty state but preserve initialization and current settings
            val currentState = onGetCurrentState()
            val newState = currentState.copy(
                installedPacks = emptyList(),
                availableConfigs = emptyList(),
                activeConfig = null
                // Keep all other settings (isInitialized, circlesVisibility, etc.)
            )
            onStateUpdate(newState)

            // Settings will be saved through the new generic persistence system

            // Rescan packs after clearing to update UI state
            onRescanPacks()

        } catch (e: Exception) {
            Logger.log("CIRCLES", LogLevel.ERROR, "Failed to clear circles files: ${e.message}", e)
        }
    }

    /**
     * Import circles from a zip file
     */
    suspend fun importFromZip() {
        try {
            Logger.log("CIRCLES", LogLevel.INFO, "Starting circles zip import")

            // Use file picker to select and import zip file
            filePicker.launchZipImport { success ->
                if (success) {
                    // Use coroutine scope to ensure proper sequencing
                    org.mountaincircles.app.utils.ScopeManager.uiScope.launch {
                        Logger.log("CIRCLES", LogLevel.INFO, "Zip import successful, rescanning packs")

                        // Capture current state before rescan to detect new packs
                        val stateBeforeRescan = onGetCurrentState()

                        // Rescan packs to detect new imports
                        onRescanPacks()

                        // Select the imported pack - find the newly added pack
                        val stateAfterRescan = onGetCurrentState()

                        val newPack = stateAfterRescan.installedPacks.find { pack ->
                            !stateBeforeRescan.installedPacks.contains(pack)
                        }

                        if (newPack != null) {
                            Logger.log("CIRCLES", LogLevel.INFO, "Detected new pack: $newPack")

                            // Find the first available config for this pack
                            val newConfig = stateAfterRescan.availableConfigs.find { config ->
                                config.packId == newPack
                            }

                            if (newConfig != null) {
                                Logger.log("CIRCLES", LogLevel.INFO, "Auto-selecting new config: ${newConfig.packId}/${newConfig.configId}")
                                onSelectPackConfig(newConfig.packId, newConfig.configId)
                            }
                        }
                    }
                } else {
                    Logger.log("CIRCLES", LogLevel.WARN, "Zip import was cancelled or failed")
                }
            }
        } catch (e: Exception) {
            Logger.log("CIRCLES", LogLevel.ERROR, "Failed to start zip import: ${e.message}", e)
        }
    }

    /**
     * Download circles from a remote HTTP URL
     */
    suspend fun downloadFromUrl(url: String, fileName: String) {
        try {
            Logger.log("CIRCLES", LogLevel.INFO, "Starting download from URL: $url")

            // Extract pack info from filename (alpes_25-100-250.zip -> packId="alpes", configId="25-100-250-4210")
            val packId = fileName.substringBefore('_')
            val configBase = fileName.substringAfter('_').substringBefore('.')
            val configId = "$configBase-4210" // Add the version suffix

            Logger.log("CIRCLES", LogLevel.DEBUG, "Parsed packId=$packId, configId=$configId from filename=$fileName")

            // Check if pack is already installed (delegate to business controller)
            val isAlreadyInstalled = onCheckPackInstalled(packId, configId)
            if (isAlreadyInstalled) {
                Logger.log("CIRCLES", LogLevel.INFO, "Pack $packId/$configId is already installed, skipping download")

                // Update state to show success without download
                val currentState = onGetCurrentState()
                onStateUpdate(currentState.copy(
                    hasError = false,
                    errorMessage = null
                ))

                // Auto-select the pack if not already selected
                val state = onGetCurrentState()
                if (state.activeConfig?.packId != packId || state.activeConfig.configId != configId) {
                    onSelectPackConfig(packId, configId)
                }

                Logger.log("CIRCLES", LogLevel.INFO, "Pack $packId/$configId is already available")
                return
            }

            // Set download state
            val currentState = onGetCurrentState()
            onStateUpdate(currentState.copy(
                isDownloading = true,
                downloadProgress = DownloadProgress(
                    fileName = fileName,
                    bytesDownloaded = 0,
                    totalBytes = 0,
                    status = "Connecting to server..."
                ),
                hasError = false,
                errorMessage = null
            ))

            // Create download manager and request
            val downloadManager: DownloadManager = createDownloadManager()
            val fileManager = getGlobalFileManager()
            val tempFilePath = "${fileManager.getAppDataDirectory()}/circles/temp_$fileName"

            val downloadRequest = DownloadRequest(
                url = url,
                filePath = tempFilePath
            )

            // Perform download with progress callback
            val result = downloadManager.download(downloadRequest) { progressData ->
                org.mountaincircles.app.utils.ScopeManager.uiScope.launch {
                    val progress = DownloadProgress(
                        fileName = fileName,
                        bytesDownloaded = progressData.downloaded,
                        totalBytes = progressData.total,
                        status = progressData.status
                    )
                    onProgressUpdate(progress)
                }
            }

            if (result.isFailure) {
                throw result.exceptionOrNull() ?: Exception("Download failed")
            }

            Logger.log("CIRCLES", LogLevel.INFO, "Download completed successfully: $tempFilePath")

            // Now process the downloaded zip file
            val stateBeforeProcessing = onGetCurrentState()
            onProgressUpdate(DownloadProgress(
                fileName = fileName,
                bytesDownloaded = stateBeforeProcessing.downloadProgress?.totalBytes ?: 0,
                totalBytes = stateBeforeProcessing.downloadProgress?.totalBytes ?: 0,
                status = "Processing downloaded file..."
            ))

            // Process the downloaded zip file
            try {
                // Read the downloaded file as bytes for cross-platform unzip
                val fileManager = getGlobalFileManager()
                val fileBytes = fileManager.readBytes(tempFilePath)
                if (fileBytes == null) {
                    throw Exception("Failed to read downloaded file: $tempFilePath")
                }

                val unzipSuccess = circlesManager.unzipToCirclesDir(fileBytes)

                if (unzipSuccess) {
                    Logger.log("CIRCLES", LogLevel.INFO, "Successfully processed downloaded zip file")

                    // Clean up temp file after successful processing
                    try {
                        val fileManager = getGlobalFileManager()
                        if (fileManager.delete(tempFilePath)) {
                            Logger.log("CIRCLES", LogLevel.DEBUG, "Cleaned up temp file: $tempFilePath")
                        } else {
                            Logger.log("CIRCLES", LogLevel.WARN, "Failed to delete temp file: $tempFilePath")
                        }
                    } catch (cleanupException: Exception) {
                        Logger.log("CIRCLES", LogLevel.WARN, "Failed to clean up temp file $tempFilePath: ${cleanupException.message}")
                        // Don't fail the entire operation for cleanup issues
                    }

                    // Rescan packs to detect the new pack
                    onRescanPacks()

                    // Auto-select the downloaded pack
                    val stateAfterRescan = onGetCurrentState()
                    val newConfig = stateAfterRescan.availableConfigs.find { config ->
                        config.packId == packId && config.configId == configId
                    }

                    if (newConfig != null) {
                        Logger.log("CIRCLES", LogLevel.INFO, "Auto-selecting downloaded pack: ${newConfig.packId}/${newConfig.configId}")
                        onSelectPackConfig(newConfig.packId, newConfig.configId)
                    }

                    // Clear download state
                    onStateUpdate(stateAfterRescan.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        hasError = false,
                        errorMessage = null
                    ))

                } else {
                    throw Exception("Failed to unzip downloaded file")
                }

            } catch (e: Exception) {
                Logger.log("CIRCLES", LogLevel.ERROR, "Failed to process downloaded file: ${e.message}", e)
                onStateUpdate(onGetCurrentState().copy(
                    isDownloading = false,
                    downloadProgress = null,
                    hasError = true,
                    errorMessage = "Failed to process downloaded file: ${e.message}"
                ))
                throw e
            }

        } catch (e: Exception) {
            Logger.log("CIRCLES", LogLevel.ERROR, "Download failed: ${e.message}", e)
            onStateUpdate(onGetCurrentState().copy(
                isDownloading = false,
                downloadProgress = null,
                hasError = true,
                errorMessage = e.message ?: "Download failed"
            ))
            throw e
        }
    }

}
