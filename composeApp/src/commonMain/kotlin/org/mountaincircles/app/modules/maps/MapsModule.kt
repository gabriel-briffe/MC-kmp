package org.mountaincircles.app.modules.maps

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import org.mountaincircles.app.error.AppError
import org.mountaincircles.app.error.ErrorHandler
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.network.DownloadManager
import org.mountaincircles.app.network.DownloadRequest
import org.mountaincircles.app.network.createDownloadManager
import org.mountaincircles.app.utils.DownloadStateManager
import org.mountaincircles.app.utils.ScopeManager
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.modules.maps.layer.ui.MapsLayerManager
import androidx.compose.runtime.Composable
import org.mountaincircles.app.modules.*
import org.mountaincircles.app.modules.ModuleMapLayer
import org.mountaincircles.app.state.*
import org.mountaincircles.app.ui.AppIcons
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import org.mountaincircles.app.modules.maps.logic.data.MapsState
import org.mountaincircles.app.modules.maps.logic.data.MapSource
import org.mountaincircles.app.modules.maps.logic.data.MapSources
import org.mountaincircles.app.modules.maps.logic.data.MapsImportDisplayData
import org.mountaincircles.app.modules.maps.logic.data.MapsLayerDisplayData
import org.mountaincircles.app.modules.maps.logic.data.DownloadProgress
import org.mountaincircles.app.modules.maps.logic.data.toUnifiedProgress
import org.mountaincircles.app.modules.maps.logic.ui.getMapsIcon
import org.mountaincircles.app.modules.maps.logic.ui.getMapsModuleActions
import androidx.compose.ui.graphics.painter.Painter
import org.mountaincircles.app.modules.maps.logic.initialization.initializeMaps
import org.mountaincircles.app.modules.maps.logic.business.MapsBusinessService


/**
 * Maps module for importing and managing hillshade map tiles
 *
 * This module handles:
 * - Displaying available maps for download
 * - Downloading MBTiles files from predefined sources
 * - Managing installed maps
 * - Providing map layers above OSM base layer
 *
 * Uses BaseStatefulModule for common initialization logic
 */
class MapsModule : BaseStatefulModule<MapsState>() {


    override val hasTopMenuButton = false  // ❌ No top menu button
    override val hasMainMenuButton = true   // ✅ Has main menu button
    override val hasSidebarWidget = false    // ❌ No sidebar widget
    override val hasSettingsPanel = false    // ❌ No settings panel
    override val hasImportSheet = true       // ✅ Has import sheet

    init {
        // Initialize state immediately so moduleState is available during module creation
        initializeState(MapsState())
    }

    // Internal helper for safe state access
    internal val currentState: MapsState get() = state.value

    // Override moduleState to automatically update hasDataToRender based on installedMaps
    override val moduleState: StateFlow<MapsState> = state.map { mapsState ->
        val hasDataToRender = mapsState.installedMaps.isNotEmpty()
        Logger.log("MAPS", LogLevel.DEBUG, "hasDataToRender update: installedMaps.size=${mapsState.installedMaps.size} -> hasDataToRender=$hasDataToRender")
        mapsState.copy(hasDataToRender = hasDataToRender)
    }.stateIn(ScopeManager.uiScope, SharingStarted.Eagerly, state.value.copy(hasDataToRender = state.value.installedMaps.isNotEmpty()))

    // 🎯 DOWNLOAD STATE MANAGER: Centralized download lifecycle management
    private val downloadStateManager = DownloadStateManager("MAPS")

    // 🎯 ACTIVE DOWNLOAD JOBS: Store jobs for cancellation
    private val activeDownloadJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    override val moduleId: String = "maps"
    override val displayName: String = "Hillshade Maps"
    override val moduleInitializationOrder: Int = 0 // Controls module initialization order
    override val sidebarWidgetOrder: Int = 0 // Controls sidebar widget display order
    override val mainMenuOrder: Int = 5 // Controls main menu ordering (lower = first)

    // Business service for core map operations
    internal val businessService = MapsBusinessService(this)


    // Public state access (use inherited state from BaseStatefulModule)
    val mapsState: StateFlow<MapsState> get() = state

    // BaseStatefulModule required methods
    override fun createLayerManager(): ModuleMapLayer {
        val adapter = MapsLayerManagerAdapter(this)
        // Initialize layers immediately when creating the adapter
        adapter.initializeLayers()
        return adapter
    }

    override suspend fun onInitialize() {
        initializeMaps()
    }


    // ✅ STANDARDIZED STATE MANAGEMENT: Validation logic
    override fun validateState(state: MapsState): List<String> {
        val errors = mutableListOf<String>()
        if (state.installedMaps.any { it.isBlank() }) {
            errors.add("Map IDs cannot be blank")
        }
        if (state.downloadProgress?.mapId?.isBlank() == true) {
            errors.add("Download mapId cannot be blank")
        }
        return errors
    }

    // Adapter to make MapsLayerManager compatible with ModuleMapLayer
    private inner class MapsLayerManagerAdapter(private val mapsModule: MapsModule) : ModuleMapLayer {
        val layerManager = MapsLayerManager(mapsModule)

        override suspend fun addLayers() {
            // Layers are added via the LayerManager system, not directly
            Logger.log("MAPS", LogLevel.DEBUG, "Maps layers managed by LayerManager system")
        }

        override suspend fun removeLayers() {
            // Layers are removed via the LayerManager system
            Logger.log("MAPS", LogLevel.DEBUG, "Maps layers removed via LayerManager system")
        }

        override suspend fun updateVisibility(visible: Boolean) {
            // Visibility is handled by the LayerManager system
            Logger.log("MAPS", LogLevel.DEBUG, "Maps layer visibility updated: $visible")
        }

        override fun areLayersAdded(): Boolean {
            // Check if layers are registered with LayerManager
            return layerManager.layerIdsPublic.isNotEmpty()
        }

        fun initializeLayers() {
            layerManager.initializeLayers()
        }

        fun cleanup() {
            layerManager.cleanup()
        }
    }

    // ✅ PROGRESS FLOW: Dedicated selective flow for optimal progress UI performance
    val progressFlow: StateFlow<org.mountaincircles.app.ui.components.UnifiedProgress> = ModuleBase.createSelectiveFlow(
        mapsState,
        { state -> state.downloadProgress?.toUnifiedProgress() ?: org.mountaincircles.app.ui.components.UnifiedProgress.idle() },
        org.mountaincircles.app.ui.components.UnifiedProgress.idle()
    )

    // 🎯 DOWNLOAD STATE MANAGER: Download map with complete pipeline using centralized manager
    suspend fun downloadMapPipeline(mapId: String): kotlinx.coroutines.Job? {
        // Check if already installed
        val isInstalled = checkMapFileExists(mapId)
        if (isInstalled) {
            Logger.log("MAPS", LogLevel.INFO, "Map $mapId already installed, skipping download")
            return null
        }

        val map = MapSources.availableMaps.find { it.id == mapId }
        if (map == null) {
            val error = AppError.ValidationError("Map not found: $mapId", "mapId")
            ErrorHandler.handle(error, "MapsModule.downloadMapPipeline")
            return null
        }

        // Set active download state at start
        updateState {
            it.copy(
                activeDownloadMapId = mapId,
                isDownloadActive = true
            )
        }

        // Execute download pipeline using centralized manager (async version)
        val downloadJob = downloadStateManager.executeDownloadAsync(mapId) {
            try {
                Logger.log("MAPS", LogLevel.INFO, "Starting download pipeline for map: ${map.name}")

                // Update state for download start
                updateState {
                    it.copy(
                        isDownloading = true,
                        downloadProgress = DownloadProgress(
                            mapId = map.id,
                            mapName = map.name,
                            current = 0,
                            total = 1,
                            bytesDownloaded = 0,
                            totalBytes = 0,
                            status = "Starting download..."
                        ),
                        hasError = false,
                        errorMessage = null
                    )
                }

                // 🎯 PHASE 1: Download the map file using cancellable approach
                val filePath = downloadMapFileCancellable(map)

                // 🎯 PHASE 2: Process downloaded file (if needed)
                processDownloadedMap(filePath, map)

                // 🎯 PHASE 3: Update module state
                updateStateForCompletedDownload(map)

                Logger.log("MAPS", LogLevel.INFO, "Download pipeline completed successfully for map: ${map.name}")

                // Clear download state on success (same as error case)
                updateState {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        hasError = false,
                        errorMessage = null,
                        activeDownloadMapId = null,
                        isDownloadActive = false
                    )
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                Logger.log("MAPS", LogLevel.INFO, "Download cancelled for map ${map.name}")
                // For cancellation, just clear the state without showing error
                updateState {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        hasError = false,
                        errorMessage = null,
                        activeDownloadMapId = null,  // Clear active download
                        isDownloadActive = false     // No download active
                    )
                }
                throw e // Re-throw to let the async handler catch it
            } catch (e: Exception) {
                // Use Phase 6 error framework for consistent classification and logging
                val appError = AppError.ModuleError("Download failed for ${map.name}", "download_pipeline", e)
                ErrorHandler.handle(appError, "MapsModule.downloadMapPipeline")

                updateState {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        hasError = true,
                        errorMessage = e.message ?: "Download failed",
                        activeDownloadMapId = null,  // Clear active download on error
                        isDownloadActive = false     // No download active
                    )
                }
                throw e // Re-throw to let the async handler catch it
            }
        }

        // Store the job for cancellation (if it was created)
        if (downloadJob != null) {
            activeDownloadJobs[mapId] = downloadJob
        }

        // Return the download job for potential cancellation
        return downloadJob
    }

    // 🎯 CANCEL DOWNLOAD: Cancel download for specific map
    fun cancelDownload(mapId: String) {
        Logger.log("MAPS", LogLevel.INFO, "Cancelling download for map: $mapId")

        // Cancel the actual download job if it exists
        val downloadJob = activeDownloadJobs[mapId]
        if (downloadJob != null && downloadJob.isActive) {
            Logger.log("MAPS", LogLevel.INFO, "Cancelling download job for $mapId")
            downloadJob.cancel()
            activeDownloadJobs.remove(mapId)
        }

        // Also cancel in the state manager as backup
        downloadStateManager.cancelDownload(mapId)

        // Update state to reflect cancellation
        updateState {
            it.copy(
                activeDownloadMapId = null,  // Clear active download
                isDownloadActive = false    // No download active
            )
        }
    }

    // 🎯 DOWNLOAD STATE MANAGER: Check if map is currently downloading using centralized manager
    fun isMapDownloading(mapId: String): Boolean = downloadStateManager.isDownloading(mapId)

    fun resetDownloadState() {
        Logger.log("MAPS", LogLevel.INFO, "🔄 Resetting download state (immediate cleanup)")
        // Clear any active downloads
        activeDownloadJobs.values.forEach { it.cancel() }
        activeDownloadJobs.clear()
        // Reset download state
        updateState {
            it.copy(
                isDownloading = false,
                downloadProgress = null,
                activeDownloadMapId = null,
                isDownloadActive = false
            )
        }
    }

    // ViewModel factory method for consistent creation
    fun createImportViewModel(): org.mountaincircles.app.modules.maps.ui.MapsImportViewModel {
        return org.mountaincircles.app.modules.maps.ui.MapsImportViewModelImpl(this)
    }

    // 🎯 DOWNLOAD STATE MANAGER: Cleanup active downloads using centralized manager
    fun cleanupActiveDownloads() {
        downloadStateManager.cleanup()
    }

    // 🎯 SINGLE PIPELINE: Download map file
    private suspend fun downloadMapFileCancellable(map: MapSource): String {
        Logger.log("MAPS", LogLevel.INFO, "Downloading map file: ${map.name}")

        val filePath = getMapFilePath(map.id)
        val downloadRequest = DownloadRequest(
            url = map.url,
            filePath = filePath
        )

        val downloadManager: DownloadManager = createDownloadManager()

        // Create a cancellable job for the download
        val result = coroutineScope {
            async {
                downloadManager.download(downloadRequest) { progressData ->
                    // Update progress - this will be cancelled if coroutine is cancelled
                    updateState {
                        it.copy(
                            downloadProgress = DownloadProgress(
                                mapId = map.id,
                                mapName = map.name,
                                current = 0,
                                total = 1,
                                bytesDownloaded = progressData.downloaded,
                                totalBytes = progressData.total,
                                status = progressData.status
                            )
                        )
                    }
                }
            }.await()
        }

        if (result.isSuccess) {
            Logger.log("MAPS", LogLevel.INFO, "Map file downloaded successfully: ${map.name}")
            return filePath
        } else {
            val exception = result.exceptionOrNull() ?: Exception("Download failed for ${map.name}")

            // Clean up partially downloaded file on failure
            try {
                val fileManager = getGlobalFileManager()
                if (fileManager.exists(filePath)) {
                    if (fileManager.delete(filePath)) {
                        Logger.log("MAPS", LogLevel.INFO, "Cleaned up partial download file: ${filePath}")
                    } else {
                        Logger.log("MAPS", LogLevel.WARN, "Failed to delete partial file: ${filePath}")
                    }
                }
            } catch (cleanupException: Exception) {
                Logger.log("MAPS", LogLevel.WARN, "Failed to clean up partial file: ${filePath}", cleanupException)
            }

            Logger.log("MAPS", LogLevel.ERROR, "Map file download failed: ${map.name}", exception)
            throw exception
        }
    }

    // 🎯 SINGLE PIPELINE: Process downloaded map
    private suspend fun processDownloadedMap(filePath: String, map: MapSource) {
        Logger.log("MAPS", LogLevel.INFO, "Processing downloaded map: ${map.name}")

        // Update progress for processing phase
        updateState {
            it.copy(
                downloadProgress = DownloadProgress(
                    mapId = map.id,
                    mapName = map.name,
                    current = 1,
                    total = 1,
                    bytesDownloaded = 100,
                    totalBytes = 100,
                    status = "Processing downloaded file..."
                )
            )
        }

        // In this case, processing is minimal since MBTiles files are ready to use
        // But we could add validation here if needed
        Logger.log("MAPS", LogLevel.INFO, "Map processing completed: ${map.name}")
    }

    // 🎯 SINGLE PIPELINE: Update state for completed download
    private suspend fun updateStateForCompletedDownload(map: MapSource) {
        Logger.log("MAPS", LogLevel.INFO, "Updating state for completed download: ${map.name}")

        // Get updated list of installed maps
        val newInstalledMaps = getInstalledMapFileIds()


        // Update state
        updateState {
            it.copy(
                isDownloading = false,
                downloadProgress = null,
                installedMaps = newInstalledMaps,
                hasError = false,
                errorMessage = null
            )
        }

        Logger.log("MAPS", LogLevel.INFO, "State updated for completed download: ${map.name}")
    }

    // ✅ PHASE 1: Selective reactive flows using new utility functions
    val importDisplayFlow: StateFlow<MapsImportDisplayData> = ModuleBase.createSelectiveFlow(
        mapsState,
        { state -> MapsImportDisplayData(state.installedMaps, state.isDownloading, state.downloadProgress?.toUnifiedProgress(), state.hasError, state.errorMessage, state.availableMaps) },
        MapsImportDisplayData(emptyList(), false, null, false, null, MapSources.availableMaps)
    )

    val layerDisplayFlow: StateFlow<MapsLayerDisplayData> = ModuleBase.createSelectiveFlow(
        moduleState,
        { state -> MapsLayerDisplayData(state.hasDataToRender ?: false, state.installedMaps) },
        MapsLayerDisplayData(false, emptyList())
    )

    // ✅ CIRCLES PATTERN: Removed Redux reducer - using direct state updates only



    // ✅ CIRCLES PATTERN: Clean UI state derivation
    override val uiState: StateFlow<ModuleUIState> = ModuleBase.createUIStateFlow(
        state,
        hasContentPredicate = { (it as? MapsState)?.installedMaps?.isNotEmpty() == true },
        isLoadingPredicate = { (it as? MapsState)?.isDownloading ?: false }
    )

    // Effect processor for side effects


    // Managers - now handled by BaseStatefulModule
    // layerManager is now managed by BaseStatefulModule
    val layerManagerInstance: MapsLayerManager?
        get() = (layerManager as? MapsLayerManagerAdapter)?.layerManager




    override suspend fun onCleanup() {
        Logger.log("MAPS", LogLevel.INFO, "Cleaning up maps module resources")

        try {

            // Layer cleanup is handled by BaseStatefulModule
            Logger.log("MAPS", LogLevel.INFO, "Maps module cleanup completed")

        } catch (e: Exception) {
            Logger.log("MAPS", LogLevel.ERROR, "Error during maps cleanup: ${e.message}", e)
        }
    }


    /**
     * Clear all installed maps
     */
    suspend fun clearAllMaps() {
        try {
            Logger.log("MAPS", LogLevel.INFO, "Clearing all maps")
            businessService.clearAllMaps()
            Logger.log("MAPS", LogLevel.INFO, "All maps cleared successfully")
        } catch (e: Exception) {
            val appError = AppError.ModuleError("Failed to clear all maps", "clear_all", e)
            ErrorHandler.handle(appError, "MapsModule.clearAllMaps")
            throw e // Re-throw to let caller handle
        }
    }

    /**
     * Delete a specific installed map
     */
    suspend fun deleteMap(mapId: String) {
        try {
            Logger.log("MAPS", LogLevel.INFO, "Deleting map: $mapId")
            businessService.deleteMap(mapId)
            Logger.log("MAPS", LogLevel.INFO, "Map deleted successfully: $mapId")
        } catch (e: Exception) {
            val appError = AppError.ModuleError("Failed to delete map $mapId", "delete_map", e)
            ErrorHandler.handle(appError, "MapsModule.deleteMap")
            throw e // Re-throw to let caller handle
        }
    }

    /**
     * Import MBTiles file from device storage
     */
    suspend fun importMBTilesFile(filePath: String) {
        businessService.importMBTilesFile(filePath)
    }

    /**
     * Generate custom map for specified area
     */
    suspend fun generateCustomMap(
        latitude: Double,
        longitude: Double,
        zoomLevels: IntRange = 8..14,
        radiusKm: Double = 50.0
    ) {
        businessService.generateCustomMap(latitude, longitude, zoomLevels, radiusKm)
    }

    // UI Interface methods
    @Composable
    override fun getIcon(): Painter {
        return getMapsIcon()
    }


    override fun getModuleActions(): List<ModuleMenuAction> {
        val isReady = uiState.value.isReady
        Logger.log("MAPS", LogLevel.DEBUG, "MapsModule: getModuleActions called, uiState.isReady=$isReady")

        return getMapsModuleActions(moduleId, isReady)
    }
}

