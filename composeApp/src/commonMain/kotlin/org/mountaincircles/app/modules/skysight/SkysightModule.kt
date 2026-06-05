package org.mountaincircles.app.modules.skysight

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.*
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.modules.skysight.layer.ui.SkysightLayerManager
import org.mountaincircles.app.modules.skysight.logic.data.SkysightState
import org.mountaincircles.app.modules.skysight.logic.data.SkysightLayerData
import org.mountaincircles.app.modules.skysight.logic.data.TimePair
import org.mountaincircles.app.modules.skysight.logic.data.TileLayer
import org.mountaincircles.app.modules.skysight.logic.ui.getSkysightButtonAction
import org.mountaincircles.app.modules.skysight.logic.ui.getSkysightButtonIcon
import org.mountaincircles.app.modules.skysight.logic.ui.getSkysightModuleActions
import org.mountaincircles.app.utils.ScopeManager
import org.mountaincircles.app.settings.Setting
import org.mountaincircles.app.settings.SettingPersistence
import org.mountaincircles.app.settings.SettingType
import org.mountaincircles.app.modules.skysight.logic.findDataUrl as findDataUrlHelper
import org.mountaincircles.app.modules.skysight.logic.getLayerDataUrls as getLayerDataUrlsHelper
import org.mountaincircles.app.modules.skysight.logic.getLayerFilterRange as getLayerFilterRangeHelper
import org.mountaincircles.app.modules.skysight.logic.getLocalFilePath as getLocalFilePathHelper
import org.mountaincircles.app.modules.skysight.logic.hasLayerDataUrls as hasLayerDataUrlsHelper
import org.mountaincircles.app.modules.skysight.logic.loadAvailableLayers as loadAvailableLayersInit
import org.mountaincircles.app.modules.skysight.logic.loadAvailableLayersFromPersistence as loadAvailableLayersFromPersistenceInit
import org.mountaincircles.app.modules.skysight.logic.SkysightDownloadOrchestrator
import org.mountaincircles.app.modules.skysight.logic.SkysightStateFlows
import org.mountaincircles.app.modules.skysight.logic.SkysightApiManager
import org.mountaincircles.app.modules.skysight.logic.SkysightStorage
import org.mountaincircles.app.modules.skysight.logic.controllers.SkysightAuthController
import org.mountaincircles.app.modules.skysight.logic.controllers.SkysightDataControllerV2
import org.mountaincircles.app.modules.skysight.logic.controllers.SkysightTilingControllerV2
import org.mountaincircles.app.modules.skysight.logic.controllers.RealTimeTilingController
import org.mountaincircles.app.modules.skysight.logic.SkysightUtils
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.mountaincircles.app.modules.skysight.logic.data.SkysightLayer
import org.mountaincircles.app.modules.skysight.logic.data.SkysightDataUrl
import org.mountaincircles.app.modules.skysight.submenu.ui.SkysightLayerWidget
import androidx.compose.ui.graphics.Color
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.network.createDownloadManager
import org.mountaincircles.app.state.getGlobalState
import kotlinx.datetime.Clock
import org.mountaincircles.app.ui.map.LayerRegistrationHelper
import org.mountaincircles.app.ui.map.LayerZIndex
import androidx.compose.runtime.remember
import org.maplibre.compose.expressions.value.RasterResampling
import org.maplibre.compose.util.PositionQuad
import org.maplibre.spatialk.geojson.Position
import org.maplibre.compose.expressions.dsl.const
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import org.mountaincircles.app.modules.skysight.logic.initialization.initializeSkysight
import org.mountaincircles.app.modules.skysight.settings.registerSkysightSettings
import org.mountaincircles.app.modules.skysight.settings.logic.SkysightSettingsProvider
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import kotlinx.serialization.json.JsonObject
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.painter.Painter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Skysight module - minimal implementation with main menu button only
 */
class SkysightModule : BaseStatefulModule<SkysightState>() {

    override val moduleId = "skysight"
    override val displayName = "SkySight"
    override val moduleInitializationOrder = 6
    override val sidebarWidgetOrder = 12  // Controls sidebar widget display order
    override val mainMenuOrder = 11

    // Declarative UI capabilities - main menu button and import sheet
    override val hasTopMenuButton = true
    override val hasMainMenuButton = true
    override val hasSidebarWidget = true
    override val hasSettingsPanel = true
    override val hasImportSheet = true

    override val controlsReRender = true

    // ✅ GENERIC: SettingPersistence for skysight settings
    internal val settingPersistence = SettingPersistence(moduleId)

    // ✅ GENERIC: Complete settings list for all skysight settings
    internal val persistentStateKeys = listOf(
        Setting("email", "", SettingType.STRING),
        Setting("password", "", SettingType.STRING),
        Setting("isLoggedIn", false, SettingType.BOOLEAN),
        Setting("allowedRegions", "", SettingType.STRING), // JSON array as string
        Setting("selectedRegion", "", SettingType.STRING), // No default - user must choose
        Setting("availableLayers", "", SettingType.STRING), // JSON array as string
        Setting("currentTime", "12:00", SettingType.STRING), // Selected time as HH:MM string
        Setting("isLabelsVisible", true, SettingType.BOOLEAN), // Whether labels are visible
        Setting("layerOpacity", 0.75f, SettingType.FLOAT), // Layer opacity (0.0-1.0)
        Setting("labelSize", 12.0f, SettingType.FLOAT), // Label text size in sp
        Setting("forecastMinZoom", 6.0f, SettingType.FLOAT), // Minimum zoom level for forecast layer and labels
        Setting("waveFilterMin", -0.5f, SettingType.FLOAT), // Wind filter minimum value
        Setting("waveFilterMax", 0.5f, SettingType.FLOAT), // Wind filter maximum value
        Setting("wblmaxminFilterMin", -0.1f, SettingType.FLOAT), // WBL max/min filter minimum value
        Setting("wblmaxminFilterMax", 0.1f, SettingType.FLOAT), // WBL max/min filter maximum value
        Setting("importTimeStart", 14f, SettingType.FLOAT), // Import time range start
        Setting("importTimeEnd", 40f, SettingType.FLOAT), // Import time range end
        Setting("layersToImport", "", SettingType.STRING) // Comma-separated layer IDs to import
    )

    // API Manager for all Skysight API interactions
    private val apiManager = SkysightApiManager().apply {
        setModule(this@SkysightModule)
    }

    // Storage for file operations using generic FileManager
    internal lateinit var storage: SkysightStorage

    // Data calculator for viewport-based data display

    @Composable
    override fun getIcon(): androidx.compose.ui.graphics.painter.Painter {
        return AppIcons.Skysight()
    }

    @Composable
    override fun getButtonIcon(): androidx.compose.ui.graphics.painter.Painter {
        return this.getSkysightButtonIcon()
    }

    override fun getTopMenuButtonType(): TopMenuButtonType = TopMenuButtonType.SUBMENU


    override fun getButtonAction(): (() -> Unit)? = getSkysightButtonAction(this)


    /**
     * Extract viewport bounds from camera state
     */

    private val downloadOrchestrator = SkysightDownloadOrchestrator(this)

    init {
        // Initialize state immediately so moduleState is available during module creation
        initializeState(SkysightState())

        // Load persisted settings asynchronously
        ScopeManager.ioScope.launch {
            initializeSkysight()
        }

        // Initialize download manager lifecycle monitoring
        downloadOrchestrator.initializeDownloadManager()
    }

    // Public reactive state for UI components
    val skysightState: StateFlow<SkysightState> get() = state

    // Internal helper for safe state access
    internal val currentState: SkysightState get() = state.value

    // State flows (defined in SkysightStateFlows for clarity)
    private val stateFlows = SkysightStateFlows(state)
    val email: StateFlow<String> get() = stateFlows.email
    val password: StateFlow<String> get() = stateFlows.password
    val isLoggedIn: StateFlow<Boolean> get() = stateFlows.isLoggedIn
    val hasDataToRender: StateFlow<Boolean> get() = stateFlows.hasDataToRender
    val isLoggingIn: StateFlow<Boolean> get() = stateFlows.isLoggingIn
    val selectedRegion: StateFlow<String> get() = stateFlows.selectedRegion
    val availableLayers: StateFlow<List<SkysightLayer>> get() = stateFlows.availableLayers
    val isLoadingLayers: StateFlow<Boolean> get() = stateFlows.isLoadingLayers
    val selectedLayerId: StateFlow<String> get() = stateFlows.selectedLayerId
    val isVisible: StateFlow<Boolean> get() = stateFlows.isVisible
    val isLabelsVisible: StateFlow<Boolean> get() = stateFlows.isLabelsVisible
    val isDownloading: StateFlow<Boolean> get() = stateFlows.isDownloading
    val cancelledBatchImport: StateFlow<Boolean> get() = stateFlows.cancelledBatchImport
    val satelliteEnabled: StateFlow<Boolean> get() = stateFlows.satelliteEnabled
    val localRainEnabled: StateFlow<Boolean> get() = stateFlows.localRainEnabled
    val layerOpacity: StateFlow<Float> get() = stateFlows.layerOpacity
    val labelSize: StateFlow<Float> get() = stateFlows.labelSize
    val forecastMinZoom: StateFlow<Float> get() = stateFlows.forecastMinZoom
    val waveFilterMin: StateFlow<Float> get() = stateFlows.waveFilterMin
    val waveFilterMax: StateFlow<Float> get() = stateFlows.waveFilterMax
    val wblmaxminFilterMin: StateFlow<Float> get() = stateFlows.wblmaxminFilterMin
    val wblmaxminFilterMax: StateFlow<Float> get() = stateFlows.wblmaxminFilterMax
    val importTimeStart: StateFlow<Float> get() = stateFlows.importTimeStart
    val importTimeEnd: StateFlow<Float> get() = stateFlows.importTimeEnd
    val layersToImport: StateFlow<Set<String>> get() = stateFlows.layersToImport
    val downloadingLayers: StateFlow<Set<String>> get() = stateFlows.downloadingLayers
    val layerImportCounts: StateFlow<Map<String, Pair<Int, Int>>> get() = stateFlows.layerImportCounts
    val selectedDate: StateFlow<kotlinx.datetime.LocalDate?> get() = stateFlows.selectedDate
    val lastSelectedLayer: StateFlow<String> get() = stateFlows.lastSelectedLayer
    val submenuMode: StateFlow<String> get() = stateFlows.submenuMode
    val realTimeTimestamp: StateFlow<kotlinx.datetime.Instant> get() = stateFlows.realTimeTimestamp
    val currentTime: StateFlow<org.mountaincircles.app.modules.skysight.logic.data.TimePair> get() = stateFlows.currentTime
    val viewportDataFlow: StateFlow<FeatureCollection<Point, JsonObject>?> get() = stateFlows.viewportDataFlow
    val layerDataFlow: StateFlow<SkysightLayerData> get() = stateFlows.layerDataFlow

    override val uiState: StateFlow<ModuleUIState> = ModuleBase.createUIStateFlow(
        state,
        hasContentPredicate = { true },
        isLoadingPredicate = { false },
        isVisiblePredicate = { (it as? SkysightState)?.isLoggedIn == true }
    )

    override fun getModuleActions(): List<ModuleMenuAction> = getSkysightModuleActions(this)

    // Methods to update form fields
    suspend fun updateEmail(email: String) {
        SkysightAuthController.updateEmail(this, settingPersistence, email)
    }

    suspend fun updatePassword(password: String) {
        SkysightAuthController.updatePassword(this, settingPersistence, password)
    }

    // Method to update selected layer
    suspend fun updateSelectedForecastLayer(layerId: String) {
        Logger.log("SKYSIGHT", LogLevel.INFO, "Selected layer: $layerId")

        // If clearing forecast layer selection, clear all forecast tiles and hide labels
        if (layerId.isEmpty() && state.value.selectedLayerId.isNotEmpty()) {
            Logger.log("SKYSIGHT", LogLevel.INFO, "Clearing forecast layer selection - clearing all forecast tiles and hiding labels")
            SkysightTilingControllerV2.clearAllExistingTiles(this)
            // Hide labels when forecast layer is deselected
            updateState { it.copy(isLabelsVisible = false) }
        }

        // If selecting a forecast layer (not clearing selection), ensure time is on 30-minute boundary
        if (layerId.isNotEmpty()) {
            // Open submenu when selecting forecast layer
            org.mountaincircles.app.state.getGlobalState().navigationState.openSubmenu("skysight")
            // Close sidebar when forecast layer is selected
            org.mountaincircles.app.state.getGlobalState().navigationState.closeSidebar()

            // val currentTime = state.value.currentTime
            // val currentMinute = currentTime.minute

            // Check if current time is not on 30-minute boundary (not :00 or :30)
            // if (currentMinute != 0 && currentMinute != 30) {
            //     Logger.log("SKYSIGHT", LogLevel.INFO, "Current time ${currentTime.hour}:${currentTime.minute.toString().padStart(2, '0')} not on 30-min boundary, rounding for forecast layer")

            //     // Round to nearest 30-minute boundary (same logic as navigateToNowForecast)
            //     val (newHour, newMinute) = if (currentMinute < 15) {
            //         // Round down to :00
            //         Pair(currentTime.hour, 0)
            //     } else if (currentMinute < 45) {
            //         // Round to :30
            //         Pair(currentTime.hour, 30)
            //     } else {
            //         // Round up to next hour :00
            //         val nextHour = (currentTime.hour + 1) % 24
            //         Pair(nextHour, 0)
            //     }

            //     Logger.log("SKYSIGHT", LogLevel.INFO, "Rounded time to ${newHour.toString().padStart(2, '0')}:${newMinute.toString().padStart(2, '0')} for forecast layer")
            //     updateCurrentTime(org.mountaincircles.app.modules.skysight.logic.data.TimePair(newHour, newMinute))
            // }
        }

        updateState { it.copy(selectedLayerId = layerId) }
    }

    // Helper methods for unified visibility system
    suspend fun selectForecastLayer(layerId: String) {
        updateSelectedForecastLayer(layerId)
    }

    suspend fun enableSatellite(enabled: Boolean) {
        updateState { it.copy(satelliteEnabled = enabled) }
        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Satellite ${if (enabled) "enabled" else "disabled"}")
    }

    suspend fun enableRain(enabled: Boolean) {
        updateState { it.copy(localRainEnabled = enabled) }
        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Rain ${if (enabled) "enabled" else "disabled"}")
    }

    suspend fun updateImportTimeRange(start: Float, end: Float) {
        updateState { it.copy(importTimeStart = start, importTimeEnd = end) }
        settingPersistence.saveFloat("importTimeStart", start)
        settingPersistence.saveFloat("importTimeEnd", end)
    }

    suspend fun updateLayersToImport(layers: Set<String>) {
        updateState { it.copy(layersToImport = layers) }
        settingPersistence.saveString("layersToImport", layers.joinToString(","))
    }

    suspend fun stopBatchDownloads() {
        Logger.log("SKYSIGHT", LogLevel.INFO, "Stopping batch downloads - checking if there was an active batch import")

        // Check if there was actually an active batch import before showing "stopped" message
        val currentState = moduleState.value
        val wasDownloading = currentState.isDownloading || currentState.activeDownloadCount > 0 || currentState.downloadingLayers.isNotEmpty()

        if (wasDownloading) {
            Logger.log("SKYSIGHT", LogLevel.INFO, "Active batch import detected - showing 'batch import stopped' message")
            // Show "batch import stopped" for 2 seconds only if there was an active download
            updateState { it.copy(cancelledBatchImport = true) }
            // Use the module's UI scope to ensure the timer survives sheet disposal
            ScopeManager.uiScope.launch {
                delay(2000)
                updateState { it.copy(cancelledBatchImport = false) }
            }
        } else {
            Logger.log("SKYSIGHT", LogLevel.INFO, "No active batch import - just resetting state")
        }

        // Always reset the downloading state
        updateState { it.copy(
            batchImportCancelled = true,
            isDownloading = false,
            activeDownloadCount = 0,
            downloadingLayers = emptySet()
        ) }
    }

    suspend fun performBatchImport(): Result<Unit> {
        val currentState = moduleState.value
        val layersToImport = currentState.layersToImport
        val timeStart = currentState.importTimeStart
        val timeEnd = currentState.importTimeEnd
        val selectedDate = currentState.selectedDate

        if (layersToImport.isEmpty() || selectedDate == null) {
            return Result.failure(Exception("No layers selected or date not set"))
        }

        // Clear any previous import counts and reset cancellation flag
        updateState { it.copy(layerImportCounts = emptyMap(), batchImportCancelled = false) }

        // Convert slider values back to actual hours for display
        val startHours = timeStart * 0.5f
        val endHours = timeEnd * 0.5f
        val startHour = startHours.toInt()
        val startMinute = ((startHours % 1) * 60).toInt()
        val endHour = endHours.toInt()
        val endMinute = ((endHours % 1) * 60).toInt()

        Logger.log("SKYSIGHT", LogLevel.INFO, "Starting batch import for ${layersToImport.size} layers, time range ${startHour.toString().padStart(2, '0')}:${startMinute.toString().padStart(2, '0')} -> ${endHour.toString().padStart(2, '0')}:${endMinute.toString().padStart(2, '0')}")

        // Set downloading state
        updateState { it.copy(isDownloading = true) }

        try {
            // Generate all timestamps for the time range
            // Convert slider values (0-48) to hours: value * 0.5
            val startTimeHours = timeStart * 0.5f
            val endTimeHours = timeEnd * 0.5f

            val timestamps = mutableListOf<Long>()
            var currentHour = startTimeHours.toInt()
            var currentMinute = ((startTimeHours % 1) * 60).toInt()

            while (true) {
                val dateTime = kotlinx.datetime.LocalDateTime(
                    selectedDate,
                    kotlinx.datetime.LocalTime(currentHour, currentMinute, 0)
                )
                val instant = dateTime.toInstant(kotlinx.datetime.TimeZone.UTC)
                timestamps.add(instant.epochSeconds)

                // Move to next 30-minute interval
                currentMinute += 30
                if (currentMinute >= 60) {
                    currentMinute = 0
                    currentHour += 1
                }

                // Stop when we reach the end time (convert back to hours for comparison)
                val currentTimeHours = currentHour + currentMinute / 60f
                if (currentTimeHours >= endTimeHours) {
                    break
                }
            }

            Logger.log("SKYSIGHT", LogLevel.INFO, "Generated ${timestamps.size} timestamps to import")

            // Pre-fetch URLs for all selected layers (authenticates once per layer as required by API)
            Logger.log("SKYSIGHT", LogLevel.INFO, "Pre-fetching URLs for all selected layers...")
            for (layerId in layersToImport) {
                SkysightDataControllerV2.fetchLayerDataUrls(this, layerId, selectedDate.toString())
            }
            Logger.log("SKYSIGHT", LogLevel.INFO, "URLs pre-fetched, authentication completed for ${layersToImport.size} layers")

            Logger.log("SKYSIGHT", LogLevel.INFO, "Generated ${timestamps.size} timestamps to import")

            // Initialize import counts for each layer
            val layerCounts = mutableMapOf<String, Pair<Int, Int>>() // layerId -> (files_on_filesystem, total_requested)
            layersToImport.forEach { layerId ->
                layerCounts[layerId] = Pair(0, timestamps.size)
            }
            updateState { it.copy(layerImportCounts = layerCounts) }

            // Import each layer for each timestamp (URLs already cached from authenticated pre-fetch)
            var totalImported = 0
            var totalSkipped = 0
            for (layerId in layersToImport) {
                // Check if batch import has been cancelled
                if (moduleState.value.batchImportCancelled) {
                    Logger.log("SKYSIGHT", LogLevel.INFO, "Batch import cancelled, stopping processing of layer $layerId")
                    break
                }

                // Check if layers to import has been cleared (graceful stop)
                val currentLayersToImport = moduleState.value.layersToImport
                if (!currentLayersToImport.contains(layerId)) {
                    Logger.log("SKYSIGHT", LogLevel.INFO, "Layer $layerId removed from import list, skipping")
                    continue
                }

                // Mark layer as downloading
                updateState { it.copy(downloadingLayers = it.downloadingLayers + layerId) }

                for (timestamp in timestamps) {
                    // Check if batch import has been cancelled
                    if (moduleState.value.batchImportCancelled) {
                        Logger.log("SKYSIGHT", LogLevel.INFO, "Batch import cancelled during download of $layerId, stopping")
                        break
                    }

                    // Check if layers to import has been cleared (graceful stop)
                    val stillImporting = moduleState.value.layersToImport.contains(layerId)
                    if (!stillImporting) {
                        Logger.log("SKYSIGHT", LogLevel.INFO, "Layer $layerId removed from import list during download, stopping")
                        break
                    }

                    try {
                        // Check if we already have this file
                        val fileKey = "${layerId}_${timestamp}"
                        if (SkysightDataControllerV2.fileExistsLocally(this, fileKey)) {
                            totalSkipped++

                            // Update the import count for this layer (file already exists)
                            downloadOrchestrator.updateLayerImportCount(layerId, timestamps.size, "skipped")

                            continue // Already have this file
                        }

                        // Get the URL for this specific timestamp (should be cached now)
                        val dataUrl = findDataUrl(layerId, selectedDate.toString(), timestamp)
                        if (dataUrl != null) {
                            // Use the storage download directly to avoid setting downloading state for each file
                            storage.downloadFile(fileKey, dataUrl.link)
                            totalImported++

                            // Update the import count for this layer immediately
                            downloadOrchestrator.updateLayerImportCount(layerId, timestamps.size, "downloaded")

                            Logger.log("SKYSIGHT", LogLevel.DEBUG, "Imported $fileKey")
                        } else {
                            Logger.log("SKYSIGHT", LogLevel.WARN, "No URL available for $layerId at timestamp $timestamp")
                        }
                    } catch (e: Exception) {
                        Logger.log("SKYSIGHT", LogLevel.ERROR, "Failed to import $layerId at timestamp $timestamp: ${e.message}")
                    }
                }

                // Mark layer as finished downloading
                updateState { it.copy(downloadingLayers = it.downloadingLayers - layerId) }
            }

            // After all downloads complete, scan filesystem to count actual files present
            Logger.log("SKYSIGHT", LogLevel.INFO, "Scanning filesystem to verify imported files...")
            val finalLayerCounts = mutableMapOf<String, Pair<Int, Int>>()
            for (layerId in layersToImport) {
                var filesOnFilesystem = 0
                for (timestamp in timestamps) {
                    val fileKey = "${layerId}_${timestamp}"
                    if (SkysightDataControllerV2.fileExistsLocally(this, fileKey)) {
                        filesOnFilesystem++
                    }
                }
                finalLayerCounts[layerId] = Pair(filesOnFilesystem, timestamps.size)
                Logger.log("SKYSIGHT", LogLevel.INFO, "Layer $layerId: $filesOnFilesystem/$timestamps.size files on filesystem")
            }
            updateState { it.copy(layerImportCounts = finalLayerCounts) }

            Logger.log("SKYSIGHT", LogLevel.INFO, "Batch import completed: $totalImported files imported, $totalSkipped files already existed")

            // Clear the downloading state
            updateState { it.copy(isDownloading = false) }

            return Result.success(Unit)

        } catch (e: Exception) {
            Logger.log("SKYSIGHT", LogLevel.ERROR, "Batch import failed: ${e.message}")
            // Clear the downloading state on error
            updateState { it.copy(isDownloading = false) }
            return Result.failure(e)
        }
    }

    // Method to update selected region
    suspend fun updateSelectedRegion(region: String) {
        Logger.log("SKYSIGHT", LogLevel.INFO, "Region changed to: $region, clearing previous selections and loading available layers...")

        // Clear all region-dependent state when switching regions
        updateState { it.copy(
            selectedRegion = region,
            selectedLayerId = "",        // Clear selected layer (will be invalid in new region)
            selectedDate = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date, // Reset to today in UTC
            currentTime = TimePair.DEFAULT, // Reset to noon (default)
            isLoadingLayers = true
        )}

        // Clear persisted settings for region-dependent values
        settingPersistence.saveString("selectedDate", kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString())
        settingPersistence.saveString("currentTime", "12:00")
        settingPersistence.saveString("selectedRegion", region)

        // Then load available layers for this region
        loadAvailableLayersInit(this, region)
    }

    // Login functionality
    suspend fun login(email: String, password: String): Result<Unit> {
        return SkysightAuthController.login(this, apiManager, settingPersistence, email, password)
    }

    // Logout functionality
    suspend fun logout() {
        SkysightAuthController.logout(this, settingPersistence)
    }

    /**
     * Update the layer data URLs for a specific date
     */

    /** Get the layer data URLs for a specific layer and date */
    fun getLayerDataUrls(layerId: String, date: String): List<SkysightDataUrl> =
        getLayerDataUrlsHelper(this, layerId, date)

    /** Find the data URL for a specific layer, date, and timestamp */
    fun findDataUrl(layerId: String, date: String, timestamp: Long): SkysightDataUrl? =
        findDataUrlHelper(this, layerId, date, timestamp)

    /** Check if we already have URLs for a specific layer and date */
    fun hasLayerDataUrls(layerId: String, date: String): Boolean =
        hasLayerDataUrlsHelper(this, layerId, date)

    /**
     * Update the selected date
     */
    suspend fun updateSelectedDate(date: kotlinx.datetime.LocalDate) {
        updateState { it.copy(selectedDate = date) }
        Logger.log("SKYSIGHT_MODULE", LogLevel.DEBUG, "Selected date updated: $date")
    }

    /**
     * Update the realtime timestamp
     */
    suspend fun updateRealtimeTimestamp(timestamp: kotlinx.datetime.Instant) {
        updateState { it.copy(realTimeTimestamp = timestamp) }
        Logger.log("SKYSIGHT_MODULE", LogLevel.DEBUG, "Realtime timestamp updated: $timestamp")
    }

    /**
     * Update the current hour
     */
    suspend fun updateCurrentTime(time: org.mountaincircles.app.modules.skysight.logic.data.TimePair) {
        updateState { it.copy(currentTime = time) }
        settingPersistence.saveString("currentTime", time.display)
        Logger.log("SKYSIGHT_MODULE", LogLevel.DEBUG, "Current time updated: ${time.display}")
    }


    /** Get the appropriate filter range for a layer based on its type */
    fun getLayerFilterRange(layerId: String): Pair<Float, Float> =
        getLayerFilterRangeHelper(this, layerId)

    /**
     * Unified pipeline: Ensure data is available and calculate viewport data for current parameters.
     * Handles URL fetching, file downloading, NetCDF analysis, and viewport calculation in one place.
     */
    /**
     * Recalculate viewport data for camera movement - only works with existing data
     */


    /**
     * Navigate to the current time rounded to the nearest half hour
     * 11:37 → 11:30, 13:48 → 14:00
     */
    /**
     * Navigate to now for forecast layers (30-minute rounding)
     */
    suspend fun navigateToNowForecast() {
        val currentState = moduleState.value
        val selectedLayerId = currentState.selectedLayerId
        val selectedDate = currentState.selectedDate

        // Require forecast layer selection
        if (selectedLayerId.isEmpty() || selectedDate == null) {
            Logger.log("SKYSIGHT_MODULE", LogLevel.WARN, "Cannot navigate to now (forecast): no forecast layer selected")
            return
        }

        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "=== NAVIGATE TO NOW FORECAST STARTED ===")

        // Get current UTC time (Skysight data uses UTC timestamps)
        val now = kotlinx.datetime.Clock.System.now()
        val currentDateTime = now.toLocalDateTime(kotlinx.datetime.TimeZone.UTC)

        val currentHour = currentDateTime.hour
        val currentMinute = currentDateTime.minute

        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Current time: ${currentHour.toString().padStart(2, '0')}:${currentMinute.toString().padStart(2, '0')}")

        // For forecast layers: round to nearest half hour
        val (newHour, newMinute) = if (currentMinute < 15) {
            // Round down to :00
            Pair(currentHour, 0)
        } else if (currentMinute < 45) {
            // Round to :30
            Pair(currentHour, 30)
        } else {
            // Round up to next hour :00
            val nextHour = (currentHour + 1) % 24
            Pair(nextHour, 0)
        }

        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Forecast rounding to: ${newHour.toString().padStart(2, '0')}:${newMinute.toString().padStart(2, '0')}")

        // Update the current time
        updateCurrentTime(org.mountaincircles.app.modules.skysight.logic.data.TimePair(newHour, newMinute))

        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "=== NAVIGATE TO NOW FORECAST COMPLETED ===")
    }

    /**
     * Navigate to now for real-time layers (10-minute rounding)
     */
    suspend fun navigateToNowRealtime(forceRealTimeCheck: Boolean = false) {
        val currentState = moduleState.value
        val hasRealTimeLayers = currentState.satelliteEnabled || currentState.localRainEnabled

        Logger.log("SKYSIGHT_MODULE", LogLevel.DEBUG, "navigateToNowRealtime: satelliteEnabled=${currentState.satelliteEnabled}, localRainEnabled=${currentState.localRainEnabled}, hasRealTimeLayers=$hasRealTimeLayers, forceRealTimeCheck=$forceRealTimeCheck")

        // Require real-time layer to be active (unless forced)
        if (!hasRealTimeLayers && !forceRealTimeCheck) {
            Logger.log("SKYSIGHT_MODULE", LogLevel.WARN, "Cannot navigate to now (realtime): no real-time layers active")
            return
        }

        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "=== NAVIGATE TO NOW REALTIME STARTED ===")

        // Get the floored 10 minutes ago timestamp
        val flooredTimestamp = SkysightUtils.getFlooredTenMinutesAgoTimestamp()

        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Realtime timestamp set to floored 10 minutes ago: ${flooredTimestamp}")
        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "=== NAVIGATE TO NOW REALTIME COMPLETED ===")

        // Update the realtime timestamp
        updateRealtimeTimestamp(flooredTimestamp)
    }

    /**
     * Legacy navigate to now - determines which function to call based on context
     * @deprecated Use navigateToNowForecast() or navigateToNowRealtime() instead
     */
    suspend fun navigateToNow() {
        val currentState = moduleState.value
        val selectedLayerId = currentState.selectedLayerId
        val selectedDate = currentState.selectedDate
        val hasRealTimeLayers = currentState.satelliteEnabled || currentState.localRainEnabled

        // If forecast layer is selected, use forecast navigation
        if (!selectedLayerId.isEmpty() && selectedDate != null) {
            Logger.log("SKYSIGHT_MODULE", LogLevel.DEBUG, "navigateToNow: delegating to forecast navigation")
            navigateToNowForecast()
        }
        // If real-time layers are active, use real-time navigation
        else if (hasRealTimeLayers) {
            Logger.log("SKYSIGHT_MODULE", LogLevel.DEBUG, "navigateToNow: delegating to realtime navigation")
            navigateToNowRealtime()
        }
        // No valid context
        else {
            Logger.log("SKYSIGHT_MODULE", LogLevel.WARN, "Cannot navigate to now: no layer/date selected and no real-time layers active")
        }
    }



    suspend fun toggleVisibility() {
        val currentVisibility = state.value.isVisible
        val newVisibility = !currentVisibility

        Logger.log("SKYSIGHT_MODULE_ZZZ", LogLevel.INFO, "=== VISIBILITY TOGGLE START: $currentVisibility -> $newVisibility ===")
        Logger.log("SKYSIGHT_MODULE_ZZZ", LogLevel.INFO, "Current state - satelliteEnabled:${state.value.satelliteEnabled}, localRainEnabled:${state.value.localRainEnabled}, selectedLayerId:'${state.value.selectedLayerId}', lastSelectedLayer:'${state.value.lastSelectedLayer}', submenuMode:'${state.value.submenuMode}'")

        if (newVisibility) {
            // Turning ON
            val lastSelected = state.value.lastSelectedLayer
            if (lastSelected.isEmpty()) {
                // No last selected layer - open sidebar and simulate click on skysight widget
                Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "No last selected layer - opening sidebar and simulating skysight widget click")
                org.mountaincircles.app.state.getGlobalState().navigationState.openSidebar()
            } else {
                // Restore last selected layer
                Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Restoring last selected layer: $lastSelected")
                when {
                    lastSelected.startsWith("forecast:") -> {
                        val layerId = lastSelected.substringAfter("forecast:")
                        // Set submenu mode based on layer type before selecting
                        val submenuMode = if (layerId.startsWith("w_")) "wave" else "forecast"
                        updateState { it.copy(submenuMode = submenuMode) }
                        selectForecastLayer(layerId)
                    }
                    lastSelected == "satellite" -> {
                        enableSatellite(true)
                        navigateToNowRealtime(forceRealTimeCheck = true)
                    }
                    lastSelected == "rain" -> {
                        enableRain(true)
                        navigateToNowRealtime(forceRealTimeCheck = true)
                    }
                    lastSelected == "satellite+rain" -> {
                        enableSatellite(true)
                        enableRain(true)
                        navigateToNowRealtime(forceRealTimeCheck = true)
                    }
                }
            }
        } else {
            // Turning OFF - clear all selected layers and save last selection
            Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Clearing all selected layers")

            // Determine what was last selected before clearing
            val lastSelected = when {
                state.value.selectedLayerId.isNotEmpty() -> "forecast:${state.value.selectedLayerId}"
                state.value.satelliteEnabled && state.value.localRainEnabled -> "satellite+rain"
                state.value.satelliteEnabled -> "satellite"
                state.value.localRainEnabled -> "rain"
                else -> ""
            }

            // Clear forecast
            if (state.value.selectedLayerId.isNotEmpty()) {
                selectForecastLayer("")
                SkysightTilingControllerV2.clearAllExistingTiles(this)
            }

            // Clear realtime
            if (state.value.satelliteEnabled) {
                enableSatellite(false)
                RealTimeTilingController.clearTilesForType(this, "satellite")
            }
            if (state.value.localRainEnabled) {
                enableRain(false)
                RealTimeTilingController.clearTilesForType(this, "rain")
            }

            // Save the last selected layer
            updateState { it.copy(lastSelectedLayer = lastSelected) }
        }

        updateState { it.copy(isVisible = newVisibility) }
        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Visibility toggled: $currentVisibility -> $newVisibility")
        Logger.log("SKYSIGHT_MODULE_ZZZ", LogLevel.INFO, "=== VISIBILITY TOGGLE END: final state - satelliteEnabled:${state.value.satelliteEnabled}, localRainEnabled:${state.value.localRainEnabled}, selectedLayerId:'${state.value.selectedLayerId}', lastSelectedLayer:'${state.value.lastSelectedLayer}', submenuMode:'${state.value.submenuMode}' ===")
    }


    suspend fun toggleLabelsVisibility() {
        val currentVisibility = state.value.isLabelsVisible
        val newVisibility = !currentVisibility

        // Update state first (state-centric approach)
        updateState { it.copy(isLabelsVisible = newVisibility) }
        settingPersistence.saveBoolean("isLabelsVisible", newVisibility)

        // When turning labels ON, recalculate viewport data immediately
        if (newVisibility) {
            val currentLayerData = layerDataFlow.value
            if (currentLayerData.hasData && currentLayerData.selectedLayerId.isNotEmpty() && currentLayerData.selectedDate != null) {
                Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Recalculating labels for current viewport")
                val globalState = org.mountaincircles.app.state.getGlobalState()
                val viewportBounds = org.mountaincircles.app.modules.skysight.logic.controllers.SkysightTilingControllerV2Viewport.getViewportBounds(globalState)

                val result = org.mountaincircles.app.modules.skysight.logic.controllers.SkysightTilingControllerV2Labels.updateLabels(
                    module = this,
                    globalState = globalState,
                    viewportBounds = viewportBounds,
                    layerId = currentLayerData.selectedLayerId,
                    date = currentLayerData.selectedDate!!,
                    timePair = currentLayerData.currentTime
                )

                if (result.isFailure) {
                    Logger.log("SKYSIGHT_MODULE", LogLevel.ERROR, "Failed to update labels: ${result.exceptionOrNull()?.message}")
                } else {
                    Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Labels recalculated successfully")
                }
            } else {
                Logger.log("SKYSIGHT_MODULE", LogLevel.DEBUG, "Skipping label recalculation: no valid layer data (hasData=${currentLayerData.hasData}, layerId='${currentLayerData.selectedLayerId}', date=${currentLayerData.selectedDate})")
            }
        }

        // Since we control re-rendering, trigger batch update for controlled layers
        triggerBatchUpdate()

        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Labels visibility toggled: $currentVisibility -> $newVisibility")
    }

    suspend fun setSatelliteEnabled(enabled: Boolean) {
        // If disabling satellite, clear only satellite tiles
        if (!enabled) {
            Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Disabling satellite layer - clearing satellite tiles")
            RealTimeTilingController.clearTilesForType(this, "satellite")
        }

        updateState {
            it.copy(
                satelliteEnabled = enabled,
                submenuMode = if (enabled || it.localRainEnabled) "realtime" else it.submenuMode
            )
        }
        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "State updated: satelliteEnabled = $enabled")

        // Handle submenu and sidebar when enabling satellite
        if (enabled) {
            // Always open submenu when enabling satellite
            org.mountaincircles.app.state.getGlobalState().navigationState.openSubmenu("skysight")

            // Check final state: close sidebar if both real-time layers will be enabled
            val currentState = state.value
            if (currentState.localRainEnabled) {
                // Both real-time layers will be enabled, close sidebar
                org.mountaincircles.app.state.getGlobalState().navigationState.closeSidebar()
            }
        }

        // Set time to real-time when enabling satellite (if rain is not currently active)
        if (enabled && !state.value.localRainEnabled) {
            Logger.log("SKYSIGHT_MODULE", LogLevel.DEBUG, "setSatelliteEnabled: About to call navigateToNowRealtime (will be first real-time layer), current state: satelliteEnabled=${state.value.satelliteEnabled}, localRainEnabled=${state.value.localRainEnabled}")
            navigateToNowRealtime(forceRealTimeCheck = true)
        }

        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Satellite layer ${if (enabled) "enabled" else "disabled"}")
    }


    suspend fun setLocalRainEnabled(enabled: Boolean) {
        // If disabling rain, clear only rain tiles
        if (!enabled) {
            Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Disabling local rain layer - clearing rain tiles")
            RealTimeTilingController.clearTilesForType(this, "rain")
        }

        updateState {
            it.copy(
                localRainEnabled = enabled,
                submenuMode = if (enabled || it.satelliteEnabled) "realtime" else it.submenuMode
            )
        }
        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "State updated: localRainEnabled = $enabled")

        // Handle submenu and sidebar when enabling rain
        if (enabled) {
            // Check final state: rain will be enabled, check if satellite is currently enabled
            val currentState = state.value
            if (currentState.satelliteEnabled) {
                // Both real-time layers will be enabled, open submenu and close sidebar
                org.mountaincircles.app.state.getGlobalState().navigationState.openSubmenu("skysight")
                org.mountaincircles.app.state.getGlobalState().navigationState.closeSidebar()
            } else {
                // Only rain will be enabled, just open submenu
                org.mountaincircles.app.state.getGlobalState().navigationState.openSubmenu("skysight")
            }
        }

        // Set time to real-time when enabling rain (if satellite is not currently active)
        if (enabled && !state.value.satelliteEnabled) {
            Logger.log("SKYSIGHT_MODULE", LogLevel.DEBUG, "setLocalRainEnabled: About to call navigateToNowRealtime (will be first real-time layer), current state: satelliteEnabled=${state.value.satelliteEnabled}, localRainEnabled=${state.value.localRainEnabled}")
            navigateToNowRealtime(forceRealTimeCheck = true)
        }

        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Local rain layer ${if (enabled) "enabled" else "disabled"}")
    }


    /**
     * Check if a file exists locally
     */

    /** Get the local file path for a file key */
    fun getLocalFilePath(fileKey: String): String = getLocalFilePathHelper(this, fileKey)


    /**
     * Fetch layer data URLs for a specific layer and date
     */


    // Sidebar widget implementation
    @Composable
    override fun SidebarWidget(onExpanded: (() -> Unit)?) {
        SkysightLayerWidget(this)
    }

    // BaseStatefulModule required methods
    override fun createLayerManager(): ModuleMapLayer {
        val adapter = SkysightLayerManagerAdapter(this)
        return adapter
    }

    override suspend fun onInitialize() {
        Logger.log("SKYSIGHT", LogLevel.INFO, "Initializing Skysight module")

        // Register for controlled re-rendering to batch layer updates
        org.mountaincircles.app.ui.map.LayerRegistrationHelper.layerManager.registerControlledReRenderModule(moduleId)

        // Initialize storage using global file manager and download manager
        Logger.log("SKYSIGHT", LogLevel.DEBUG, "Getting global file manager and download manager")
        val fileManager = getGlobalFileManager()
        val downloadManager = createDownloadManager()
        storage = SkysightStorage(fileManager, downloadManager)

        // Load persistent state (excluding credentials which are loaded in initializeSkysight())
        val savedIsLoggedIn = settingPersistence.getBoolean("isLoggedIn", false)
        val savedAllowedRegionsStr = settingPersistence.getString("allowedRegions", "") ?: ""
        val savedAllowedRegions = if (savedAllowedRegionsStr.isNotEmpty()) {
            savedAllowedRegionsStr.split(",")
        } else {
            emptyList()
        }
        val savedSelectedRegion = settingPersistence.getString("selectedRegion", "") ?: ""

        // Load available layers from persistence (full objects or fallback to names)
        val savedAvailableLayers = loadAvailableLayersFromPersistenceInit(this)

        // Clear all URLs from cached layers (lazy loading approach - fetch URLs only when needed)
        val clearedLayers = savedAvailableLayers.map { layer ->
            layer.copy(dataUrls = emptyMap()) // Clear all URLs for lazy loading
        }

        Logger.log("SKYSIGHT", LogLevel.DEBUG, "Loaded persistent state: loggedIn=$savedIsLoggedIn, selectedRegion=$savedSelectedRegion, layers=${savedAvailableLayers.size} (URLs cleared for lazy loading)")

        // Update state with loaded values (credentials already loaded by initializeSkysight())
        updateState { it.copy(
            isLoggedIn = savedIsLoggedIn,
            hasDataToRender = savedIsLoggedIn, // Set based on login state for top menu button and submenu
            allowedRegions = savedAllowedRegions,
            selectedRegion = savedSelectedRegion,
            availableLayers = clearedLayers
        )}

        // Persist layers without URLs (no need to persist URLs anymore)
        try {
            val layersJson = Json.encodeToString(clearedLayers)
            settingPersistence.saveString("availableLayers", layersJson)
            Logger.log("SKYSIGHT", LogLevel.DEBUG, "Persisted ${clearedLayers.size} layers without URLs (lazy loading)")
        } catch (e: Exception) {
            Logger.log("SKYSIGHT", LogLevel.ERROR, "Failed to persist cleared layers: ${e.message}", e)
        }

        // Initialize layer manager after initialization is complete
        (layerManager as? SkysightLayerManagerAdapter)?.initializeLayers()

        // Mark module as fully initialized
        updateState { it.copy(isInitialized = true) }

        Logger.log("SKYSIGHT", LogLevel.INFO, "Skysight module initialized")
    }

    /**
     * Get storage statistics for Skysight files
     */
    suspend fun getStorageStats(): Map<String, Any> {
        return storage.getStorageStats()
    }

    /**
     * Ensure parsed NetCDF data is cached in state
     */


    /**
     * Calculate linear index for data access
     */

    /**
     * Clear all SkySight files and cached data
     */
    suspend fun clearAllSkysightFiles(): Result<Unit> {
        return try {
            Logger.log("SKYSIGHT", LogLevel.INFO, "Clearing all Skysight files and cached data...")

            // Clear cached URLs for all layers and import counts
            updateState { state ->
                val layersWithClearedUrls = state.availableLayers.map { layer ->
                    layer.copy(dataUrls = emptyMap())
                }
                state.copy(
                    availableLayers = layersWithClearedUrls,
                    layerImportCounts = emptyMap()
                )
            }

            // Clear all local files using the storage interface
            storage.clearAllFiles()
            Logger.log("SKYSIGHT", LogLevel.INFO, "All Skysight files cleared successfully")

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.log("SKYSIGHT", LogLevel.ERROR, "Failed to clear Skysight files: ${e.message}")
            Result.failure(e)
        }
    }

    // Adapter to make SkysightLayerManager compatible with ModuleMapLayer
    private inner class SkysightLayerManagerAdapter(private val skysightModule: SkysightModule) : ModuleMapLayer {
        val layerManager = SkysightLayerManager(skysightModule)

        override suspend fun addLayers() {
            // Layers are added via the LayerManager system, not directly
            Logger.log("SKYSIGHT", LogLevel.DEBUG, "Skysight layers managed by LayerManager system")
        }

        override suspend fun removeLayers() {
            // Layers are removed via the LayerManager system
            Logger.log("SKYSIGHT", LogLevel.DEBUG, "Skysight layers removed via LayerManager system")
        }

        override suspend fun updateVisibility(visible: Boolean) {
            // Visibility is handled by the LayerManager system
            Logger.log("SKYSIGHT", LogLevel.DEBUG, "Skysight layer visibility updated: $visible")
        }

        override fun areLayersAdded(): Boolean {
            // Check if layers are registered with LayerManager
            return layerManager.layerIdsPublic.isNotEmpty()
        }

        suspend fun initializeLayers() {
            layerManager.initializeLayers()
        }

    // Counter for unique layer names to avoid MapLibre conflicts

    /**
     * Add a tile layer to the map (for geographic tiling system)
     */

        /**
         * Remove a tile layer from the map (for geographic tiling system)
         */

        /**
         * Calculate zIndex offset for a tile based on its coordinates
         * Format: "36N_1E" -> 0.36114 (latMin.lonMin.latMax.lonMax as decimal)
         */
    }

    /**
     * Public method to access API manager (for use by controllers)
     */
    fun getApiManager() = apiManager

    /**
     * Trigger batch update for controlled re-rendering
     */
    fun triggerBatchUpdate() {
        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "=== TRIGGER BATCH UPDATE CALLED ===")
        Logger.log("SKYSIGHT_MODULE", LogLevel.DEBUG, "Module state: satelliteEnabled=${state.value.satelliteEnabled}, localRainEnabled=${state.value.localRainEnabled}, isVisible=${state.value.isVisible}")

        org.mountaincircles.app.ui.map.LayerRegistrationHelper.layerManager.triggerBatchUpdate(moduleId)

        Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "=== TRIGGER BATCH UPDATE COMPLETED ===")
    }

}