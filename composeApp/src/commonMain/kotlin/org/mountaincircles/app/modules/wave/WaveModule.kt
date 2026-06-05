package org.mountaincircles.app.modules.wave

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.*
import org.mountaincircles.app.state.getGlobalState
import org.mountaincircles.app.utils.DownloadStateManager
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.ui.AppIcons
// Now using BaseStatefulModule for common initialization logic
import org.mountaincircles.app.modules.wave.logic.data.WaveSelection
import org.mountaincircles.app.modules.wave.logic.data.RasterData
import org.mountaincircles.app.modules.wave.logic.data.NavigationData
import org.mountaincircles.app.ui.components.UnifiedProgress
import org.mountaincircles.app.modules.wave.logic.data.toUnifiedProgress
import org.mountaincircles.app.modules.wave.logic.data.ProgressData
import org.mountaincircles.app.modules.wave.layer.ui.WaveLayerManager
import org.mountaincircles.app.modules.wave.import.logic.WaveImportType
import org.mountaincircles.app.modules.wave.logic.data.ImportProgressState
import org.mountaincircles.app.modules.wave.logic.data.WaveState
import org.mountaincircles.app.modules.wave.logic.data.WaveProgress
import org.mountaincircles.app.modules.wave.logic.controllers.WaveNavigationController
import org.mountaincircles.app.modules.wave.logic.controllers.WaveImportController
import org.mountaincircles.app.modules.wave.logic.controllers.WaveManager
import org.mountaincircles.app.modules.wave.logic.controllers.WaveLogic
import org.mountaincircles.app.modules.wave.logic.WaveStateFlows
import org.mountaincircles.app.modules.wave.logic.getWaveModuleActions
import org.mountaincircles.app.modules.wave.logic.ui.getWaveIcon
import org.mountaincircles.app.modules.wave.logic.initialization.initializeWave
import org.mountaincircles.app.modules.wave.logic.business.WaveBusinessService
import org.mountaincircles.app.modules.wave.logic.data.TimeAndPressureDisplayData
import org.mountaincircles.app.modules.wave.logic.data.LayerVisibilityData
import org.mountaincircles.app.modules.wave.logic.data.FontSettingsData
import org.mountaincircles.app.modules.wave.logic.WindVectorCalculator
import org.mountaincircles.app.modules.wave.logic.ScreenUtils
import org.mountaincircles.app.settings.Setting
import org.mountaincircles.app.settings.SettingPersistence
import org.mountaincircles.app.settings.SettingType
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import kotlinx.serialization.json.JsonObject
import org.mountaincircles.app.utils.ScopeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow as KotlinStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map

/**
 * Wave module following Circles pattern with BaseStatefulModule
 * Uses BaseStatefulModule for common initialization logic
 */
class WaveModule : BaseStatefulModule<WaveState>() {


    override val hasTopMenuButton = true  // ✅ Has top menu button
    override val hasMainMenuButton = true  // ✅ Has main menu button
    override val hasSidebarWidget = false   // ❌ No sidebar widget
    override val hasSettingsPanel = true    // ✅ Has settings panel
    override val hasImportSheet = true      // ✅ Has import sheet

    // Top menu button configuration
    override fun getTopMenuButtonType(): TopMenuButtonType = TopMenuButtonType.SUBMENU

    @Composable
    override fun getButtonIcon(): Painter {
        return getWaveIcon()
    }

    override fun getButtonAction(): (() -> Unit)? {
        return null // Handled by submenu system
    }



    init {
        // Initialize state immediately so moduleState is available during module creation
        initializeState(WaveState())
    }

    // State flows (extracted to WaveStateFlows for Phase E)
    private val stateFlows = WaveStateFlows(state)
    override val moduleState: StateFlow<WaveState> get() = stateFlows.moduleState

    // Internal helper for safe state access
    internal val currentState: WaveState get() = state.value

    // 🎯 DOWNLOAD STATE MANAGER: Centralized download lifecycle management
    private val downloadStateManager = DownloadStateManager("WAVE")

    // 🎯 ACTIVE DOWNLOAD JOBS: Store jobs for cancellation
    private val activeDownloadJobs = mutableMapOf<WaveImportType, kotlinx.coroutines.Job>()

    // 🎯 COMMON TIME UTILITY: Consistent time management across all functions
    private fun getCurrentDateTime(): Pair<kotlinx.datetime.Instant, LocalDate> {
        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.UTC).date
        return Pair(now, today)
    }

    // ✅ STATE MANAGEMENT: Now handled by BaseStatefulModule.stateManager

    // ✅ GENERIC: SettingPersistence for wave settings (initialized in onInitialize)
    internal lateinit var settingPersistence: SettingPersistence

    // ✅ GENERIC: Complete settings list for all wave settings
    internal val persistentStateKeys = listOf(
        Setting("opacity", 0.75f, SettingType.FLOAT),
        Setting("mainLabelFontSize", 13.0f, SettingType.FLOAT),
        Setting("subLabelFontSize", 10.0f, SettingType.FLOAT),
        Setting("windBarbSize", 0.5f, SettingType.FLOAT),
        Setting("windSpeedScaleDistortion", 0.3f, SettingType.FLOAT),
        Setting("barbInterval", 10f, SettingType.FLOAT),
        Setting("showZeroWindBarbs", false, SettingType.BOOLEAN),
        Setting("waveVisibility", false, SettingType.BOOLEAN),
        Setting("windLayerVisibility", false, SettingType.BOOLEAN),
        Setting("selectedForecastDate", "", SettingType.STRING),
        Setting("selectedTargetDate", "", SettingType.STRING),
        Setting("selectedHour", 12, SettingType.INT),
        Setting("selectedPressure", 500, SettingType.INT),
        Setting("selectedFilePath", "", SettingType.STRING)
    )

    // Core logic
    internal val waveLogic = WaveLogic()

    // Core logic and managers
    internal val waveManager = WaveManager()

    // Wind vector calculation (after waveManager initialization)
    internal val windVectorCalculator by lazy { WindVectorCalculator(waveManager.getWaveDirectory()) }

    /**
     * Build region-specific file path for wind component files
     */
    private fun buildRegionFilePath(
        forecastDate: String,
        targetDate: String,
        hour: Int,
        pressure: Int,
        component: String,
        region: String
    ): String {
        val hourPadded = hour.toString().padStart(2, '0')
        val filename = "arome_${component}_${region}_${forecastDate}_${targetDate}_${hourPadded}_${pressure}.tiff"
        return "${waveManager.getWaveDirectory()}/$filename"
    }


    // Controllers - Circles pattern with module reference
    private val navigationController = WaveNavigationController(this)
    private val importController = WaveImportController(this)

    // Business Service - handles core business logic
    internal val businessService = WaveBusinessService(this)

    // Module metadata
    override val moduleId: String = "wave"
    override val displayName: String = "Wave - Wind"
    override val moduleInitializationOrder: Int = 2 // Controls module initialization order
    override val sidebarWidgetOrder: Int = 3 // Controls sidebar widget display order
    override val mainMenuOrder: Int = 1 // Controls main menu ordering (lower = first)

    // Public state access (use inherited state from BaseStatefulModule)
    val waveState: StateFlow<WaveState> get() = state

    // BaseStatefulModule required methods
    override fun createLayerManager(): ModuleMapLayer {
        val adapter = WaveLayerManagerAdapter(this)
        // Initialize layers immediately when creating the adapter
        adapter.initializeLayers()
        return adapter
    }

    override suspend fun onInitialize() {
        // Initialize SettingPersistence
        settingPersistence = SettingPersistence(moduleId)

        // Initialize wave module (includes settings registration and loading)
        initializeWave()
    }


    // Adapter to make WaveLayerManager compatible with ModuleMapLayer
    private inner class WaveLayerManagerAdapter(private val waveModule: WaveModule) : ModuleMapLayer {
        private val layerManager = WaveLayerManager(waveModule)

    override suspend fun addLayers() {
            // Layers are added via the LayerManager system, not directly
            Logger.log("WAVE", LogLevel.DEBUG, "Wave layers managed by LayerManager system")
    }

    override suspend fun removeLayers() {
            // Layers are removed via the LayerManager system
            Logger.log("WAVE", LogLevel.DEBUG, "Wave layers removed via LayerManager system")
    }

    override suspend fun updateVisibility(visible: Boolean) {
            // Visibility is handled by the LayerManager system
            Logger.log("WAVE", LogLevel.DEBUG, "Wave layer visibility updated: $visible")
    }

    override fun areLayersAdded(): Boolean {
            // Check if layers are registered with LayerManager
            return layerManager.layerIdsPublic.isNotEmpty()
        }

        fun initializeLayers() {
            layerManager.initializeLayers()
        }

        fun cleanup() {
            // Cleanup is handled by the LayerManager system
        }
    }
    override val uiState: KotlinStateFlow<ModuleUIState> get() = stateFlows.uiState
    val navigationFlow: KotlinStateFlow<NavigationData> get() = stateFlows.navigationFlow
    val progressFlow: KotlinStateFlow<UnifiedProgress> get() = stateFlows.progressFlow
    val entriesFlow: KotlinStateFlow<Int> get() = stateFlows.entriesFlow
    val timeAndPressureDisplayFlow: KotlinStateFlow<TimeAndPressureDisplayData> get() = stateFlows.timeAndPressureDisplayFlow
    val fontSettingsFlow: KotlinStateFlow<FontSettingsData> get() = stateFlows.fontSettingsFlow
    val layerDataFlow: KotlinStateFlow<RasterData> get() = stateFlows.layerDataFlow
    val windLayerVisibilityFlow: KotlinStateFlow<Boolean> get() = stateFlows.windLayerVisibilityFlow
    val windVectorsFeatureCollectionFlow: KotlinStateFlow<FeatureCollection<Point, JsonObject>?> get() = stateFlows.windVectorsFeatureCollectionFlow
    val windBarbSizeFlow: KotlinStateFlow<Float> get() = stateFlows.windBarbSizeFlow
    val windSpeedScaleDistortionFlow: KotlinStateFlow<Float> get() = stateFlows.windSpeedScaleDistortionFlow
    val barbIntervalFlow: KotlinStateFlow<Float> get() = stateFlows.barbIntervalFlow
    val showZeroWindBarbsFlow: KotlinStateFlow<Boolean> get() = stateFlows.showZeroWindBarbsFlow
    val combinedStateFlow: KotlinStateFlow<WaveState> get() = stateFlows.combinedStateFlow

    // ✅ STANDARDIZED STATE MANAGEMENT: Validation logic
    override fun validateState(state: WaveState): List<String> {
        val errors = mutableListOf<String>()
        if (state.entries.any { it.filePath.isBlank() }) {
            errors.add("Wave entry file paths cannot be blank")
        }
        if (state.selection.hour !in 0..23) {
            errors.add("Selection hour must be between 0-23")
        }
        return errors
    }

    fun updateImportProgress(progress: ImportProgressState) {
        updateState { currentState.copy(importProgress = progress) }
    }

    fun updateProgress(progress: WaveProgress?) {
        businessService.updateProgress(progress)
    }

    fun clearProgress() {
        businessService.clearProgress()
    }

    fun resetDownloadState() {
        Logger.log("WAVE", LogLevel.INFO, "🔄 Resetting download state (immediate cleanup)")
        clearProgress()
    }

    /**
     * Calculate and display wind vectors for the current map viewport
     */
    suspend fun calculateWindVectors(cameraPosition: org.maplibre.compose.camera.CameraPosition, barbInterval: Float = 10f, cameraBearing: Float = 0.0f, showZeroWindBarbs: Boolean = false) {
        Logger.log("WIND_VECTORS", LogLevel.INFO, "Starting wind vector calculation")

        try {
            // Get current wave selection to find corresponding U/V files
            val currentSelection = currentState.selection
            if (!currentSelection.isValid()) {
                Logger.log("WIND_VECTORS", LogLevel.WARN, "No valid wave selection for wind calculation")
                return
            }

            // Define regions to process
            val regions = listOf("South", "North", "MiddleEast", "MiddleWest")
            val allFeatures = mutableListOf<org.maplibre.spatialk.geojson.Feature<org.maplibre.spatialk.geojson.Point, kotlinx.serialization.json.JsonObject>>()

            // Construct forecast parameters
            val forecastDateStr = currentSelection.forecastDate
            val targetDateStr = currentSelection.targetDate
            val pressure = currentSelection.pressure

            Logger.log("WIND_VECTORS", LogLevel.INFO, "Processing wind data for regions: $regions")

            // Calculate actual viewport bounds using MapLibre's visible region API
            val cameraState = globalState.currentCameraState.value
            val viewportBounds = if (cameraState != null) {
                windVectorCalculator.extractViewportBounds(cameraState)
            } else {
                Logger.log("WIND_VECTORS", LogLevel.WARN, "Camera state not available for viewport calculation")
                // Fallback bounds
                val centerLat = cameraPosition.target.latitude
                val centerLng = cameraPosition.target.longitude
                listOf(centerLng - 0.1, centerLat - 0.1, centerLng + 0.1, centerLat + 0.1)
            }

            // Calculate geographic spacing once for all regions
            val screenWidthMm = globalState.screenWidthMm.value
            val screenHeightMm = globalState.screenHeightMm.value

            val (geographicSpacingLng, geographicSpacingLat) = ScreenUtils.calculateGeographicSpacing(
                viewportBounds[0], // west
                viewportBounds[1], // south
                viewportBounds[2], // east
                viewportBounds[3], // north
                screenWidthMm,
                screenHeightMm,
                barbInterval.toDouble()
            )

            // Process each region
            for (region in regions) {
                Logger.log("WIND_VECTORS", LogLevel.DEBUG, "Processing region: $region")

                // Build region-specific file paths
                val uFilePath = buildRegionFilePath(forecastDateStr, targetDateStr, currentSelection.hour, pressure, "u", region)
                val vFilePath = buildRegionFilePath(forecastDateStr, targetDateStr, currentSelection.hour, pressure, "v", region)

                // Check if region files exist
                val uFile = java.io.File(uFilePath)
                val vFile = java.io.File(vFilePath)

                if (!uFile.exists() || !vFile.exists()) {
                    Logger.log("WIND_VECTORS", LogLevel.DEBUG, "Region $region files not found - skipping (U: ${uFile.exists()}, V: ${vFile.exists()})")
                    continue
                }

                Logger.log("WIND_VECTORS", LogLevel.DEBUG, "Found region $region files: U=$uFilePath, V=$vFilePath")

                try {
                    // Calculate wind vectors for this region using existing calculator
                    // Now returns List<Feature> directly
                    val regionFeatures = windVectorCalculator.calculateWindVectors(
                        viewportBounds = viewportBounds,
                        uFilePath = uFilePath,
                        vFilePath = vFilePath,
                        barbInterval = barbInterval,
                        geographicSpacingLng = geographicSpacingLng,
                        geographicSpacingLat = geographicSpacingLat,
                        forecastDate = forecastDateStr,
                        targetDate = targetDateStr,
                        hour = currentSelection.hour,
                        pressure = pressure,
                        cameraBearing = cameraBearing,
                        showZeroWindBarbs = showZeroWindBarbs
                    )

                    // Directly accumulate the features
                    if (regionFeatures.isNotEmpty()) {
                        allFeatures.addAll(regionFeatures)
                    }

                    Logger.log("WIND_VECTORS", LogLevel.DEBUG, "Region $region processed: ${regionFeatures.size} features")

                } catch (e: Exception) {
                    Logger.log("WIND_VECTORS", LogLevel.WARN, "Failed to process region $region: ${e.message}")
                    // Continue with other regions
                }
            }

            // Create FeatureCollection from all Feature objects
            val featureCollection = if (allFeatures.isNotEmpty()) {
                FeatureCollection(allFeatures)
            } else {
                null
            }

            Logger.log("WIND_VECTORS", LogLevel.INFO, "Combined wind data from ${regions.size} regions: ${allFeatures.size} total features")

            // Store FeatureCollection in state
            updateState { currentState ->
                currentState.copy(windVectorsFeatureCollection = featureCollection)
            }

            Logger.log("WIND_VECTORS", LogLevel.INFO, "Wind vector calculation completed")

        } catch (e: Exception) {
            Logger.log("WIND_VECTORS", LogLevel.ERROR, "Failed to calculate wind vectors: ${e.message}", e)
        }
    }

    private fun updateWaveVisibility(visible: Boolean) {
        updateState { it.copy(isVisible = visible) }
    }

    /**
     * Generic: Save all settings to persistence
     */

    suspend fun updateOpacity(opacity: Float) {
        businessService.updateOpacity(opacity)
        settingPersistence.saveFloat("opacity", opacity)
    }

    suspend fun updateSelection(newSelection: WaveSelection, persist: Boolean = true) {
        businessService.updateSelection(newSelection)

        // Clear cached GeoTIFF metadata when selection changes (different forecast/time)
        windVectorCalculator.invalidateMetadataCache()

        // Persist the selection (unless loading from persistence)
        if (persist) {
            settingPersistence.saveString("selectedForecastDate", newSelection.forecastDate)
            settingPersistence.saveString("selectedTargetDate", newSelection.targetDate)
            settingPersistence.saveInt("selectedHour", newSelection.hour)
            settingPersistence.saveInt("selectedPressure", newSelection.pressure)
            settingPersistence.saveString("selectedFilePath", newSelection.filePath)
        }
    }


    fun setEntries(entries: List<org.mountaincircles.app.modules.wave.logic.data.WaveEntry>) {
        businessService.setEntries(entries)
    }

    fun setCacheClearRequested(requested: Boolean) {
        updateState { currentState.copy(cacheClearRequested = requested) }
    }

    // Managers - now handled by BaseStatefulModule
    // layerManager is now managed by BaseStatefulModule

    override suspend fun onCleanup() {
        Logger.log("WAVE", LogLevel.INFO, "Cleaning up wave module resources")

        try {
            // Settings are auto-saved by controllers, layer cleanup handled by BaseStatefulModule
            Logger.log("WAVE", LogLevel.INFO, "Wave module cleanup completed")
        } catch (e: Exception) {
            Logger.log("WAVE", LogLevel.ERROR, "Error during wave cleanup: ${e.message}", e)
        }
    }

    // ModuleMapLayer methods now handled by WaveLayerManagerAdapter in BaseStatefulModule

    // ModuleUI interface
    @Composable
    override fun getIcon(): Painter {
        // ✅ Simple static icon like Circles module
        return getWaveIcon()
    }



    // Public API methods - simplified for Phase 1
    suspend fun toggleVisibility() {
        val currentVisibility = moduleState.value.isVisible
        val newVisibility = !currentVisibility
        updateWaveVisibility(newVisibility)
        settingPersistence.saveBoolean("waveVisibility", newVisibility)
        Logger.log("WAVE", LogLevel.INFO, "Wave visibility toggled: $currentVisibility -> $newVisibility")
    }

    suspend fun toggleWindLayerVisibility() {
        val currentVisibility = moduleState.value.windLayerVisible
        val newVisibility = !currentVisibility
        updateState { it.copy(windLayerVisible = newVisibility) }
        // Wind visibility is not persistent - always starts as false
        Logger.log("WAVE", LogLevel.INFO, "Wind layer visibility toggled: $currentVisibility -> $newVisibility")

        // REMOVED: Direct calculation call - let state change trigger MapContainer recalculation
    }

    suspend fun updateMainLabelFontSize(fontSize: Float) {
        businessService.updateMainLabelFontSize(fontSize)
        settingPersistence.saveFloat("mainLabelFontSize", fontSize)
    }

    suspend fun updateSubLabelFontSize(fontSize: Float) {
        businessService.updateSubLabelFontSize(fontSize)
        settingPersistence.saveFloat("subLabelFontSize", fontSize)
    }

    suspend fun updateWindBarbSize(size: Float) {
        updateState { it.copy(windBarbSize = size) }
        settingPersistence.saveFloat("windBarbSize", size)
    }

    suspend fun updateWindSpeedScaleDistortion(distortion: Float) {
        updateState { it.copy(windSpeedScaleDistortion = distortion) }
        settingPersistence.saveFloat("windSpeedScaleDistortion", distortion)
    }

    suspend fun updateBarbInterval(interval: Float) {
        updateState { it.copy(barbInterval = interval) }
        settingPersistence.saveFloat("barbInterval", interval)
    }

    suspend fun updateShowZeroWindBarbs(show: Boolean) {
        updateState { it.copy(showZeroWindBarbs = show) }
        settingPersistence.saveBoolean("showZeroWindBarbs", show)
    }

    // Navigation methods - delegated to controller
    suspend fun stepPressure(delta: Int) = navigationController.stepPressure(delta)
    suspend fun stepHour(delta: Int) = navigationController.stepHour(delta)
    suspend fun navigateToNow() = navigationController.navigateToNow()
    suspend fun setForecastDate(forecastDate: String) = navigationController.setForecastDate(forecastDate)

    // 🎯 SINGLE PIPELINE: Download wave data with complete pipeline in one coroutine
    // 🎯 DOWNLOAD STATE MANAGER: Download wave with complete pipeline using centralized manager
    suspend fun downloadWavePipeline(importType: WaveImportType, includeWindFiles: Boolean = false, selectedWindRegions: Set<org.mountaincircles.app.modules.wave.ui.WindRegion> = emptySet()): kotlinx.coroutines.Job? {
        val importTypeKey = importType.name

        // Set active download state at start
        updateState {
            it.copy(
                activeDownloadType = importType,
                isDownloadActive = true
            )
        }

        // Execute download pipeline using centralized manager (async version)
        val downloadJob = downloadStateManager.executeDownloadAsync(importTypeKey) {
            try {
                Logger.log("WAVE", LogLevel.INFO, "Starting wave download pipeline for type: $importType")

                // 🎯 PHASE 1: Import the wave data
                importController.importWaves(importType, includeWindFiles, selectedWindRegions)

                Logger.log("WAVE", LogLevel.INFO, "Wave download pipeline completed successfully for type: $importType")

                // Update state to reflect completion
                updateState {
                    it.copy(
                        isDownloading = false,
                        currentProgress = null,
                        hasError = false,
                        errorMessage = null,
                        activeDownloadType = null,  // Clear active download
                        isDownloadActive = false    // No download active
                    )
                }

            } catch (e: Exception) {
                Logger.log("WAVE", LogLevel.ERROR, "Wave download pipeline failed for type $importType: ${e.message}", e)
                updateState {
                    it.copy(
                        isDownloading = false,
                        hasError = true,
                        errorMessage = e.message ?: "Download failed",
                        activeDownloadType = null,  // Clear active download on error
                        isDownloadActive = false    // No download active
                    )
                }
                throw e // Re-throw to let the async handler catch it
            }
        }

        // Store the job for cancellation (if it was created)
        if (downloadJob != null) {
            activeDownloadJobs[importType] = downloadJob
        }

        // Return the download job for potential cancellation
        return downloadJob
    }

    // 🎯 CANCEL DOWNLOAD: Cancel download for specific import type
    fun cancelDownload(importType: WaveImportType) {
        Logger.log("WAVE", LogLevel.INFO, "Cancelling download for type: $importType")

        // Cancel the actual download job if it exists
        val downloadJob = activeDownloadJobs[importType]
        if (downloadJob != null && downloadJob.isActive) {
            Logger.log("WAVE", LogLevel.INFO, "Cancelling download job for $importType")
            downloadJob.cancel()
            activeDownloadJobs.remove(importType)
        }

        // Update state to reflect cancellation
        updateState {
            it.copy(
                activeDownloadType = null,  // Clear active download
                isDownloadActive = false    // No download active
            )
        }
    }

    // 🎯 DOWNLOAD STATE MANAGER: Check if wave type is currently downloading using centralized manager
    fun isWaveDownloading(importType: WaveImportType): Boolean = downloadStateManager.isDownloading(importType.name)

    // Check if specific forecast type is available (has files for its forecast date + target date)
    fun isWaveForecastAvailable(importType: WaveImportType): Boolean {
        val waveState = currentState
        if (waveState.entries.isEmpty()) return false

        val (now, today) = getCurrentDateTime()

        val forecastDate: LocalDate
        val targetDate: LocalDate

        when (importType) {
            WaveImportType.TODAY -> {
                forecastDate = today
                targetDate = today
            }
            WaveImportType.TOMORROW -> {
                forecastDate = today
                targetDate = today.plus(1, DateTimeUnit.DAY)
            }
            WaveImportType.YESTERDAY_FOR_TODAY -> {
                forecastDate = today.minus(1, DateTimeUnit.DAY)
                targetDate = today
            }
        }

        val forecastDateStr = forecastDate.toString()
        val targetDateStr = targetDate.toString()

        return waveState.entries.any { entry ->
            entry.forecastDate == forecastDateStr && entry.targetDate == targetDateStr
        }
    }

    // Get file count for specific forecast type
    fun getWaveForecastFileCount(importType: WaveImportType): Int {
        val waveState = currentState
        if (waveState.entries.isEmpty()) return 0

        val (now, today) = getCurrentDateTime()

        val forecastDate: LocalDate
        val targetDate: LocalDate

        when (importType) {
            WaveImportType.TODAY -> {
                forecastDate = today
                targetDate = today
            }
            WaveImportType.TOMORROW -> {
                forecastDate = today
                targetDate = today.plus(1, DateTimeUnit.DAY)
            }
            WaveImportType.YESTERDAY_FOR_TODAY -> {
                forecastDate = today.minus(1, DateTimeUnit.DAY)
                targetDate = today
            }
        }

        val forecastDateStr = forecastDate.toString()
        val targetDateStr = targetDate.toString()

        return waveState.entries.count { entry ->
            entry.forecastDate == forecastDateStr && entry.targetDate == targetDateStr
        }
    }

    // Get wave file count (VV files only) for specific forecast type
    fun getWaveForecastWaveFileCount(importType: WaveImportType): Int {
        val waveState = currentState
        if (waveState.entries.isEmpty()) return 0

        val (now, today) = getCurrentDateTime()

        val forecastDate: LocalDate
        val targetDate: LocalDate

        when (importType) {
            WaveImportType.TODAY -> {
                forecastDate = today
                targetDate = today
            }
            WaveImportType.TOMORROW -> {
                forecastDate = today
                targetDate = today.plus(1, DateTimeUnit.DAY)
            }
            WaveImportType.YESTERDAY_FOR_TODAY -> {
                forecastDate = today.minus(1, DateTimeUnit.DAY)
                targetDate = today
            }
        }

        val forecastDateStr = forecastDate.toString()
        val targetDateStr = targetDate.toString()

        return waveState.entries.count { entry ->
            entry.forecastDate == forecastDateStr &&
            entry.targetDate == targetDateStr &&
            entry.filePath.contains("_vv_")  // Only VV (wave) files
        }
    }

    // Get wind file count (U+V files only) for specific forecast type
    fun getWaveForecastWindFileCountByRegion(importType: WaveImportType): Map<org.mountaincircles.app.modules.wave.ui.WindRegion, Int> {
        val waveState = currentState
        if (waveState.entries.isEmpty()) return emptyMap()

        val (now, today) = getCurrentDateTime()

        val forecastDate: LocalDate
        val targetDate: LocalDate

        when (importType) {
            WaveImportType.TODAY -> {
                forecastDate = today
                targetDate = today
            }
            WaveImportType.TOMORROW -> {
                forecastDate = today
                targetDate = today.plus(1, DateTimeUnit.DAY)
            }
            WaveImportType.YESTERDAY_FOR_TODAY -> {
                forecastDate = today.minus(1, DateTimeUnit.DAY)
                targetDate = today
            }
        }

        val forecastDateStr = forecastDate.toString()
        val targetDateStr = targetDate.toString()

        return org.mountaincircles.app.modules.wave.ui.WindRegion.values().associateWith { region ->
            val regionName = when (region) {
                org.mountaincircles.app.modules.wave.ui.WindRegion.NORTH -> "North"
                org.mountaincircles.app.modules.wave.ui.WindRegion.SOUTH -> "South"
                org.mountaincircles.app.modules.wave.ui.WindRegion.MIDDLE_EAST -> "MiddleEast"
                org.mountaincircles.app.modules.wave.ui.WindRegion.MIDDLE_WEST -> "MiddleWest"
            }

            waveState.entries.count { entry ->
                entry.forecastDate == forecastDateStr &&
                entry.targetDate == targetDateStr &&
                (entry.filePath.contains("_u_${regionName}_") || entry.filePath.contains("_v_${regionName}_"))
            }
        }.filterValues { it > 0 } // Only include regions with files
    }

    fun getWaveForecastWindFileCount(importType: WaveImportType): Int {
        val waveState = currentState
        if (waveState.entries.isEmpty()) return 0

        val (now, today) = getCurrentDateTime()

        val forecastDate: LocalDate
        val targetDate: LocalDate

        when (importType) {
            WaveImportType.TODAY -> {
                forecastDate = today
                targetDate = today
            }
            WaveImportType.TOMORROW -> {
                forecastDate = today
                targetDate = today.plus(1, DateTimeUnit.DAY)
            }
            WaveImportType.YESTERDAY_FOR_TODAY -> {
                forecastDate = today.minus(1, DateTimeUnit.DAY)
                targetDate = today
            }
        }

        val forecastDateStr = forecastDate.toString()
        val targetDateStr = targetDate.toString()

        return waveState.entries.count { entry ->
            entry.forecastDate == forecastDateStr &&
            entry.targetDate == targetDateStr &&
            (entry.filePath.contains("_u_") || entry.filePath.contains("_v_"))  // U or V (wind) files
        }
    }

    // Get total wave files in memory (all VV files, any date/forecast type)
    fun getTotalWaveFilesInMemory(): Int {
        val waveState = currentState
        if (waveState.entries.isEmpty()) return 0

        return waveState.entries.count { entry ->
            entry.filePath.contains("_vv_")  // All VV (wave) files in memory
        }
    }

    // Get total wind files in memory (all U+V files, any date/forecast type)
    fun getTotalWindFilesInMemory(): Int {
        val waveState = currentState
        if (waveState.entries.isEmpty()) return 0

        return waveState.entries.count { entry ->
            entry.filePath.contains("_u_") || entry.filePath.contains("_v_")  // All U or V (wind) files in memory
        }
    }

    // Get total size of all wave files in memory (VV files)
    fun getTotalWaveFileSize(): Long {
        val waveState = currentState
        if (waveState.entries.isEmpty()) return 0L

        return waveState.entries
            .filter { entry -> entry.filePath.contains("_vv_") }
            .sumOf { entry -> waveManager.getFileSize(entry.filePath) }
    }

    // Get total size of all wind files in memory (U+V files)
    fun getTotalWindFileSize(): Long {
        val waveState = currentState
        if (waveState.entries.isEmpty()) return 0L

        return waveState.entries
            .filter { entry -> entry.filePath.contains("_u_") || entry.filePath.contains("_v_") }
            .sumOf { entry -> waveManager.getFileSize(entry.filePath) }
    }

    // 🎯 DOWNLOAD STATE MANAGER: Cancel active download using centralized manager
    fun cancelWaveDownload(importType: WaveImportType) {
        val jobKey = importType.name
        val cancelled = downloadStateManager.cancelDownload(jobKey)
        if (cancelled) {
            Logger.log("WAVE", LogLevel.INFO, "Cancelled download for type: $importType")

            // Update state
            updateState {
                it.copy(
                    isDownloading = false,
                    currentProgress = null
                )
            }
        }
    }

    // Cancel all active wave downloads
    fun cancelAllWaveDownloads(): Int {
        val cancelledCount = downloadStateManager.cancelAllDownloads()
        if (cancelledCount > 0) {
            Logger.log("WAVE", LogLevel.INFO, "Cancelled $cancelledCount active downloads")

            // Update state
            updateState {
                it.copy(
                    isDownloading = false,
                    currentProgress = null
                )
            }
        }
        return cancelledCount
    }

    // 🎯 DOWNLOAD STATE MANAGER: Cleanup active downloads using centralized manager
    fun cleanupActiveDownloads() {
        downloadStateManager.cleanup()
    }

    // Import methods - delegated to controller (legacy)
    suspend fun importWaves(importType: WaveImportType) = importController.importWaves(importType)
    suspend fun clearAllFiles() = importController.clearAllFiles()
    suspend fun rescanFiles() = importController.rescanFiles()

    // Module actions
    override fun getModuleActions(): List<ModuleMenuAction> {
        val state = moduleState.value
        return getWaveModuleActions(moduleId, state.isInitialized)
    }

}