package org.mountaincircles.app.modules.skysight.logic

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.controllers.RealTimeTilingController
import org.mountaincircles.app.modules.skysight.logic.data.DownloadStatusType
import org.mountaincircles.app.utils.ScopeManager
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.network.createDownloadManager

/**
 * Orchestrates realtime tile download manager lifecycle and batch import count updates.
 * Extracted from SkysightModule for clearer separation of concerns.
 */
class SkysightDownloadOrchestrator(private val module: SkysightModule) {

    private var downloadManagerJob: Job? = null

    /**
     * Initialize the download manager lifecycle monitoring
     */
    fun initializeDownloadManager() {
        val realtimeLayersActive = module.state.map {
            it.satelliteEnabled || it.localRainEnabled
        }.distinctUntilChanged()

        ScopeManager.uiScope.launch {
            realtimeLayersActive.collect { isActive: Boolean ->
                if (isActive && downloadManagerJob?.isActive != true) {
                    startDownloadManager()
                } else if (!isActive && downloadManagerJob?.isActive == true) {
                    ScopeManager.ioScope.launch {
                        stopDownloadManager()
                    }
                }
            }
        }
    }

    /**
     * Start the download manager coroutine
     */
    private fun startDownloadManager() {
        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Starting realtime download manager")

        downloadManagerJob = ScopeManager.ioScope.launch {
            module.state.collect { currentState ->
                var stateSnapshot = currentState
                while (stateSnapshot.tileDownloadList.isNotEmpty() && stateSnapshot.activeDownloadCount < 5) {
                    processNextDownloadFromQueue()
                    stateSnapshot = module.state.value
                }
            }
        }
    }

    /**
     * Stop the download manager coroutine and clean up
     */
    suspend fun stopDownloadManager() {
        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Stopping realtime download manager")

        downloadManagerJob?.cancel()
        downloadManagerJob = null

        val currentState = module.state.value
        if (currentState.tileDownloadList.isNotEmpty()) {
            Logger.log("SKYSIGHT_MODULE", LogLevel.DEBUG, "Clearing ${currentState.tileDownloadList.size} pending downloads")

            module.updateState { it.copy(
                tileDownloadList = mutableListOf(),
                activeDownloadCount = 0
            ) }

            currentState.tileDownloadStatuses.forEach { (tileKey, status) ->
                if (status.status == DownloadStatusType.PENDING) {
                    RealTimeTilingController.removeDownloadStatus(module, tileKey)
                }
            }
        }
    }

    /**
     * Process the next download from the queue
     */
    private suspend fun processNextDownloadFromQueue() {
        var tileKey: String? = null

        module.updateState { currentState ->
            if (currentState.tileDownloadList.isEmpty() || currentState.activeDownloadCount >= 5) {
                return@updateState currentState
            }

            tileKey = currentState.tileDownloadList.last()
            val updatedQueue = currentState.tileDownloadList.toMutableList()
            updatedQueue.removeAt(updatedQueue.size - 1)

            currentState.copy(
                tileDownloadList = updatedQueue,
                activeDownloadCount = currentState.activeDownloadCount + 1
            )
        }

        if (tileKey == null) return

        try {
            val downloadStatus = RealTimeTilingController.checkDownloadStatus(module, tileKey)
            val isBlocked = downloadStatus != null && when (downloadStatus.status) {
                DownloadStatusType.PENDING,
                DownloadStatusType.FAILED -> true
            }

            if (isBlocked) {
                module.updateState { it.copy(activeDownloadCount = it.activeDownloadCount - 1) }
                return
            }

            val apiManager = SkysightApiManager().apply { setModule(module) }
            val apiKeyResult = apiManager.getApiKey()
            if (apiKeyResult.isFailure) {
                Logger.log("SKYSIGHT_MODULE", LogLevel.ERROR, "API key validation failed, skipping download")
                return
            }

            RealTimeTilingController.setDownloadStatus(module, tileKey, DownloadStatusType.PENDING)

            val storage = SkysightStorage(
                fileManager = getGlobalFileManager(),
                downloadManager = createDownloadManager()
            )

            val layerType = tileKey.substringBefore("_")
            RealTimeTilingController.downloadAndAddTileForType(
                module = module,
                storage = storage,
                apiManager = apiManager,
                tileKey = tileKey,
                layerType = layerType,
                useRealtimeTimeouts = true
            )

            RealTimeTilingController.removeDownloadStatus(module, tileKey)
            module.triggerBatchUpdate()

        } catch (e: Exception) {
            val isHttpError = e.message?.contains("HTTP") == true &&
                    (e.message?.contains(" 4") == true || e.message?.contains(" 5") == true)
            if (isHttpError) {
                RealTimeTilingController.setDownloadStatus(module, tileKey, DownloadStatusType.FAILED)
            } else {
                RealTimeTilingController.removeDownloadStatus(module, tileKey)
                Logger.log("SKYSIGHT_MODULE", LogLevel.DEBUG, "Re-queuing failed tile for immediate retry: $tileKey")
                module.updateState { currentState ->
                    val updatedQueue = currentState.tileDownloadList.toMutableList()
                    updatedQueue.add(tileKey)
                    currentState.copy(tileDownloadList = updatedQueue)
                }
            }
        } finally {
            module.updateState { it.copy(activeDownloadCount = it.activeDownloadCount - 1) }
        }
    }

    /**
     * Remove a tile from the download queue
     */
    suspend fun removeTileFromDownloadQueue(tileKey: String) {
        module.updateState { currentState ->
            val updatedQueue = currentState.tileDownloadList.toMutableList()
            updatedQueue.remove(tileKey)
            currentState.copy(tileDownloadList = updatedQueue)
        }
    }

    /**
     * Update layer import count for batch import progress
     */
    suspend fun updateLayerImportCount(layerId: String, timestampsSize: Int, context: String) {
        module.updateState { currentState ->
            val currentCounts = currentState.layerImportCounts
            val currentCount = currentCounts[layerId] ?: Pair(0, timestampsSize)
            val newCount = Pair(currentCount.first + 1, currentCount.second)
            val newCounts = currentCounts.toMutableMap()
            newCounts[layerId] = newCount
            currentState.copy(layerImportCounts = newCounts)
        }
        Logger.log("SKYSIGHT", LogLevel.DEBUG, "Updated count for $layerId ($context)")
    }
}
