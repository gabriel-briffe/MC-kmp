package org.mountaincircles.app.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.serializer
import org.mountaincircles.app.modules.ModuleManager
import org.mountaincircles.app.persistence.DataStoreDataManager
import org.mountaincircles.app.persistence.DataManager
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.ui.map.MapClickEvent
import org.mountaincircles.app.state.GeoBounds
import org.mountaincircles.app.state.OfflineDownloadUiState
import org.mountaincircles.app.state.OfflineSelectionPhase


class GlobalState {
    // Application-wide coroutine scope (uses centralized ScopeManager)
    val applicationScope = org.mountaincircles.app.utils.ScopeManager.uiScope
    
    private val _mapReady = MutableStateFlow(false)
    val mapReady: StateFlow<Boolean> = _mapReady
    
    // North-lock state for map orientation with DataStore persistence
    private val _northLocked = MutableStateFlow(false)
    val northLocked: StateFlow<Boolean> = _northLocked

    // Core marker state - shared by all modules
    private val _showMarker = MutableStateFlow(false)
    val showMarker: StateFlow<Boolean> = _showMarker

    private val _markerPosition = MutableStateFlow<MapClickEvent?>(null)
    val markerPosition: StateFlow<MapClickEvent?> = _markerPosition

    private val _markerStyle = MutableStateFlow<MarkerStyle?>(null)
    val markerStyle: StateFlow<MarkerStyle?> = _markerStyle

    // Geolocation settings - loaded from persisted settings
    private val _circleRadiusKm = MutableStateFlow(0f)
    val circleRadiusKm: StateFlow<Float> = _circleRadiusKm

    private val _vectorLengthKm = MutableStateFlow(0f)
    val vectorLengthKm: StateFlow<Float> = _vectorLengthKm

    private val _circleWidthDp = MutableStateFlow(4f)
    val circleWidthDp: StateFlow<Float> = _circleWidthDp

    private val _vectorWidthDp = MutableStateFlow(3f)
    val vectorWidthDp: StateFlow<Float> = _vectorWidthDp

    private val _osmBasemapEnabled = MutableStateFlow(true)
    val osmBasemapEnabled: StateFlow<Boolean> = _osmBasemapEnabled

    private val _terrainBasemapEnabled = MutableStateFlow(true)
    val terrainBasemapEnabled: StateFlow<Boolean> = _terrainBasemapEnabled

    // Click position tracking (for modules that want to show markers on clicks)
    private val _clickedPosition = MutableStateFlow<MapClickEvent?>(null)
    val clickedPosition: StateFlow<MapClickEvent?> = _clickedPosition

    // Current camera state for viewport calculations (includes projection access)
    private val _currentCameraState = MutableStateFlow<org.maplibre.compose.camera.CameraState?>(null)
    val currentCameraState: StateFlow<org.maplibre.compose.camera.CameraState?> = _currentCameraState

    // MapLibre map reference for snapshot functionality
    private val _mapLibreMapReference = MutableStateFlow<Any?>(null)
    val mapLibreMapReference: StateFlow<Any?> = _mapLibreMapReference

    // Screen dimensions for wind vector downsampling (in millimeters)
    private val _screenWidthMm = MutableStateFlow(0.0)
    val screenWidthMm: StateFlow<Double> = _screenWidthMm

    private val _screenHeightMm = MutableStateFlow(0.0)
    val screenHeightMm: StateFlow<Double> = _screenHeightMm

    // Unified data manager for global app settings
    private val appSettingsManager: DataManager<GlobalAppSettings> = DataStoreDataManager(
        moduleId = "app_global",
        serializer = GlobalAppSettings.serializer(),
        defaultData = GlobalAppSettings.DEFAULT
    )
    

    
        // Navigation state
    val navigationState = NavigationState()

    // Module management
    val moduleManager = ModuleManager.create()

    // Offline region selection (Android: MapLibre offline packs)
    private val _offlineSelectionPhase = MutableStateFlow(OfflineSelectionPhase.Idle)
    val offlineSelectionPhase: StateFlow<OfflineSelectionPhase> = _offlineSelectionPhase

    private val _offlinePreviewBounds = MutableStateFlow<GeoBounds?>(null)
    val offlinePreviewBounds: StateFlow<GeoBounds?> = _offlinePreviewBounds

    private val _offlineDownloadState = MutableStateFlow(OfflineDownloadUiState())
    val offlineDownloadState: StateFlow<OfflineDownloadUiState> = _offlineDownloadState

    /** Set by [OfflineRegionMapContent] when user confirms download. */
    private val _offlineDownloadRequest = MutableStateFlow<GeoBounds?>(null)
    val offlineDownloadRequest: StateFlow<GeoBounds?> = _offlineDownloadRequest

    fun startOfflineRegionSelection() {
        if (!_osmBasemapEnabled.value && !_terrainBasemapEnabled.value) {
            Logger.log("OFFLINE", LogLevel.WARN, "Cannot store offline: enable OSM and/or Terrain in Basemap")
            return
        }
        _offlinePreviewBounds.value = null
        _offlineDownloadState.value = OfflineDownloadUiState()
        _offlineDownloadRequest.value = null
        _offlineSelectionPhase.value = OfflineSelectionPhase.Drawing
        navigationState.closeMainMenu()
        Logger.log("OFFLINE", LogLevel.INFO, "Started offline region selection")
    }

    fun cancelOfflineRegionSelection() {
        _offlineSelectionPhase.value = OfflineSelectionPhase.Idle
        _offlinePreviewBounds.value = null
        _offlineDownloadRequest.value = null
        _offlineDownloadState.value = OfflineDownloadUiState()
        Logger.log("OFFLINE", LogLevel.INFO, "Cancelled offline region selection")
    }

    fun setOfflinePreviewBounds(bounds: GeoBounds) {
        _offlinePreviewBounds.value = bounds
        _offlineSelectionPhase.value = OfflineSelectionPhase.Preview
    }

    fun redrawOfflineRegion() {
        _offlinePreviewBounds.value = null
        _offlineDownloadRequest.value = null
        _offlineSelectionPhase.value = OfflineSelectionPhase.Drawing
    }

    fun confirmOfflineRegionDownload() {
        val bounds = _offlinePreviewBounds.value ?: return
        if (!_osmBasemapEnabled.value && !_terrainBasemapEnabled.value) {
            _offlineDownloadState.value = OfflineDownloadUiState(
                error = "Enable OSM and/or Terrain in the Basemap sidebar section.",
                statusMessage = "Download failed",
            )
            return
        }
        _offlineDownloadRequest.value = bounds
        _offlineSelectionPhase.value = OfflineSelectionPhase.Downloading
        _offlineDownloadState.value = OfflineDownloadUiState(statusMessage = "Preparing download…")
        Logger.log("OFFLINE", LogLevel.INFO, "Confirmed offline download for bounds $bounds")
    }

    fun updateOfflineDownloadState(state: OfflineDownloadUiState) {
        _offlineDownloadState.value = state
    }

    fun completeOfflineRegionDownload() {
        _offlineSelectionPhase.value = OfflineSelectionPhase.Idle
        _offlinePreviewBounds.value = null
        _offlineDownloadRequest.value = null
        _offlineDownloadState.value = OfflineDownloadUiState(
            statusMessage = "Region saved for offline use",
            isComplete = true,
        )
        Logger.log("OFFLINE", LogLevel.INFO, "Offline region download complete")
    }


    
    fun onMapReady() {
        _mapReady.value = true
        // Modules now use declarative layer rendering, no notification needed
    }
    
    suspend fun initializeModules() {
        // Load persisted app settings using unified pattern
        val settings = appSettingsManager.load()
        _northLocked.value = settings.northLockEnabled

        // Load geolocation settings
        _circleRadiusKm.value = settings.circleRadiusKm
        _vectorLengthKm.value = settings.vectorLengthKm
        _circleWidthDp.value = settings.circleWidthDp
        _vectorWidthDp.value = settings.vectorWidthDp
        _osmBasemapEnabled.value = settings.osmBasemapEnabled
        _terrainBasemapEnabled.value = settings.terrainBasemapEnabled

        // Initialize module manager
        moduleManager.initialize(this)

        // Register all available modules
        moduleManager.registerAllModules()
    }
    
    /**
     * Toggle north-lock state and save to DataStore using unified pattern
     */
    suspend fun toggleNorthLock() {
        val currentSettings = appSettingsManager.load()
        val newValue = !currentSettings.northLockEnabled
        _northLocked.value = newValue
        appSettingsManager.save(currentSettings.copy(northLockEnabled = newValue))
    }

    /**
     * Set north-lock state directly and save using unified pattern
     */
    suspend fun setNorthLock(locked: Boolean) {
        _northLocked.value = locked
        val currentSettings = appSettingsManager.load()
        appSettingsManager.save(currentSettings.copy(northLockEnabled = locked))
    }

    /**
     * Core marker control methods - shared by all modules
     */
    fun showMarkerAt(position: MapClickEvent, style: MarkerStyle = MarkerStyle.DEFAULT) {
        _markerPosition.value = position
        _markerStyle.value = style
        _showMarker.value = true
        Logger.log("GLOBAL_MARKER", LogLevel.DEBUG, "Showing marker at (${position.latitude}, ${position.longitude}) with style: $style")
    }

    fun hideMarker() {
        Logger.log("GLOBAL_MARKER", LogLevel.INFO, "🔴 STARTING: Hiding marker")
        Logger.log("GLOBAL_MARKER", LogLevel.DEBUG, "Current marker state - show: ${_showMarker.value}, position: ${_markerPosition.value}, style: ${_markerStyle.value}")

        _showMarker.value = false
        _markerPosition.value = null
        _markerStyle.value = null

        Logger.log("GLOBAL_MARKER", LogLevel.INFO, "✅ COMPLETED: Marker hidden - show: ${_showMarker.value}")
    }

    fun updateMarkerPosition(position: MapClickEvent) {
        _markerPosition.value = position
    }

    fun setClickedPosition(event: MapClickEvent?) {
        _clickedPosition.value = event
    }

    // Geolocation settings
    suspend fun updateCircleRadius(radiusKm: Float) {
        val clampedValue = radiusKm.coerceIn(0f, 20f) // Clamp between 0-20km (0 = disabled)
        _circleRadiusKm.value = clampedValue
        // Save to persistence
        try {
            val currentSettings = appSettingsManager.load()
            appSettingsManager.save(currentSettings.copy(circleRadiusKm = clampedValue))
        } catch (e: Exception) {
            Logger.log("GEOLOCATION", LogLevel.ERROR, "Failed to save circle radius: ${e.message}", e)
        }
    }

    suspend fun updateVectorLength(lengthKm: Float) {
        val clampedValue = lengthKm.coerceIn(0f, 50f) // Clamp between 0-50km (0 = disabled)
        _vectorLengthKm.value = clampedValue
        // Save to persistence
        try {
            val currentSettings = appSettingsManager.load()
            appSettingsManager.save(currentSettings.copy(vectorLengthKm = clampedValue))
        } catch (e: Exception) {
            Logger.log("GEOLOCATION", LogLevel.ERROR, "Failed to save vector length: ${e.message}", e)
        }
    }


    suspend fun updateCircleWidth(widthDp: Float) {
        val clampedValue = widthDp.coerceIn(1f, 10f) // Clamp between 1-10dp
        _circleWidthDp.value = clampedValue
        // Save to persistence
        try {
            val currentSettings = appSettingsManager.load()
            appSettingsManager.save(currentSettings.copy(circleWidthDp = clampedValue))
        } catch (e: Exception) {
            Logger.log("GEOLOCATION", LogLevel.ERROR, "Failed to save circle width: ${e.message}", e)
        }
    }

    suspend fun updateVectorWidth(widthDp: Float) {
        val clampedValue = widthDp.coerceIn(1f, 10f) // Clamp between 1-10dp
        _vectorWidthDp.value = clampedValue
        // Save to persistence
        try {
            val currentSettings = appSettingsManager.load()
            appSettingsManager.save(currentSettings.copy(vectorWidthDp = clampedValue))
        } catch (e: Exception) {
            Logger.log("GEOLOCATION", LogLevel.ERROR, "Failed to save vector width: ${e.message}", e)
        }
    }

    suspend fun setOsmBasemapEnabled(enabled: Boolean) {
        if (!enabled && !_terrainBasemapEnabled.value) return
        _osmBasemapEnabled.value = enabled
        persistBasemapSettings()
    }

    suspend fun setTerrainBasemapEnabled(enabled: Boolean) {
        if (!enabled && !_osmBasemapEnabled.value) return
        _terrainBasemapEnabled.value = enabled
        persistBasemapSettings()
    }

    private suspend fun persistBasemapSettings() {
        try {
            val currentSettings = appSettingsManager.load()
            appSettingsManager.save(
                currentSettings.copy(
                    osmBasemapEnabled = _osmBasemapEnabled.value,
                    terrainBasemapEnabled = _terrainBasemapEnabled.value,
                ),
            )
        } catch (e: Exception) {
            Logger.log("BASEMAP", LogLevel.ERROR, "Failed to save basemap settings: ${e.message}", e)
        }
    }

    fun updateCurrentCameraState(cameraState: org.maplibre.compose.camera.CameraState) {
        _currentCameraState.value = cameraState
    }

    fun updateScreenDimensions(widthMm: Double, heightMm: Double) {
        _screenWidthMm.value = widthMm
        _screenHeightMm.value = heightMm
        Logger.log("GLOBAL_STATE", LogLevel.DEBUG, "Screen dimensions updated: ${widthMm}mm x ${heightMm}mm")
    }

    fun setMapLibreMapReference(map: Any?) {
        _mapLibreMapReference.value = map
        if (map != null) {
            Logger.log("GLOBAL_STATE", LogLevel.INFO, "MapLibre map reference stored: $map")
        } else {
            Logger.log("GLOBAL_STATE", LogLevel.INFO, "MapLibre map reference cleared")
        }
    }

    companion object
}

/**
 * Marker style enum for different use cases
 */
enum class MarkerStyle {
    DEFAULT,    // Basic marker
    AIRSPACE,   // Airspace-specific styling
    POI,        // Point of interest
    SEARCH      // Search result
}