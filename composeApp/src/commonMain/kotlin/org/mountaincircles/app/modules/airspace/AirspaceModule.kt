package org.mountaincircles.app.modules.airspace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.settings.Setting
import org.mountaincircles.app.settings.SettingPersistence
import org.mountaincircles.app.settings.SettingType
import org.mountaincircles.app.state.getGlobalState
import org.mountaincircles.app.utils.DownloadStateManager
import org.mountaincircles.app.modules.*
import org.mountaincircles.app.modules.BaseStatefulModule
import org.mountaincircles.app.modules.airspace.import.logic.AirspaceDownloadManager
import org.mountaincircles.app.modules.airspace.import.logic.OpenAirParser
import org.mountaincircles.app.modules.airspace.import.logic.AirspaceStorage
import org.mountaincircles.app.modules.airspace.layer.ui.AirspaceLayerManager
import org.mountaincircles.app.modules.airspace.overlay.ui.AirspacePopupOverlay
import org.mountaincircles.app.modules.airspace.submenu.ui.AirspaceFilterWidget
import org.mountaincircles.app.modules.airspace.logic.ui.getAirspaceIcon
import org.mountaincircles.app.modules.airspace.logic.ui.getAirspaceModuleActions
import org.mountaincircles.app.modules.airspace.logic.ui.getAirspaceButtonAction
import org.mountaincircles.app.modules.airspace.logic.ui.getAirspaceButtonIcon
import org.mountaincircles.app.modules.airspace.logic.initialization.initializeAirspace
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.ui.map.LayerManager
import org.mountaincircles.app.ui.map.LayerZIndex
import org.mountaincircles.app.ui.map.MapClickEvent
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.ui.modules.ComposableProvider
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.mountaincircles.app.ui.overlay.MapOverlayRegistry
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceFeatureData
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceProgress
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceState
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceSources
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceImportDisplayData
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceLayerDisplayData
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceFilterDisplayData
import org.mountaincircles.app.modules.airspace.logic.data.toUnifiedProgress
import org.mountaincircles.app.modules.airspace.logic.business.AirspaceBusinessService
import org.mountaincircles.app.network.DownloadManager
import org.mountaincircles.app.network.createDownloadManager


class AirspaceModule : BaseStatefulModule<AirspaceState>(), PopupClosable {


    override val hasTopMenuButton = true  // ✅ Has top menu button
    override val hasMainMenuButton = true  // ✅ Has main menu button
    override val hasSidebarWidget = true    // ✅ Has sidebar widget
    override val hasSettingsPanel = false   // ❌ No settings panel
    override val hasImportSheet = true      // ✅ Has import sheet

    init {
        // Initialize state immediately so moduleState is available during module creation
        initializeState(AirspaceState())
    }

    // Internal helper for safe state access
    internal val currentState: AirspaceState get() = state.value

    // 🎯 DOWNLOAD STATE MANAGER: Centralized download lifecycle management
    private val downloadStateManager = DownloadStateManager("AIRSPACE")

    // Business Service - handles core business logic
    internal val businessService = AirspaceBusinessService(this)

    override val moduleId: String = "airspace"
    override val displayName: String = "Airspace"
    override val moduleInitializationOrder: Int = 4 // Controls module initialization order
    override val sidebarWidgetOrder: Int = 4 // Controls sidebar widget display order
    override val mainMenuOrder: Int = 4 // Controls main menu ordering (lower = first)

    // Public state access (use inherited state from BaseStatefulModule)
    val airspaceState: StateFlow<AirspaceState> get() = state

    // Override moduleState to pass through hasDataToRender (set directly in business logic)
    override val moduleState: StateFlow<AirspaceState> = state

    // ✅ SETTINGS PERSISTENCE: Generic key-value persistence for airspace settings
    internal val settingPersistence = SettingPersistence(moduleId)

    // ✅ GENERIC: Complete settings list for all airspace settings
    internal val persistentStateKeys = listOf(
        Setting("airspaceVisibility", false, SettingType.BOOLEAN),
        Setting("currentVisibleTypes", "", SettingType.STRING),
        Setting("selectedCountries", "", SettingType.STRING)
    )

    // ✅ SIMPLIFIED REACTIVITY: Only visibility changes
    val layerVisibility: StateFlow<Boolean> = state.map { it.isVisible }.stateIn(
        scope = org.mountaincircles.app.utils.ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val currentVisibleTypes: StateFlow<Set<String>> = state.map { it.currentVisibleTypes }.stateIn(
        scope = org.mountaincircles.app.utils.ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )

    // Reactive UI state - minimal implementation
    override val uiState: StateFlow<ModuleUIState> = ModuleBase.createUIStateFlow(
        state,
        hasContentPredicate = { it.hasDataToRender ?: false },
        isLoadingPredicate = { (it as? AirspaceState)?.isDownloading ?: false }
    )

    // Progress flow for import UI - maps currentProgress to UnifiedProgress
    val progressFlow: StateFlow<org.mountaincircles.app.ui.components.UnifiedProgress> = state.map { state ->
        state.currentProgress?.toUnifiedProgress() ?: org.mountaincircles.app.ui.components.UnifiedProgress.idle()
    }.stateIn(
        scope = org.mountaincircles.app.utils.ScopeManager.computationScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = org.mountaincircles.app.ui.components.UnifiedProgress.idle()
    )

    // BaseStatefulModule required methods
    override fun createLayerManager(): ModuleMapLayer {
        return AirspaceLayerManagerAdapter(this)
    }

    /**
     * Create ViewModel for airspace import UI
     */
    fun createImportViewModel(): org.mountaincircles.app.modules.airspace.ui.AirspaceImportViewModel {
        return org.mountaincircles.app.modules.airspace.ui.AirspaceImportViewModelImpl(this)
    }

    override suspend fun onInitialize() {
        // Initialize airspace module (includes settings registration and loading)
        initializeAirspace()

        // Initialize layer manager after initialization is complete
        (layerManager as? AirspaceLayerManagerAdapter)?.initializeLayers()

        // Mark module as fully initialized
        updateState { it.copy(isInitialized = true) }
    }


    /**
     * Update airspace visibility with persistence
     */
    suspend fun updateVisibility(visible: Boolean) = businessService.updateVisibility(visible)

    // Adapter to make AirspaceLayerManager compatible with ModuleMapLayer
    private inner class AirspaceLayerManagerAdapter(private val airspaceModule: AirspaceModule) : ModuleMapLayer {
        val layerManager = AirspaceLayerManager(airspaceModule)

        override suspend fun addLayers() {
            // Layers are added via the LayerManager system, not directly
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Airspace layers managed by LayerManager system")
        }

        override suspend fun removeLayers() {
            // Layers are removed via the LayerManager system
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Airspace layers removed via LayerManager system")
        }

        override suspend fun updateVisibility(visible: Boolean) {
            // Visibility is handled by the LayerManager system
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Airspace layer visibility updated: $visible")
        }

        override fun areLayersAdded(): Boolean {
            // Check if layers are registered with LayerManager
            return layerManager.layerIdsPublic.isNotEmpty()
        }

        suspend fun initializeLayers() {
            layerManager.initializeLayers()
        }

        fun cleanup() {
            layerManager.cleanup()
        }
    }

    // Managers
    internal lateinit var fileManager: org.mountaincircles.app.io.FileManager
    internal lateinit var parser: OpenAirParser
    internal lateinit var storage: AirspaceStorage
    internal lateinit var downloadManager: DownloadManager
    internal lateinit var airspaceDownloadManager: AirspaceDownloadManager

    // Controllers

    // Public accessors
    val layerManagerInstance: AirspaceLayerManager? get() = (layerManager as? AirspaceLayerManagerAdapter)?.layerManager
    val airspaceStorage: AirspaceStorage get() = storage
    val airspaceDownloadManagerInstance: AirspaceDownloadManager get() = airspaceDownloadManager

    /**
     * Calculate visible types from available types minus disabled types
     */
    // Removed - now using currentVisibleTypes directly

    /**
     * Clean up visible types after import - remove types that no longer exist
     */
    internal fun cleanupVisibleTypes(currentVisibleTypes: Set<String>, newAvailableTypes: Set<String>): Set<String> {
        // Keep only visible types that still exist in the new data
        return currentVisibleTypes.filter { it in newAvailableTypes }.toSet()
    }

    // ✅ STANDARDIZED STATE MANAGEMENT: Validation logic
    override fun validateState(state: AirspaceState): List<String> {
        val errors = mutableListOf<String>()
        if (state.selectedCountries.any { it.isBlank() }) {
            errors.add("Country codes cannot be blank")
        }
        if (state.currentVisibleTypes.any { it.isBlank() }) {
            errors.add("Visible types cannot be blank")
        }
        return errors
    }

    // Allow business controller to update state with new state
    fun updateAirspaceState(newState: AirspaceState) {
        updateState { newState }
    }

    suspend fun saveAirspaceSettings() = businessService.saveAirspaceSettings()

    /**
     * Get or create a filtered airspace file for the given visible types
     */
    // Caching methods removed - filtering is dynamic

    // Public access for generic button system
    fun getLayerManager(): AirspaceLayerManager? = (layerManager as? AirspaceLayerManagerAdapter)?.layerManager
    fun setLayerManager(manager: AirspaceLayerManager?) {
        // This method is no longer needed with BaseStatefulModule pattern
        // Layer manager is created by createLayerManager()
    }



    /**
     * Initialize the airspace module
     */

    /**
     * Public method to rescan for existing airspace data files and log results
     * Similar to circles module's rescanPacks() method
     * Also loads availableTypes for the filter widget
     */

    /**
     * Cleanup module resources
     */

    /**
     * Get module actions for main menu - only the import action
     */
    override fun getModuleActions(): List<ModuleMenuAction> {
        return listOf(
            ModuleMenuAction(
                id = "import_airspace_data",
                title = "Import Airspace Data",
                description = "Offline airspace",
                getIcon = { AppIcons.Airspace() },
                isEnabled = currentState.isInitialized,
                action = {
                    Logger.log("AIRSPACE_UI", LogLevel.INFO, "Import airspace data action triggered from main menu")
                    globalState.navigationState.openImportSheet(moduleId)
                }
            )
        )
    }

    /**
     * 🎯 SINGLE PIPELINE: Download airspace data with complete pipeline in one coroutine
     */
    // 🎯 DOWNLOAD STATE MANAGER: Download airspace with complete pipeline using centralized manager
    suspend fun downloadAirspacePipeline() {
        val downloadKey = "airspace_import"

        // Execute download pipeline using centralized manager (async version to avoid double execution bug)
        val downloadJob = downloadStateManager.executeDownloadAsync(downloadKey) {
            try {
                Logger.log("AIRSPACE", LogLevel.INFO, "Starting airspace download pipeline")

                // 🎯 PHASE 1: Execute the airspace import
                val result = businessService.importAirspaceForSelectedCountries()

                when {
                    result.isSuccess -> {
                        Logger.log("AIRSPACE", LogLevel.INFO, "Airspace download pipeline completed successfully")

                        // Make airspace visible after successful import
                        updateVisibility(true)
                        Logger.log("AIRSPACE", LogLevel.INFO, "Airspace made visible after successful import")

                        // Reset error state on success
                        updateState {
                            it.copy(
                                hasError = false,
                                errorMessage = null
                            )
                        }
                    }
                    else -> {
                        val exception = result.exceptionOrNull()
                        val selectedCountries = currentState.selectedCountries

                        val error = org.mountaincircles.app.modules.airspace.ui.AirspaceError.ImportError(
                            exception?.message ?: "Import failed",
                            selectedCountries,
                            exception
                        )
                        org.mountaincircles.app.error.ErrorHandler.handle(error.toAppError())

                        // Update state with error
                        updateState {
                            it.copy(
                                hasError = true,
                                errorMessage = exception?.message ?: "Import failed"
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                Logger.log("AIRSPACE", LogLevel.ERROR, "Airspace download pipeline failed: ${e.message}", e)

                // Update state with error
                updateState {
                    it.copy(
                        hasError = true,
                        errorMessage = e.message ?: "Download failed"
                    )
                }
            }
        }
    }

    // 🎯 DOWNLOAD STATE MANAGER: Check if airspace is currently downloading using centralized manager
    fun isAirspaceDownloading(): Boolean = downloadStateManager.isDownloading("airspace_import")

    // 🎯 DOWNLOAD STATE MANAGER: Cancel active airspace download using centralized manager
    fun cancelAirspaceDownload() {
        val cancelled = downloadStateManager.cancelDownload("airspace_import")
        if (cancelled) {
            Logger.log("AIRSPACE", LogLevel.INFO, "Cancelled airspace download")

            // Reset download state
            updateState {
                it.copy(
                    hasError = false,
                    errorMessage = null
                )
            }
        }
    }

    // 🎯 DOWNLOAD STATE MANAGER: Cleanup active downloads using centralized manager
    fun cleanupActiveDownloads() {
        downloadStateManager.cleanup()
    }

    /**
     * Import airspace data for selected countries (legacy method)
     */
    suspend fun importAirspaceForSelectedCountries(): Result<Unit> = businessService.importAirspaceForSelectedCountries()

    /**
     * Import airspace data for specific countries (legacy method)
     */
    suspend fun importAirspaceForCountries(countryCodes: List<String>): Result<Unit> = businessService.importAirspaceForCountries(countryCodes.toSet())

    /**
     * Update selected countries
     */
    fun updateSelectedCountries(countryCodes: List<String>) = businessService.updateSelectedCountries(countryCodes)

    /**
     * Clear all airspace data
     */
    suspend fun clearAirspaceData() {
        try {
            Logger.log("AIRSPACE", LogLevel.INFO, "Clearing all airspace data")
            businessService.clearAirspaceData()
            Logger.log("AIRSPACE", LogLevel.INFO, "Airspace data cleared successfully")
        } catch (e: Exception) {
            val error = org.mountaincircles.app.modules.airspace.ui.AirspaceError.ClearError(
                e.message ?: "Failed to clear airspace data",
                e
            )
            org.mountaincircles.app.error.ErrorHandler.handle(error.toAppError())
            throw e // Re-throw to maintain existing behavior
        }
    }

    /**
     * Toggle visibility of a specific airspace type
     */
    fun toggleAirspaceType(type: String, visible: Boolean) = businessService.toggleAirspaceType(type, visible)

    /**
     * Check if airspace data is available
     */
    suspend fun hasAirspaceData(): Boolean = businessService.hasAirspaceData()


    /**
     * Register the settings provider for UI
     */

    /**
     * Toggle airspace layer visibility (following Circles/Wave pattern)
     */
    suspend fun toggleVisibility() {
        val currentVisibility = currentState.isVisible
        val newVisibility = !currentVisibility
        businessService.updateVisibility(newVisibility)
        settingPersistence.saveBoolean("airspaceVisibility", newVisibility)
        Logger.log("AIRSPACE", LogLevel.INFO, "Airspace visibility toggled: $currentVisibility -> $newVisibility")
    }


    /**
     * Show airspace popup with clicked features
     */
    fun showAirspacePopup(features: List<AirspaceFeatureData>) {
        if (features.isNotEmpty()) {
            val featureId = features.first().id.ifBlank { features.first().allProperties["AI"] ?: features.first().allProperties["id"] ?: "unknown" }
            // Open centralized popup (exclusive like submenus)
            val globalState = getGlobalState()
            globalState.navigationState.openPopup(org.mountaincircles.app.state.PopupId(moduleId, featureId))
        }
        // Also update module state for backward compatibility
        businessService.showAirspacePopup(features)
    }

    /**
     * Hide airspace popup
     */
    fun hideAirspacePopup() {
        // Close centralized popup (exclusive like submenus)
        val globalState = getGlobalState()
        globalState.navigationState.closePopup()
        // Also update module state for backward compatibility
        businessService.hideAirspacePopup()
    }



    // ModuleUI interface implementation
    override fun getTopMenuButtonType(): TopMenuButtonType = TopMenuButtonType.TOGGLE

    @Composable
    override fun getIcon(): Painter? {
        return getAirspaceIcon()
    }

    @Composable
    override fun getButtonIcon(): Painter {
        return this.getAirspaceButtonIcon()
    }


    override fun getButtonAction(): () -> Unit {
        return this.getAirspaceButtonAction()
    }




    // Sidebar widget implementation
    @Composable
    override fun SidebarWidget(onExpanded: (() -> Unit)?) {
        AirspaceFilterWidget(this)
    }

    // Settings persistence methods


    // PopupClosable implementation
    override fun onPopupClosed() {
        Logger.log("AIRSPACE_POPUP", LogLevel.INFO, "🧹 Generic popup close triggered airspace cleanup")
        businessService.hideAirspacePopup()
    }
}
