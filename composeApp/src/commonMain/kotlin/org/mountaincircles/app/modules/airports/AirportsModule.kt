package org.mountaincircles.app.modules.airports

import kotlinx.coroutines.launch
import org.mountaincircles.app.network.createDownloadManager
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.utils.DownloadStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.error.AppError
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.error.ErrorHandler
import org.mountaincircles.app.modules.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.painter.Painter
import org.mountaincircles.app.modules.airports.logic.data.AirportsState
import org.mountaincircles.app.modules.airports.logic.data.AirportsImportDisplayData
import org.mountaincircles.app.modules.airports.logic.data.AirportsFilterDisplayData
import org.mountaincircles.app.modules.airports.logic.data.AirportsLayerDisplayData
import org.mountaincircles.app.modules.airports.logic.data.AirportsPopupDisplayData
import org.mountaincircles.app.modules.airports.overlay.ui.AirportsPopupOverlay
import org.mountaincircles.app.ui.overlay.MapOverlayRegistry
import org.mountaincircles.app.modules.airports.submenu.ui.AirportsFilterWidget
import org.mountaincircles.app.modules.airports.logic.initialization.initializeAirports
import org.mountaincircles.app.modules.airports.settings.registerAirportsSettings
import org.mountaincircles.app.modules.airports.settings.logic.AirportsSettingsProvider
import org.mountaincircles.app.ui.settings.ModuleSettingsRegistry
import org.mountaincircles.app.modules.airports.logic.data.AirportSources
import org.mountaincircles.app.modules.airports.logic.data.toUnifiedProgress
import org.mountaincircles.app.ui.components.UnifiedProgress
import org.mountaincircles.app.modules.airports.import.logic.AirportsStorage
import org.mountaincircles.app.modules.airports.import.logic.AirportsDownloadManager
import org.mountaincircles.app.modules.airports.logic.business.AirportsBusinessService
import org.mountaincircles.app.settings.Setting
import org.mountaincircles.app.settings.SettingPersistence
import org.mountaincircles.app.settings.SettingType
import org.mountaincircles.app.state.getGlobalState

// ✅ URI-ONLY APPROACH: Display settings data class

/**
 * Airports Module - Basic structure for airport data management
 * Minimal implementation for registration testing
 */

/**
 * Data class containing only the fields needed for settings observation
 * Used to optimize reactive programming by avoiding unnecessary state changes
 */
private data class SettingsData(
    val airportsVisibility: Boolean,
    val airportIconSize: Float,
    val airportIconsMinZoom: Float,
    val airportLabelSize: Float,
    val airportLabelsMinZoom: Float,
    val hasData: Boolean
)

@Stable
class AirportsModule : BaseStatefulModule<AirportsState>(), PopupClosable {


    override val hasTopMenuButton = true

    override fun getTopMenuButtonType(): org.mountaincircles.app.modules.TopMenuButtonType = org.mountaincircles.app.modules.TopMenuButtonType.TOGGLE

    override fun getButtonAction(): () -> Unit = {
        CoroutineScope(Dispatchers.Main).launch {
            Logger.log("AIRPORTS", LogLevel.INFO, "Airport toggle button clicked via generic system")
            this@AirportsModule.toggleVisibility()
        }
    }

    @Composable
    override fun getButtonIcon(): Painter {
        val airportsState = airportsState.collectAsState().value
        val isVisible = airportsState.airportsVisibility

        return when {
            isVisible -> AppIcons.Airport()   // Layer is visible - will be white
            else -> AppIcons.AirportOff()  // Layer is hidden - will be grey
        }
    }


    /**
     * Toggle airport layer visibility
     */
    suspend fun toggleVisibility() {
        val currentVisibility = currentState.airportsVisibility
        val newVisibility = !currentVisibility
        updateState { it.copy(airportsVisibility = newVisibility) }
        settingPersistence.saveBoolean("airportsVisibility", newVisibility)

        // Close airport popup when visibility is turned off
        if (!newVisibility) {
            hideAirportPopup()
        }

        Logger.log("AIRPORTS", LogLevel.INFO, "Airport visibility toggled: $currentVisibility -> $newVisibility")
    }
    override val hasMainMenuButton = true  // ✅ Has main menu button
    override val hasSidebarWidget = true    // ✅ Has sidebar widget
    override val hasSettingsPanel = true    // ✅ Has settings panel
    override val hasImportSheet = true      // ✅ Has import sheet

    init {
        // Initialize state immediately so moduleState is available during module creation
        initializeState(AirportsState())
    }

    // Internal helper for safe state access
    internal val currentState: AirportsState get() = state.value

    // Download infrastructure (initialized during module setup)
    internal lateinit var storage: AirportsStorage
    internal lateinit var downloadManager: AirportsDownloadManager
    internal lateinit var businessService: AirportsBusinessService

    // Download state management (cloned from airspace)
    private val downloadStateManager = DownloadStateManager("AIRPORTS")

    // ✅ GENERIC: SettingPersistence for airports settings (initialized in onInitialize)
    internal lateinit var settingPersistence: SettingPersistence

    // ✅ GENERIC: Complete settings list for all airports settings
    internal val persistentStateKeys = listOf(
        Setting("airportIconSize", 6.0f, SettingType.FLOAT),
        Setting("airportLabelSize", 12.0f, SettingType.FLOAT),
        Setting("airportIconsMinZoom", 6.0f, SettingType.FLOAT),
        Setting("airportLabelsMinZoom", 8.0f, SettingType.FLOAT),
        Setting("airportsVisibility", true, SettingType.BOOLEAN),
        Setting("currentVisibleTypes", "", SettingType.STRING),
        Setting("disabledAirportIds", "", SettingType.STRING),
        Setting("selectedCountries", "", SettingType.STRING)
    )

    // Public accessors for business service (cloned from airspace)
    val airportsStorage: AirportsStorage get() = storage
    val airportsDownloadManagerInstance: AirportsDownloadManager get() = downloadManager
    val airportsBusinessService: AirportsBusinessService get() = businessService
    val layerManagerInstance: org.mountaincircles.app.modules.airports.layer.ui.AirportsLayerManager? get() = (layerManager as? AirportsLayerManagerAdapter)?.layerManager

    override val moduleId = "airports"
    override val displayName = "Airports"
    override val moduleInitializationOrder = 3 // Controls module initialization order
    override val sidebarWidgetOrder = 2 // Controls sidebar widget display order
    override val mainMenuOrder: Int = 3 // Controls main menu ordering (lower = first)

    // Public state access (use inherited state from BaseStatefulModule)
    val airportsState: StateFlow<AirportsState> get() = state

    // ✅ AIRSPACE-STYLE: Simple layer visibility (like Airspace)
    val layerVisibility: StateFlow<Boolean> = state.map { it.airportsVisibility }.stateIn(
        scope = org.mountaincircles.app.utils.ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    // ✅ RENDERING SETTINGS: Direct StateFlows for layer settings
    val iconSize: StateFlow<Float> = state.map { it.airportIconSize }.stateIn(
        scope = org.mountaincircles.app.utils.ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = 6.0f
    )

    val iconsMinZoom: StateFlow<Float> = state.map { it.airportIconsMinZoom }.stateIn(
        scope = org.mountaincircles.app.utils.ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = 6.0f
    )

    val labelSize: StateFlow<Float> = state.map { it.airportLabelSize }.stateIn(
        scope = org.mountaincircles.app.utils.ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = 12.0f
    )

    val labelsMinZoom: StateFlow<Float> = state.map { it.airportLabelsMinZoom }.stateIn(
        scope = org.mountaincircles.app.utils.ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = 8.0f
    )

    val disabledAirportIds: StateFlow<Set<String>> = state.map { it.disabledAirportIds }.stateIn(
        scope = org.mountaincircles.app.utils.ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )

    val currentVisibleTypes: StateFlow<Set<String>> = state.map { it.currentVisibleTypes }.stateIn(
        scope = org.mountaincircles.app.utils.ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )

    // ✅ MapLibre filtering: No filtered URI needed - raw data used with dynamic filtering

    // Progress flow for import UI - maps currentProgress to UnifiedProgress
    val progressFlow: StateFlow<org.mountaincircles.app.ui.components.UnifiedProgress> = state.map { state ->
        state.currentProgress?.toUnifiedProgress() ?: org.mountaincircles.app.ui.components.UnifiedProgress.idle()
    }.stateIn(
        scope = org.mountaincircles.app.utils.ScopeManager.computationScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = org.mountaincircles.app.ui.components.UnifiedProgress.idle()
    )

    // UI state derived from unified state
    override val uiState: StateFlow<ModuleUIState> = ModuleBase.createUIStateFlow(
        state,
        hasContentPredicate = { it.hasDataToRender ?: false },
        isLoadingPredicate = { (it as? AirportsState)?.isDownloading ?: false }
    )

    // ✅ STANDARDIZED STATE MANAGEMENT: Validation logic
    override fun validateState(state: AirportsState): List<String> {
        val errors = mutableListOf<String>()
        // Add any validation logic here if needed
        return errors
    }

    // Country selection management (following airspace pattern)
    fun updateSelectedCountries(countryCodes: List<String>) {
        Logger.log("AIRPORTS", LogLevel.INFO, "Updating selected countries: $countryCodes")

        // Update state with new countries
        updateState { it.copy(selectedCountries = countryCodes) }

        // Persist the selected countries
        val countriesString = countryCodes.joinToString(",")
        org.mountaincircles.app.utils.ScopeManager.ioScope.launch {
            settingPersistence.saveString("selectedCountries", countriesString)
        }

        Logger.log("AIRPORTS", LogLevel.DEBUG, "Selected countries updated and persisted: ${countryCodes.size} countries")
    }

    /**
     * Clean up visible types after import - keep only types that exist in new data
     */
    internal fun cleanupVisibleTypes(currentVisibleTypes: Set<String>, newAvailableTypes: Set<String>): Set<String> {
        // Keep only visible types that still exist in the new data
        return currentVisibleTypes.filter { it in newAvailableTypes }.toSet()
    }


    fun toggleAirportType(type: String, visible: Boolean) {
        businessService.toggleAirportType(type, visible)
    }

    // Download pipeline - cloned from airspace pattern
    suspend fun downloadAirportsPipeline() {
        val downloadKey = "airports_import"

        // Execute download pipeline using centralized manager (async version to avoid double execution bug)
        val downloadJob = downloadStateManager.executeDownloadAsync(downloadKey) {
            try {
                Logger.log("AIRPORTS", LogLevel.INFO, "Starting airports download pipeline")

                // Execute the airports import
                val result = businessService.importAirportsForSelectedCountries()

                when {
                    result.isSuccess -> {
                        Logger.log("AIRPORTS", LogLevel.INFO, "Airports download pipeline completed successfully")

                        // Reset error state on success
                        updateState {
                            it.copy(
                                hasError = false,
                                errorMessage = null
                            )
                        }

                        Logger.log("AIRPORTS", LogLevel.INFO, "Airports import completed successfully")
                    }
                    else -> {
                        val error = result.exceptionOrNull() ?: Exception("Unknown import error")
                        Logger.log("AIRPORTS", LogLevel.ERROR, "Airports download pipeline failed: ${error.message}", error)

                        // Set error state
                        updateState {
                            it.copy(
                                hasError = true,
                                errorMessage = error.message
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                val error = AppError.ModuleError(moduleId, "Download pipeline failed: ${e.message}", e)
                ErrorHandler.handle(error, "AirportsModule.downloadAirports")
                updateState {
                    it.copy(
                        hasError = true,
                        errorMessage = error.getUserMessage()
                    )
                }
            }
        }

        Logger.log("AIRPORTS", LogLevel.DEBUG, "Airports download pipeline job submitted")
    }

    // Clear airports data - calls business service
    suspend fun clearAirportsData() {
        Logger.log("AIRPORTS", LogLevel.INFO, "Clearing airports data")

        try {
            val result = businessService.clearAirportsData()
            if (result.isSuccess) {
                Logger.log("AIRPORTS", LogLevel.INFO, "Airports data cleared successfully")
            } else {
                val exception = result.exceptionOrNull() ?: Exception("Unknown clear error")
                val error = AppError.ModuleError(moduleId, "Failed to clear airports data: ${exception.message}", exception)
                ErrorHandler.handle(error, "AirportsModule.clearAirportsData")
                updateState { it.copy(hasError = true, errorMessage = error.getUserMessage()) }
            }
        } catch (e: Exception) {
            val error = AppError.ModuleError(moduleId, "Exception clearing airports data: ${e.message}", e)
            ErrorHandler.handle(error, "AirportsModule.clearAirportsData")
            updateState { it.copy(hasError = true, errorMessage = error.getUserMessage()) }
        }
    }

    // Rescan data - calls business service

    // Check if airports are currently downloading
    fun isAirportsDownloading(): Boolean = downloadStateManager.isDownloading("airports_import")

    // Cancel current airports download
    suspend fun cancelAirportsDownload(): Boolean {
        Logger.log("AIRPORTS", LogLevel.INFO, "Cancelling airports download")
        val cancelled = downloadStateManager.cancelDownload("airports_import")
        if (cancelled) {
            Logger.log("AIRPORTS", LogLevel.INFO, "Airports download cancelled successfully")
            // Reset downloading state
            updateState { it.copy(isDownloading = false) }
        } else {
            Logger.log("AIRPORTS", LogLevel.WARN, "No active airports download to cancel")
        }
        return cancelled
    }

    override suspend fun cleanup() {
        Logger.log("AIRPORTS", LogLevel.INFO, "🧹 AirportsModule cleanup started")

        // Cleanup download state manager
        downloadStateManager.cleanup()

        // ✅ No cache invalidation needed - MapLibre handles filtering dynamically

        Logger.log("AIRPORTS", LogLevel.INFO, "🧹 AirportsModule cleanup completed")
    }

    override fun createLayerManager(): org.mountaincircles.app.modules.ModuleMapLayer? {
        return AirportsLayerManagerAdapter(this)
    }

    /**
     * Start observing settings changes to update the layer manager
     */

    override suspend fun onInitialize() {
        // Initialize basic components
        initializeAirports()

        // Initialize layer manager after initialization is complete
        (layerManager as? AirportsLayerManagerAdapter)?.initializeLayers()

        // Mark module as initialized
        updateState { it.copy(isInitialized = true) }
    }


    // Module actions for main menu
    override fun getModuleActions(): List<ModuleMenuAction> {
        return listOf(
            ModuleMenuAction(
                id = "import_airports_data",
                title = "Import Airport Data",
                description = "Offline airports and landouts",
                getIcon = { AppIcons.Airport() },
                isEnabled = currentState.isInitialized,
                action = {
                    Logger.log("AIRPORTS_UI", LogLevel.INFO, "Import airport data action triggered from main menu")
                    globalState.navigationState.openImportSheet(moduleId)
                }
            )
        )
    }


    // ModuleUI interface implementation
    @Composable
    override fun getIcon(): androidx.compose.ui.graphics.painter.Painter {
        return AppIcons.Airport()
    }

    /**
     * Generic: Load all persisted settings and apply to module state
     */

    // Settings update methods
    suspend fun updateAirportIconSize(size: Float) {
        Logger.log("AIRPORTS", LogLevel.INFO, "Updating airport icon size: $size")
        updateState { it.copy(airportIconSize = size) }
        settingPersistence.saveFloat("airportIconSize", size)
    }

    suspend fun updateAirportLabelSize(size: Float) {
        Logger.log("AIRPORTS", LogLevel.INFO, "Updating airport label size: $size")
        updateState { it.copy(airportLabelSize = size) }
        settingPersistence.saveFloat("airportLabelSize", size)
    }

    suspend fun updateAirportIconsMinZoom(minZoom: Float) {
        Logger.log("AIRPORTS", LogLevel.INFO, "Updating airport icons min zoom: $minZoom")
        updateState { it.copy(airportIconsMinZoom = minZoom) }
        settingPersistence.saveFloat("airportIconsMinZoom", minZoom)
    }

    suspend fun updateAirportLabelsMinZoom(minZoom: Float) {
        Logger.log("AIRPORTS", LogLevel.INFO, "Updating airport labels min zoom: $minZoom")
        updateState { it.copy(airportLabelsMinZoom = minZoom) }
        settingPersistence.saveFloat("airportLabelsMinZoom", minZoom)
    }


    /**
     * Show airport popup with clicked features
     */
    fun showAirportPopup(features: List<org.mountaincircles.app.modules.airports.logic.data.AirportFeatureData>) {
        if (features.isNotEmpty()) {
            val airportId = features.first().id
            // Open centralized popup (exclusive like submenus)
            val globalState = getGlobalState()
            globalState.navigationState.openPopup(org.mountaincircles.app.state.PopupId(moduleId, airportId))
        }
        // Also update module state for backward compatibility
        businessService.showAirportPopup(features)
    }

    /**
     * Hide airport popup
     */
    fun hideAirportPopup() {
        // Close centralized popup (exclusive like submenus)
        val globalState = getGlobalState()
        globalState.navigationState.closePopup()
        // Also update module state for backward compatibility
        businessService.hideAirportPopup()
    }

    // Sidebar widget implementation
    @Composable
    override fun SidebarWidget(onExpanded: (() -> Unit)?) {
        org.mountaincircles.app.modules.airports.submenu.ui.AirportsFilterWidget(this)
    }

    // Adapter to make AirportsLayerManager compatible with ModuleMapLayer
    private inner class AirportsLayerManagerAdapter(private val airportsModule: AirportsModule) : org.mountaincircles.app.modules.ModuleMapLayer {
        val layerManager = org.mountaincircles.app.modules.airports.layer.ui.AirportsLayerManager(airportsModule)

        override suspend fun addLayers() {
            // Layers are added via the LayerManager system, not directly
            Logger.log("AIRPORTS", LogLevel.DEBUG, "Airports layers managed by LayerManager system")
        }

        override suspend fun removeLayers() {
            // Layers are removed via the LayerManager system
            Logger.log("AIRPORTS", LogLevel.DEBUG, "Airports layers removed via LayerManager system")
        }

        override suspend fun updateVisibility(visible: Boolean) {
            // Visibility is handled by the LayerManager system
            Logger.log("AIRPORTS", LogLevel.DEBUG, "Airports layer visibility updated: $visible")
        }

        override fun areLayersAdded(): Boolean {
            // Check if layers are registered with LayerManager
            return layerManager.layerIdsPublic.isNotEmpty()
        }

        suspend fun initializeLayers() {
            layerManager.initializeLayers()
        }
    }

    // PopupClosable implementation
    override fun onPopupClosed() {
        Logger.log("AIRPORTS_POPUP", LogLevel.INFO, "🧹 Generic popup close triggered airports cleanup")
        businessService.hideAirportPopup()
    }
}
