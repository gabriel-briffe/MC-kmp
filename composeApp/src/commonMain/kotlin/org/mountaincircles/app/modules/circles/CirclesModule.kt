package org.mountaincircles.app.modules.circles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.*
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.state.getGlobalState

import org.mountaincircles.app.state.GlobalState
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.GlobalScope
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.utils.ScopeManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.mountaincircles.app.modules.circles.logic.data.CirclesState
import org.mountaincircles.app.modules.circles.logic.data.PackConfig
import org.mountaincircles.app.modules.circles.import.logic.CirclesManager
import org.mountaincircles.app.modules.circles.import.ui.CirclesFilePicker
import org.mountaincircles.app.modules.circles.layer.ui.CirclesLayerManager
import org.mountaincircles.app.modules.circles.overlay.ui.CirclesParameterOverlay
import org.mountaincircles.app.modules.circles.settings.logic.CirclesSettingsProvider
import org.mountaincircles.app.ui.settings.ModuleSettingsRegistry
import org.mountaincircles.app.ui.overlay.MapOverlayRegistry
import org.mountaincircles.app.network.DownloadManager
import org.mountaincircles.app.network.DownloadRequest
import org.mountaincircles.app.network.createDownloadManager
import org.mountaincircles.app.utils.DownloadStateManager
import org.mountaincircles.app.modules.circles.logic.data.AirfieldLabelsData
import org.mountaincircles.app.modules.circles.logic.data.ClickData
import org.mountaincircles.app.modules.circles.logic.data.DownloadProgress
import org.mountaincircles.app.modules.circles.logic.data.LabelsData
import org.mountaincircles.app.modules.circles.logic.data.LinesData
import org.mountaincircles.app.modules.circles.logic.data.PointsData
import org.mountaincircles.app.modules.circles.logic.data.PolygonData
import org.mountaincircles.app.modules.circles.logic.data.PacksDisplayData
import org.mountaincircles.app.modules.circles.logic.CirclesStateFlows
import org.mountaincircles.app.modules.circles.logic.getCirclesModuleActions
import org.mountaincircles.app.modules.circles.logic.ui.getCirclesIcon
import org.mountaincircles.app.modules.circles.submenu.ui.CirclePacksWidget
import org.mountaincircles.app.settings.Setting
import org.mountaincircles.app.settings.SettingPersistence
import org.mountaincircles.app.settings.SettingType
import org.mountaincircles.app.modules.circles.logic.initialization.initializeCircles
import org.mountaincircles.app.modules.circles.logic.controllers.CirclesBusinessController
import org.mountaincircles.app.modules.circles.logic.controllers.CirclesFileOperations
import org.mountaincircles.app.modules.circles.logic.business.CirclesBusinessService
import org.mountaincircles.app.modules.circles.logic.data.toUnifiedProgress
import org.mountaincircles.app.modules.circles.import.data.CirclePackDefinitions

/**
 * Circles module for MountainCircles Map using BaseStatefulModule
 * Uses BaseStatefulModule for common initialization logic
 */
class CirclesModule : BaseStatefulModule<CirclesState>() {

    override val hasTopMenuButton = true  // ✅ Has top menu button
    override val hasMainMenuButton = true  // ✅ Has main menu button
    override val hasSidebarWidget = true    // ✅ Has sidebar widget
    override val hasSettingsPanel = true    // ✅ Has settings panel
    override val hasImportSheet = true      // ✅ Has import sheet

    // 🎯 DOWNLOAD STATE MANAGER: Centralized download lifecycle management
    private val downloadStateManager = DownloadStateManager("CIRCLES")

    // 🎯 ACTIVE DOWNLOAD JOBS: Store running download jobs for direct cancellation
    private val activeDownloadJobs = mutableMapOf<String, Job>()

    // Business Service - handles core business logic
    internal val businessService = CirclesBusinessService(this)

    override val moduleId: String = "circles"
    override val displayName: String = "Circles"
    override val moduleInitializationOrder: Int = 1 // Controls module initialization order
    override val sidebarWidgetOrder: Int = 1 // Controls sidebar widget display order
    override val mainMenuOrder: Int = 2 // Controls main menu ordering (lower = first)


    init {
        // Initialize state immediately so moduleState is available during module creation
        initializeState(CirclesState())
    }

    // Private helper for safe state access
    internal val currentState: CirclesState get() = state.value

    // ✅ TEST: SettingPersistence for generic testing
    internal val settingPersistence = SettingPersistence(moduleId)

    // ✅ GENERIC: Complete settings list for all circles settings
    internal val persistentStateKeys = listOf(
        Setting("circlesVisibility", false, SettingType.BOOLEAN),
        Setting("sectorsOpacity", 0.1f, SettingType.FLOAT),
        Setting("airfieldsVisibility", true, SettingType.BOOLEAN),
        Setting("airfieldRadius", 8.0f, SettingType.FLOAT),
        Setting("airfieldClickSize", 30.0f, SettingType.FLOAT),
        Setting("labelOffset", 16.0f, SettingType.FLOAT),
        Setting("circlesLabelSize", 14.0f, SettingType.FLOAT),
        Setting("circlesLabelSpacing", 240.0f, SettingType.FLOAT),
        Setting("airfieldLabelSize", 12.0f, SettingType.FLOAT),
        Setting("circlesLineWidth", 2.0f, SettingType.FLOAT),
        Setting("airfieldIconSize", 6.0f, SettingType.FLOAT),
        Setting("circlesMinZoom", 7.0f, SettingType.FLOAT),
        Setting("circleLabelsMinZoom", 9.0f, SettingType.FLOAT),
        Setting("airfieldIconsMinZoom", 18.0f, SettingType.FLOAT),
        Setting("airfieldLabelsMinZoom", 18.0f, SettingType.FLOAT),
        Setting("sectorsMinZoom", 1.0f, SettingType.FLOAT),
        Setting("activePackId", "", SettingType.STRING),
        Setting("activeConfigId", "", SettingType.STRING),
    )

    // State flows (extracted to CirclesStateFlows for Phase E)
    private val stateFlows = CirclesStateFlows(state)
    val circlesVisibility: StateFlow<Boolean> get() = stateFlows.circlesVisibility
    val sectorsOpacity: StateFlow<Float> get() = stateFlows.sectorsOpacity
    val airfieldsVisibility: StateFlow<Boolean> get() = stateFlows.airfieldsVisibility
    val airfieldRadius: StateFlow<Float> get() = stateFlows.airfieldRadius
    val labelOffset: StateFlow<Float> get() = stateFlows.labelOffset
    val circlesLabelSize: StateFlow<Float> get() = stateFlows.circlesLabelSize
    val circlesLabelSpacing: StateFlow<Float> get() = stateFlows.circlesLabelSpacing
    val airfieldLabelSize: StateFlow<Float> get() = stateFlows.airfieldLabelSize
    val circlesLineWidth: StateFlow<Float> get() = stateFlows.circlesLineWidth
    val airfieldIconSize: StateFlow<Float> get() = stateFlows.airfieldIconSize
    val circlesMinZoom: StateFlow<Float> get() = stateFlows.circlesMinZoom
    val circleLabelsMinZoom: StateFlow<Float> get() = stateFlows.circleLabelsMinZoom
    val airfieldIconsMinZoom: StateFlow<Float> get() = stateFlows.airfieldIconsMinZoom
    val airfieldLabelsMinZoom: StateFlow<Float> get() = stateFlows.airfieldLabelsMinZoom
    val sectorsMinZoom: StateFlow<Float> get() = stateFlows.sectorsMinZoom
    val airfieldClickSize: StateFlow<Float> get() = stateFlows.airfieldClickSize
    val installedPacks: StateFlow<List<String>> get() = stateFlows.installedPacks
    val availableConfigs: StateFlow<List<PackConfig>> get() = stateFlows.availableConfigs
    val activeConfig: StateFlow<PackConfig?> get() = stateFlows.activeConfig
    val isDownloading: StateFlow<Boolean> get() = stateFlows.isDownloading
    val downloadProgress: StateFlow<DownloadProgress?> get() = stateFlows.downloadProgress
    val activeDownloadPackId: StateFlow<String?> get() = stateFlows.activeDownloadPackId
    val isDownloadActive: StateFlow<Boolean> get() = stateFlows.isDownloadActive
    val isInitialized: StateFlow<Boolean> get() = stateFlows.isInitialized
    val hasError: StateFlow<Boolean> get() = stateFlows.hasError
    val errorMessage: StateFlow<String?> get() = stateFlows.errorMessage
    val hasDataToRender: StateFlow<Boolean> get() = stateFlows.hasDataToRender
    val circlesState: StateFlow<CirclesState> get() = state
    override val moduleState: StateFlow<CirclesState> get() = stateFlows.moduleState

    // ✅ SIMPLE SOLUTION: Reactive boolean for circles submenu state (initialized in onInitialize)
    var circlesSubmenuOpen: StateFlow<Boolean>? = null

    // ✅ STANDARDIZED STATE MANAGEMENT: Uses inherited updateState with validation

    // ✅ STATE VALIDATION: Override to provide Circles-specific validation
    override fun validateState(state: CirclesState): List<String> {
        val errors = mutableListOf<String>()
        if (state.installedPacks.any { it.isBlank() }) {
            errors.add("Pack IDs cannot be blank")
        }
        return errors
    }

    // Click area feedback state
    private var fadeOutJob: Job? = null

    // BaseStatefulModule required methods
    override fun createLayerManager(): ModuleMapLayer {
        val adapter = CirclesLayerManagerAdapter(this)
        // Defer layer initialization until after persisted state is loaded
        return adapter
    }

    override suspend fun onInitialize() {
        Logger.log("CIRCLES", LogLevel.INFO, "Starting Circles module initialization")
        // Initialize basic components (includes settings loading and reactive state setup)
        initializeCircles()

        Logger.log("CIRCLES", LogLevel.INFO, "Persisted state loaded, initializing layers")
        // Now initialize layers after persisted state is loaded
        (layerManager as? CirclesLayerManagerAdapter)?.initializeLayers()
        Logger.log("CIRCLES", LogLevel.INFO, "Circles layers initialized")

        // Create business controller with layer manager
        createCirclesBusinessController()

        // Setup file operations with proper callbacks now that business controller exists
        setupFileOperations()

        // Scan for installed packs now that business controller is ready
        businessController.rescanPacks()

        // Update layer manager with current selection
        val currentState = currentState
        (layerManager as? CirclesLayerManagerAdapter)?.updateSelection(currentState.activeConfig)

        // Mark module as initialized
        updateState { it.copy(isInitialized = true) }
    }

    /**
     * Create and initialize the circles business controller
     * This encapsulates the complex business controller creation with all dependencies
     */
    private suspend fun createCirclesBusinessController() {
        Logger.log("CIRCLES", LogLevel.INFO, "Creating circles business controller")

        // Extract the specialized layer manager from the adapter
        val layerManagerInstance = (layerManager as? CirclesLayerManagerAdapter)?.layerManager

        // Create the business controller with all required dependencies
        businessController = CirclesBusinessController(
            circlesState = circlesState,  // Use inherited state property
            stateUpdater = { transform -> updateState(transform) },  // Provide update function
            circlesManager = circlesManager,
            layerManager = layerManagerInstance,
            preferredPackId = preferredPackId,
            preferredConfigId = preferredConfigId
        )

        Logger.log("CIRCLES", LogLevel.DEBUG, "Circles business controller created successfully")
    }

    /**
     * Setup file operations with proper callbacks now that business controller exists
     * This encapsulates the complex file operations initialization with all callbacks
     */
    private fun setupFileOperations() {
        Logger.log("CIRCLES", LogLevel.INFO, "Setting up file operations with business controller callbacks")

        fileOperations = CirclesFileOperations(
            circlesManager = circlesManager,
            filePicker = filePicker,
            onStateUpdate = { newState -> updateState { newState } },
            onProgressUpdate = { progress -> updateState { it.copy(downloadProgress = progress) } },
            onRescanPacks = { businessController.rescanPacks() },
            onSelectPackConfig = { packId, configId -> businessController.selectPackConfig(packId, configId) },
            onGetCurrentState = { currentState },
            onCheckPackInstalled = { packId, configId -> businessController.isPackInstalled(packId, configId) }
        )

        Logger.log("CIRCLES", LogLevel.DEBUG, "File operations setup completed")
    }


    // Adapter to make CirclesLayerManager compatible with ModuleMapLayer
    internal inner class CirclesLayerManagerAdapter(private val circlesModule: CirclesModule) : ModuleMapLayer {
        val layerManager = CirclesLayerManager(circlesModule)

        override suspend fun addLayers() {
            // Layers are added via the LayerManager system, not directly
            Logger.log("CIRCLES", LogLevel.DEBUG, "Circles layers managed by LayerManager system")
        }

        override suspend fun removeLayers() {
            // Layers are removed via the LayerManager system
            Logger.log("CIRCLES", LogLevel.DEBUG, "Circles layers removed via LayerManager system")
        }

        override suspend fun updateVisibility(visible: Boolean) {
            // Visibility is handled by the LayerManager system
            Logger.log("CIRCLES", LogLevel.DEBUG, "Circles layer visibility updated: $visible")
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

        fun updateSelection(selection: PackConfig?) {
            layerManager.updateSelection(selection)
        }
    }

    // Progress flow for import UI - maps downloadProgress to UnifiedProgress
    val progressFlow: StateFlow<org.mountaincircles.app.ui.components.UnifiedProgress> = circlesState.map { state ->
        val progress = state.downloadProgress?.toUnifiedProgress() ?: org.mountaincircles.app.ui.components.UnifiedProgress.idle()
        if (progress.isDownloading) {
            Logger.log("CIRCLES_UI", LogLevel.DEBUG, "Progress flow update: ${progress.percentComplete}% - ${progress.fileName}")
        }
        progress
    }.stateIn(
        scope = org.mountaincircles.app.utils.ScopeManager.computationScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = org.mountaincircles.app.ui.components.UnifiedProgress.idle()
    )

    // UI state derived from unified state
    override val uiState: StateFlow<ModuleUIState> get() = stateFlows.uiState

    // Platform-specific manager for file operations
    internal lateinit var circlesManager: CirclesManager

    // File picker for zip import
    internal lateinit var filePicker: CirclesFilePicker

    // Managers - now handled by BaseStatefulModule
    // layerManager is now managed by BaseStatefulModule
    val layerManagerInstance: CirclesLayerManager?
        get() = (layerManager as? CirclesLayerManagerAdapter)?.layerManager


    // Business controller for business logic operations
    internal lateinit var businessController: CirclesBusinessController

    // File operations manager
    internal lateinit var fileOperations: CirclesFileOperations

    // Temporary storage for preferred pack selection during rescan
    internal var preferredPackId: String? = null
    internal var preferredConfigId: String? = null

    
    override suspend fun onCleanup() {
        Logger.log("CIRCLES", LogLevel.INFO, "Cleaning up circles module resources")
        // Layer cleanup is handled by BaseStatefulModule
    }
    
    /**
     * Get module actions for main menu
     */
    override fun getModuleActions(): List<ModuleMenuAction> {
        val s = currentState
        return getCirclesModuleActions(moduleId, s.isInitialized, s.isDownloading) { id ->
            globalState.navigationState.openImportSheet(id)
        }
    }
    
    /**
     * Generic: Save all settings to persistence
     */
    /**
     * Generic: Load all persisted settings and apply to module state
     */
    
    /**
     * Rescan installed packs
     */
    suspend fun rescanPacks() = businessService.rescanPacks()

    
    /**
     * Update visibility
     */
    suspend fun updateCirclesVisibility(isVisible: Boolean) {
        businessService.updateCirclesVisibility(isVisible)
        settingPersistence.saveBoolean("circlesVisibility", isVisible)
    }
    
    /**
     * Update sectors opacity (0-50%)
     */
    suspend fun updateSectorsOpacity(opacity: Float) {
        businessService.updateSectorsOpacity(opacity)
        settingPersistence.saveFloat("sectorsOpacity", opacity)
    }
    
    /**
     * Update airfields visibility
     */
    suspend fun updateAirfieldsVisibility(isVisible: Boolean) {
        businessService.updateAirfieldsVisibility(isVisible)
        settingPersistence.saveBoolean("airfieldsVisibility", isVisible)
    }
    
    /**
     * Update airfield radius
     */
    suspend fun updateAirfieldRadius(radius: Float) {
        businessService.updateAirfieldRadius(radius)
        settingPersistence.saveFloat("airfieldRadius", radius)
    }

    /**
     * Update label offset
     */
    suspend fun updateLabelOffset(offset: Float) {
        businessService.updateLabelOffset(offset)
        settingPersistence.saveFloat("labelOffset", offset)
    }
    
    /**
     * Update circles label size
     */
    suspend fun updateCirclesLabelSize(size: Float) {
        businessService.updateCirclesLabelSize(size)
        settingPersistence.saveFloat("circlesLabelSize", size)
    }

    /**
     * Update circles label spacing
     */
    suspend fun updateCirclesLabelSpacing(spacing: Float) {
        businessService.updateCirclesLabelSpacing(spacing)
        settingPersistence.saveFloat("circlesLabelSpacing", spacing)
    }

    /**
     * Update airfield label size
     */
    suspend fun updateAirfieldLabelSize(size: Float) {
        businessService.updateAirfieldLabelSize(size)
        settingPersistence.saveFloat("airfieldLabelSize", size)
    }

    /**
     * Update airfield click size
     */
    suspend fun updateAirfieldClickSize(size: Float) {
        businessService.updateAirfieldClickSize(size)
        settingPersistence.saveFloat("airfieldClickSize", size)
    }

    /**
     * Update circles line width
     */
    suspend fun updateCirclesLineWidth(width: Float) {
        businessService.updateCirclesLineWidth(width)
        settingPersistence.saveFloat("circlesLineWidth", width)
    }
    
    /**
     * Update airfield icon size
     */
    suspend fun updateAirfieldIconSize(size: Float) {
        businessService.updateAirfieldIconSize(size)
        settingPersistence.saveFloat("airfieldIconSize", size)
    }

    /**
     * Update circles min zoom
     */
    suspend fun updateCirclesMinZoom(minZoom: Float) {
        businessService.updateCirclesMinZoom(minZoom)
        settingPersistence.saveFloat("circlesMinZoom", minZoom)
    }
    
    /**
     * Update circle labels min zoom
     */
    suspend fun updateCircleLabelsMinZoom(minZoom: Float) {
        businessService.updateCircleLabelsMinZoom(minZoom)
        settingPersistence.saveFloat("circleLabelsMinZoom", minZoom)
    }

    /**
     * Update airfield icons min zoom
     */
    suspend fun updateAirfieldIconsMinZoom(minZoom: Float) {
        businessService.updateAirfieldIconsMinZoom(minZoom)
        settingPersistence.saveFloat("airfieldIconsMinZoom", minZoom)
    }
    
    /**
     * Update airfield labels min zoom
     */
    suspend fun updateAirfieldLabelsMinZoom(minZoom: Float) {
        businessService.updateAirfieldLabelsMinZoom(minZoom)
        settingPersistence.saveFloat("airfieldLabelsMinZoom", minZoom)
    }

    /**
     * Update sectors min zoom
     */
    suspend fun updateSectorsMinZoom(minZoom: Float) {
        businessService.updateSectorsMinZoom(minZoom)
        settingPersistence.saveFloat("sectorsMinZoom", minZoom)
    }
    
    /**
     * Select a specific pack and configuration
     */
    suspend fun selectPackConfig(packId: String, configId: String) {
        businessService.selectPackConfig(packId, configId)
        settingPersistence.saveString("activePackId", packId)
        settingPersistence.saveString("activeConfigId", configId)
    }

    /**
     * Show visual feedback for click areas
     */
    fun showClickAreaFeedback() = businessService.showClickAreaFeedback()

    /**
     * Get available pack configurations
     */
    fun getAvailablePackConfigs() = businessService.getAvailablePackConfigs()

    /**
     * Check if pack is installed
     */
    suspend fun isPackInstalled(packId: String, configId: String) = businessService.isPackInstalled(packId, configId)

    /**
     * Delete a pack
     */
    suspend fun deletePack(packId: String, configId: String): Boolean {
        val success = businessService.deletePack(packId, configId)

        if (success) {
            // After deletion, check if we need to auto-select a new pack
            val currentState = circlesState.value

            if (currentState.availableConfigs.isNotEmpty()) {
                // Auto-select the first available pack
                val firstConfig = currentState.availableConfigs.first()
                Logger.log("CIRCLES", LogLevel.INFO, "Auto-selecting first available pack after deletion: ${firstConfig.packId}/${firstConfig.configId}")
                selectPackConfig(firstConfig.packId, firstConfig.configId)
            } else {
                // No packs available, clear persisted settings
                Logger.log("CIRCLES", LogLevel.INFO, "No packs available after deletion, clearing persisted settings")
                settingPersistence.saveString("activePackId", null)
                settingPersistence.saveString("activeConfigId", null)
            }
        }

        return success
    }

    /**
     * Toggle visibility
     */
    suspend fun toggleVisibility() {
        businessService.toggleVisibility()
        // Save the new visibility state
        val newVisibility = circlesState.value.circlesVisibility
        settingPersistence.saveBoolean("circlesVisibility", newVisibility)
    }

    
    /**
     * Clear all circles files
     */
    suspend fun clearAllFiles() = fileOperations.clearAllFiles()
    
    /**
     * Import circles from a zip file
     */
    suspend fun importFromZip() = fileOperations.importFromZip()
    
    // 🎯 SINGLE PIPELINE: Download circle pack with complete pipeline in one coroutine
    // 🎯 DOWNLOAD STATE MANAGER: Download pack with complete pipeline using centralized manager
    suspend fun downloadPackPipeline(packId: String, configId: String): Job? {
        // Use full pack identifier for uniqueness
        val fullPackId = "${packId}_${configId}"

        // Find the pack definition
        val packDef = CirclePackDefinitions.availablePacks.find { it.packId == packId && it.configId == configId }
        if (packDef == null) {
            Logger.log("CIRCLES", LogLevel.ERROR, "Pack definition not found: $packId/$configId")
            return null
        }

        // Get the file name for cleanup purposes
        val fileName = packDef.getFileName()

        // Check if already installed (use the config ID format)
        val fullConfigId = "${configId}-4210"
        val isInstalled = businessService.isPackInstalled(packId, fullConfigId)
        if (isInstalled) {
            Logger.log("CIRCLES", LogLevel.INFO, "Pack $packId/$configId already installed, skipping download")
            return null
        }

        // Set download state at start
        updateState {
            it.copy(
                activeDownloadPackId = fullPackId,
                isDownloadActive = true
            )
        }

        // Execute download pipeline using centralized manager (async version)
        val downloadJob = downloadStateManager.executeDownloadAsync(fullPackId) {
            try {
                Logger.log("CIRCLES", LogLevel.INFO, "Starting download pipeline for pack: ${packDef.displayName}")

                // Update state for download start
                updateState {
                    it.copy(
                        isDownloading = true,
                        downloadProgress = DownloadProgress(
                            fileName = packDef.getFileName(),
                            bytesDownloaded = 0,
                            totalBytes = 0,
                            status = "Starting download..."
                        ),
                        hasError = false,
                        errorMessage = null
                    )
                }

                // 🎯 PHASE 1: Download the pack file using cancellable approach
                val url = packDef.getDownloadUrl()
                val fileName = packDef.getFileName()
                downloadFromUrlCancellable(url, fileName)

                Logger.log("CIRCLES", LogLevel.INFO, "Download pipeline completed successfully for pack: ${packDef.displayName}")

                // Clear download state on success (same as error case)
                updateState {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        hasError = false,
                        errorMessage = null,
                        activeDownloadPackId = null,
                        isDownloadActive = false
                    )
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                Logger.log("CIRCLES", LogLevel.INFO, "Download cancelled for pack ${packDef.displayName}")
                // For cancellation, just clear the state without showing error
                updateState {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        hasError = false,
                        errorMessage = null,
                        activeDownloadPackId = null,  // Clear active download
                        isDownloadActive = false     // No download active
                    )
                }
                throw e // Re-throw to let the async handler catch it
        } catch (e: Exception) {
                Logger.log("CIRCLES", LogLevel.ERROR, "Download pipeline failed for pack ${packDef.displayName}: ${e.message}", e)

                // Clean up partially downloaded file on failure
                try {
                    val fileManager = getGlobalFileManager()
                    val filePath = getPackFilePath(fileName)
                    if (fileManager.exists(filePath)) {
                        if (fileManager.delete(filePath)) {
                            Logger.log("CIRCLES", LogLevel.INFO, "Cleaned up partial download file: ${fileName}")
                        } else {
                            Logger.log("CIRCLES", LogLevel.WARN, "Failed to delete partial download file: ${fileName}")
                        }
                    }
                } catch (cleanupException: Exception) {
                    Logger.log("CIRCLES", LogLevel.WARN, "Failed to clean up partial file: ${fileName}", cleanupException)
                }

                updateState {
                    it.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        hasError = true,
                        errorMessage = e.message ?: "Download failed",
                        activeDownloadPackId = null,  // Clear active download on error
                        isDownloadActive = false     // No download active
                    )
                }
                throw e // Re-throw to let the async handler catch it
            }
        }

        // Store the job for cancellation (if it was created)
        if (downloadJob != null) {
            activeDownloadJobs[fullPackId] = downloadJob
        }

        // Return the download job for potential cancellation
        return downloadJob
    }

    // 🎯 DOWNLOAD STATE MANAGER: Check if pack is currently downloading using centralized manager
    fun isPackDownloading(fullPackId: String): Boolean = downloadStateManager.isDownloading(fullPackId)

    // 🎯 CANCEL DOWNLOAD: Cancel download for specific pack
    fun cancelPackDownload(fullPackId: String) {
        Logger.log("CIRCLES", LogLevel.INFO, "Cancelling download for pack: $fullPackId")

        // Cancel the actual download job if it exists
        val downloadJob = activeDownloadJobs[fullPackId]
        if (downloadJob != null && downloadJob.isActive) {
            Logger.log("CIRCLES", LogLevel.INFO, "Cancelling download job for $fullPackId")
            downloadJob.cancel()
            activeDownloadJobs.remove(fullPackId)
        }

        // Also cancel in the state manager as backup
        downloadStateManager.cancelDownload(fullPackId)

        // Update state to reflect cancellation
        updateState {
            it.copy(
                activeDownloadPackId = null,  // Clear active download
                isDownloadActive = false    // No download active
            )
        }
    }

    // 🎯 DOWNLOAD STATE MANAGER: Cleanup active downloads using centralized manager
    fun cleanupActiveDownloads() {
        downloadStateManager.cleanup()
    }

    // 🎯 SINGLE PIPELINE: Download pack file using cancellable approach
    private suspend fun downloadFromUrlCancellable(url: String, fileName: String) {
        Logger.log("CIRCLES", LogLevel.INFO, "Downloading pack file: ${fileName}")

        val tempFilePath = getPackFilePath("temp_$fileName")
        val downloadRequest = DownloadRequest(
            url = url,
            filePath = tempFilePath
        )

        val downloadManager: DownloadManager = createDownloadManager()

        // Create a cancellable job for the download
        val result = coroutineScope {
            async {
                downloadManager.download(downloadRequest) { progressData ->
                    // Update progress using safe wrapper to prevent crashes during network failures
                    Logger.log("CIRCLES_DOWNLOAD", LogLevel.DEBUG, "Progress update: ${progressData.downloaded}/${progressData.total} bytes (${progressData.percentage}%)")
                    downloadStateManager.safeProgressUpdate {
                        updateState {
                            it.copy(
                                downloadProgress = DownloadProgress(
                                    fileName = fileName,
                                    bytesDownloaded = progressData.downloaded,
                                    totalBytes = progressData.total,
                                    status = progressData.status
                                )
                            )
                        }
                    }
                }
            }.await()
        }

        if (result.isSuccess) {
            Logger.log("CIRCLES", LogLevel.INFO, "Pack file downloaded successfully: ${fileName}")

            // Now process the downloaded zip file (unzip and install)
            processDownloadedPackFile(tempFilePath, fileName)

        } else {
            val exception = result.exceptionOrNull() ?: Exception("Download failed for ${fileName}")

            // Clean up partially downloaded file on failure
            try {
                val fileManager = getGlobalFileManager()
                if (fileManager.exists(tempFilePath)) {
                    if (fileManager.delete(tempFilePath)) {
                        Logger.log("CIRCLES", LogLevel.INFO, "Cleaned up partial download file: ${tempFilePath}")
                    } else {
                        Logger.log("CIRCLES", LogLevel.WARN, "Failed to delete partial download file: ${tempFilePath}")
                    }
                }
            } catch (cleanupException: Exception) {
                Logger.log("CIRCLES", LogLevel.WARN, "Failed to clean up partial file: ${tempFilePath}", cleanupException)
            }

            Logger.log("CIRCLES", LogLevel.ERROR, "Pack file download failed: ${fileName}", exception)
            throw exception
        }
    }

    // 🎯 SINGLE PIPELINE: Process downloaded pack file (unzip and install)
    private suspend fun processDownloadedPackFile(tempFilePath: String, fileName: String) {
        Logger.log("CIRCLES", LogLevel.INFO, "Processing downloaded pack file: ${fileName}")

        try {
            // Extract pack info from filename (alpes_25-100-250.zip -> packId="alpes", configId="25-100-250-4210")
            val packId = fileName.substringBefore('_')
            val configBase = fileName.substringAfter('_').substringBefore('.')
            val configId = "$configBase-4210" // Add the version suffix

            // Create input stream from the downloaded file
            val fileManager = getGlobalFileManager()
            val fileBytes = fileManager.readBytes(tempFilePath)
            if (fileBytes == null) {
                throw Exception("Failed to read downloaded file: $tempFilePath")
            }

            // Use platform-specific unzip method with ByteArray
            val unzipSuccess = (circlesManager as org.mountaincircles.app.modules.circles.import.logic.CirclesManager).unzipToCirclesDir(fileBytes)

            if (unzipSuccess) {
                Logger.log("CIRCLES", LogLevel.INFO, "Successfully processed downloaded pack file: ${fileName}")

                // Clean up temp file after successful processing
                try {
                    val fileManager = getGlobalFileManager()
                    if (fileManager.delete(tempFilePath)) {
                        Logger.log("CIRCLES", LogLevel.DEBUG, "Cleaned up temp file: ${tempFilePath}")
                    } else {
                        Logger.log("CIRCLES", LogLevel.WARN, "Failed to delete temp file: ${tempFilePath}")
                    }
                } catch (cleanupException: Exception) {
                    Logger.log("CIRCLES", LogLevel.WARN, "Failed to clean up temp file ${tempFilePath}: ${cleanupException.message}")
                }

                // Rescan packs to detect the new pack
                rescanPacks()

                // Auto-select the downloaded pack
                val stateAfterRescan = currentState
                val newConfig = stateAfterRescan.availableConfigs.find { config ->
                    config.packId == packId && config.configId == configId
                }

                if (newConfig != null) {
                    Logger.log("CIRCLES", LogLevel.INFO, "Auto-selecting downloaded pack: ${newConfig.packId}/${newConfig.configId}")
                    selectPackConfig(newConfig.packId, newConfig.configId)
                }

            } else {
                throw Exception("Failed to unzip downloaded file")
            }

        } catch (e: Exception) {
            Logger.log("CIRCLES", LogLevel.ERROR, "Failed to process downloaded pack file: ${e.message}", e)

            // Clean up temp file on processing failure
            try {
                val fileManager = getGlobalFileManager()
                if (fileManager.delete(tempFilePath)) {
                    Logger.log("CIRCLES", LogLevel.DEBUG, "Cleaned up temp file after processing failure: ${tempFilePath}")
                } else {
                    Logger.log("CIRCLES", LogLevel.WARN, "Failed to delete temp file after processing failure: ${tempFilePath}")
                }
            } catch (cleanupException: Exception) {
                Logger.log("CIRCLES", LogLevel.WARN, "Failed to clean up temp file ${tempFilePath}: ${cleanupException.message}")
            }

            throw e
        }
    }

    // 🎯 HELPER: Get pack file path
    private fun getPackFilePath(fileName: String): String {
        val fileManager = getGlobalFileManager()
        return "${fileManager.getAppDataDirectory()}/circles/$fileName"
    }

    /**
     * Download circles from a remote HTTP URL (legacy method - kept for compatibility)
     */
    suspend fun downloadFromUrl(url: String, fileName: String) = fileOperations.downloadFromUrl(url, fileName)

    // ModuleUI interface methods - delegate to UI interface
    @Composable
    override fun getIcon(): Painter {
        return getCirclesIcon()
    }


    // Sidebar widget implementation
    @Composable
    override fun SidebarWidget(onExpanded: (() -> Unit)?) {
        CirclePacksWidget(this)
    }
  }

