package org.mountaincircles.app.modules.livetracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.*
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.state.getGlobalState
import org.mountaincircles.app.modules.livetracking.layer.ui.LiveTrackingLayerManager
import org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingLayerData
import org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingVisibilityMode
import org.mountaincircles.app.modules.livetracking.logic.data.PolledBoundaries
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeout.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mountaincircles.app.modules.livetracking.logic.initialization.initializeLiveTracking
import org.mountaincircles.app.modules.livetracking.logic.ui.getLiveTrackingButtonIcon
import org.mountaincircles.app.modules.livetracking.logic.ui.getLiveTrackingButtonAction
import org.mountaincircles.app.modules.livetracking.logic.ui.getLiveTrackingModuleActions
import org.mountaincircles.app.modules.livetracking.logic.business.LiveTrackingBusinessService
import org.mountaincircles.app.modules.livetracking.logic.data.FriendEntry
import org.mountaincircles.app.modules.livetracking.logic.LiveTrackingPollingController
import org.mountaincircles.app.io.getGlobalNetworkMonitor
import org.mountaincircles.app.io.isAppInBackground
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position as GeoPosition
import org.mountaincircles.app.settings.Setting
import org.mountaincircles.app.settings.SettingType
import org.maplibre.compose.camera.CameraState
import org.mountaincircles.app.utils.ScopeManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


// Reactive settings state for UI reactivity
data class LiveTrackingSettings(
    val iconSize: Float,
    val iconMinZoom: Float,
    val labelSize: Float,
    val labelMinZoom: Float,
    val labelOffset: Float,
    val timeOut: Float
)

/**
 * Live Tracking module for displaying real-time aircraft positions
 */
class LiveTrackingModule : BaseStatefulModule<org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingState>(), PopupClosable {


    override val moduleId = "livetracking"
    override val displayName = "Live Tracking"
    override val moduleInitializationOrder = 5
    override val sidebarWidgetOrder = 10
    override val mainMenuOrder = 10

    // Declarative UI capabilities
    override val hasTopMenuButton = true
    override val hasMainMenuButton = true
    override val hasSidebarWidget = false
    override val hasSettingsPanel = true
    override val hasImportSheet = true

    @Composable
    override fun getIcon(): androidx.compose.ui.graphics.painter.Painter {
        return AppIcons.Glider()
    }

    @Composable
    override fun getButtonIcon(): androidx.compose.ui.graphics.painter.Painter {
        return this.getLiveTrackingButtonIcon()
    }

    override fun getTopMenuButtonType(): TopMenuButtonType = TopMenuButtonType.TOGGLE

    // Livetracking icon needs cropping to fit properly
    override fun shouldCropTopMenuIcon(): Boolean = true

    override fun getButtonAction(): (() -> Unit)? {
        return this.getLiveTrackingButtonAction()
    }


    init {
        // Initialize state immediately so moduleState is available during module creation
        initializeState(org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingState())
    }

    // Public reactive state for UI components
    val liveTrackingState: StateFlow<org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingState> get() = state

    // Internal helper for safe state access
    internal val currentState: org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingState get() = state.value

    // Business Service - handles core business logic
    internal val businessService = LiveTrackingBusinessService(this)

    val layerDataFlow: StateFlow<LiveTrackingLayerData> = ModuleBase.createSelectiveFlow(
        state,
        { state ->
            LiveTrackingLayerData(
                visibilityMode = state.visibilityMode
            )
        },
        LiveTrackingLayerData(LiveTrackingVisibilityMode.ALL_HIDDEN)
    )

    val aircraftFeaturesFlow: StateFlow<Map<String, Feature<Point, JsonObject>>> = ModuleBase.createSelectiveFlow(
        state,
        { state -> state.aircraftFeatures },
        emptyMap()
    )

    val friendlistFlow: StateFlow<List<org.mountaincircles.app.modules.livetracking.logic.data.FriendEntry>> = ModuleBase.createSelectiveFlow(
        state,
        { state -> state.friendlist },
        emptyList()
    )

    // UI state for settings and other UI components
    override val uiState: StateFlow<ModuleUIState> = ModuleBase.createUIStateFlow(
        state,
        hasContentPredicate = { true },
        isLoadingPredicate = { false }
    )

    // Settings infrastructure
    internal lateinit var settingPersistence: org.mountaincircles.app.settings.SettingPersistence

    // Polling management (viewport, cycle, camera debounce, fetch/parse)
    internal val pollingController = LiveTrackingPollingController(this)

    // Reactive settings state for UI reactivity
    private val settingsState = mutableStateOf(
        LiveTrackingSettings(
            iconSize = 2.0f,
            iconMinZoom = 0.0f,
            labelSize = 12.0f,
            labelMinZoom = 6.0f,
            labelOffset = 2.0f,
            timeOut = 5.0f
        )
    )

    // Public access to reactive settings state
    val liveTrackingSettings: State<LiveTrackingSettings> get() = settingsState

    // Backward compatibility - reactive getters/setters
    var aircraftIconSize: Float
        get() = settingsState.value.iconSize
        set(value) { settingsState.value = settingsState.value.copy(iconSize = value) }

    var aircraftIconMinZoom: Float
        get() = settingsState.value.iconMinZoom
        set(value) { settingsState.value = settingsState.value.copy(iconMinZoom = value) }

    var aircraftLabelSize: Float
        get() = settingsState.value.labelSize
        set(value) { settingsState.value = settingsState.value.copy(labelSize = value) }

    var aircraftLabelMinZoom: Float
        get() = settingsState.value.labelMinZoom
        set(value) { settingsState.value = settingsState.value.copy(labelMinZoom = value) }

    var aircraftLabelOffset: Float
        get() = settingsState.value.labelOffset
        set(value) { settingsState.value = settingsState.value.copy(labelOffset = value) }

    var aircraftDataTimeout: Float
        get() = settingsState.value.timeOut
        set(value) { settingsState.value = settingsState.value.copy(timeOut = value) }


    // Settings list for persistence and reset
    internal val persistentStateKeys = listOf(
        Setting("aircraftIconSize", 2.0f, SettingType.FLOAT),
        Setting("aircraftLabelSize", 12.0f, SettingType.FLOAT),
        Setting("aircraftLabelOffset", 2.0f, SettingType.FLOAT),
        Setting("aircraftDataTimeout", 5.0f, SettingType.FLOAT),
        Setting("liveTrackingVisibilityMode", "ALL_VISIBLE", SettingType.STRING)
    )

    // ✅ STANDARDIZED STATE MANAGEMENT: Module initialization status

    override fun createLayerManager(): ModuleMapLayer {
        val adapter = LiveTrackingLayerManagerAdapter(this)
        // Defer layer initialization until after module initialization
        return adapter
    }

    override suspend fun onInitialize() {
        // Initialize live tracking module (includes settings registration and loading)
        initializeLiveTracking()

        // Initialize layers only after full module initialization
        val adapter = layerManager as? LiveTrackingLayerManagerAdapter
        adapter?.initializeLayers()

        // Mark module as fully initialized and available
        updateState { it.copy(isInitialized = true, hasDataToRender = true) }
    }


    // Settings update methods
    suspend fun updateAircraftIconSize(size: Float) {
        Logger.log("LIVETRACKING", LogLevel.INFO, "Updating aircraft icon size: $size")
        settingsState.value = settingsState.value.copy(iconSize = size)
        settingPersistence.saveFloat("aircraftIconSize", size)
    }

    suspend fun updateAircraftIconMinZoom(minZoom: Float) {
        Logger.log("LIVETRACKING", LogLevel.INFO, "Updating aircraft icon min zoom: $minZoom")
        settingsState.value = settingsState.value.copy(iconMinZoom = minZoom)
        settingPersistence.saveFloat("aircraftIconMinZoom", minZoom)
    }

    suspend fun updateAircraftLabelSize(size: Float) {
        Logger.log("LIVETRACKING", LogLevel.INFO, "Updating aircraft label size: $size")
        settingsState.value = settingsState.value.copy(labelSize = size)
        settingPersistence.saveFloat("aircraftLabelSize", size)
    }

    suspend fun updateAircraftLabelMinZoom(minZoom: Float) {
        Logger.log("LIVETRACKING", LogLevel.INFO, "Updating aircraft label min zoom: $minZoom")
        settingsState.value = settingsState.value.copy(labelMinZoom = minZoom)
        settingPersistence.saveFloat("aircraftLabelMinZoom", minZoom)
    }

    suspend fun updateAircraftLabelOffset(offset: Float) {
        Logger.log("LIVETRACKING", LogLevel.INFO, "Updating aircraft label offset: $offset")
        settingsState.value = settingsState.value.copy(labelOffset = offset)
        settingPersistence.saveFloat("aircraftLabelOffset", offset)
    }


    fun updateAircraftDataTimeout(timeoutMinutes: Float) {
        Logger.log("LIVETRACKING_SETTINGS", LogLevel.INFO, "Updating aircraft data timeout to ${timeoutMinutes} minutes")
        aircraftDataTimeout = timeoutMinutes
        // Save to persistent storage
        val scope = kotlinx.coroutines.MainScope()
        scope.launch {
            try {
                settingPersistence.saveFloat("aircraftDataTimeout", timeoutMinutes)
                Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Saved aircraft data timeout to persistent storage: ${timeoutMinutes} minutes")
            } catch (e: Exception) {
                Logger.log("LIVETRACKING_SETTINGS", LogLevel.ERROR, "Failed to save aircraft data timeout to persistent storage: ${e.message}")
            }
        }
    }

    /**
     * Attempt to poll aircraft data - checks network validation before polling
     */
    suspend fun attemptPoll(north: Double, south: Double, east: Double, west: Double) =
        pollingController.attemptPoll(north, south, east, west)

    /**
     * Handle aircraft data polling with viewport extent calculation
     */
    suspend fun handleAircraftPolling(cameraState: CameraState) =
        pollingController.handleAircraftPolling(cameraState)

    /**
     * Start the 20s polling cycle if not already running
     */
    fun startPollingCycle(cameraState: CameraState) =
        pollingController.startPollingCycle(cameraState)

    /**
     * Stop the polling cycle
     */
    fun stopPollingCycle() =
        pollingController.stopPollingCycle()

    /**
     * Handle camera movement with debouncing and smart repolling
     */
    fun handleCameraMove(cameraState: CameraState) =
        pollingController.handleCameraMove(cameraState)

    // Adapter to make LiveTrackingLayerManager compatible with ModuleMapLayer
    private inner class LiveTrackingLayerManagerAdapter(private val liveTrackingModule: LiveTrackingModule) : ModuleMapLayer {
        private val layerManager = LiveTrackingLayerManager(liveTrackingModule)

        override suspend fun addLayers() {
            Logger.log("LIVETRACKING", LogLevel.DEBUG, "Live tracking layers managed by LayerManager system")
        }

        override suspend fun removeLayers() {
            Logger.log("LIVETRACKING", LogLevel.DEBUG, "Live tracking layers removed via LayerManager system")
        }

        override suspend fun updateVisibility(visible: Boolean) {
            Logger.log("LIVETRACKING", LogLevel.DEBUG, "Live tracking layer visibility updated: $visible")
        }

        override fun areLayersAdded(): Boolean {
            return layerManager.layerIdsPublic.isNotEmpty()
        }

        fun initializeLayers() {
            layerManager.initializeLayers()
        }
    }

    // Popup management methods
    fun showAircraftPopup(deviceId: String) {
        Logger.log("LIVETRACKING_POPUP", LogLevel.INFO, "Showing aircraft popup for deviceId: $deviceId")
        // Open centralized popup (exclusive like submenus)
        val globalState = getGlobalState()
        globalState.navigationState.openPopup(org.mountaincircles.app.state.PopupId(moduleId, deviceId))
        // Also update module state for backward compatibility
        updateState { currentState ->
            currentState.copy(
                showPopup = true,
                popupDeviceId = deviceId
            )
        }
    }

    fun hideAircraftPopup() {
        Logger.log("LIVETRACKING_POPUP", LogLevel.INFO, "Hiding aircraft popup")
        // Close centralized popup (exclusive like submenus)
        val globalState = getGlobalState()
        globalState.navigationState.closePopup()
        // Also update module state for backward compatibility
        businessService.hideAircraftPopup()
    }

    // Friendlist management methods
    fun addToFriendlist(deviceId: String) {
        Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.INFO, "Adding aircraft $deviceId to friendlist")

        // Try to find aircraft data to get registration info
        val aircraftData = findAircraftData(deviceId)
        val registration = (aircraftData?.get("registration") as? String) ?: ""
        val registrationShort = (aircraftData?.get("registrationShort") as? String) ?: ""

        val newFriend = FriendEntry(
            deviceId = deviceId,
            customName = registrationShort.ifEmpty { deviceId }, // Initialize custom name as registration short, fallback to deviceId
            registration = registration,
            registrationShort = registrationShort
        )

        // Update state and save persistence in the same operation
        updateState { currentState ->
            val updatedList = currentState.friendlist + newFriend
            // Save to persistent storage
            val scope = kotlinx.coroutines.MainScope()
            scope.launch {
                try {
                    val jsonString = kotlinx.serialization.json.Json.encodeToString(updatedList)
                    settingPersistence.saveString("friendlist", jsonString)
                    Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.DEBUG, "Saved friendlist to persistent storage: $updatedList")
                } catch (e: Exception) {
                    Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.ERROR, "Failed to save friendlist to persistent storage: ${e.message}")
                }
            }
            currentState.copy(friendlist = updatedList)
        }
    }

    fun removeFromFriendlist(deviceId: String) {
        Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.INFO, "Removing aircraft $deviceId from friendlist")

        // Update state and save persistence in the same operation
        updateState { currentState ->
            val updatedList = currentState.friendlist.filter { it.deviceId != deviceId }
            // Save to persistent storage
            val scope = kotlinx.coroutines.MainScope()
            scope.launch {
                try {
                    val jsonString = kotlinx.serialization.json.Json.encodeToString(updatedList)
                    settingPersistence.saveString("friendlist", jsonString)
                    Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.DEBUG, "Saved friendlist to persistent storage: $updatedList")
                } catch (e: Exception) {
                    Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.ERROR, "Failed to save friendlist to persistent storage: ${e.message}")
                }
            }
            currentState.copy(friendlist = updatedList)
        }
    }

    fun isOnFriendlist(deviceId: String): Boolean {
        return state.value.friendlist.any { it.deviceId == deviceId }
    }

    /**
     * Get aircraft coordinates for camera movement
     */
    fun getAircraftCoordinates(deviceId: String): Pair<Double, Double>? {
        Logger.log("GET_AIRCRAFT_COORDS", LogLevel.DEBUG, "Getting coordinates for deviceId: $deviceId")
        val aircraftData = findAircraftData(deviceId)
        Logger.log("GET_AIRCRAFT_COORDS", LogLevel.DEBUG, "Aircraft data: $aircraftData")

        return aircraftData?.get("coordinates")?.let { coords ->
            Logger.log("GET_AIRCRAFT_COORDS", LogLevel.DEBUG, "Coordinates object: $coords")
            if (coords is List<*> && coords.size >= 2) {
                val longitude = coords[0] as? Double
                val latitude = coords[1] as? Double
                Logger.log("GET_AIRCRAFT_COORDS", LogLevel.DEBUG, "Parsed longitude: $longitude, latitude: $latitude")
                if (longitude != null && latitude != null) {
                    Pair(longitude, latitude)
                } else {
                    Logger.log("GET_AIRCRAFT_COORDS", LogLevel.WARN, "Longitude or latitude is null")
                    null
                }
            } else {
                Logger.log("GET_AIRCRAFT_COORDS", LogLevel.WARN, "Coordinates is not a list or doesn't have 2 elements")
                null
            }
        }.also { result ->
            Logger.log("GET_AIRCRAFT_COORDS", LogLevel.INFO, "Returning coordinates: $result")
        }
    }

    /**
     * Move camera to aircraft coordinates
     */
    fun moveCameraToAircraft(longitude: Double, latitude: Double) {
        Logger.log("MOVE_CAMERA", LogLevel.INFO, "Moving camera to aircraft at $longitude, $latitude")

        // Get global state and current camera state
        val globalState = getGlobalState()
        Logger.log("MOVE_CAMERA", LogLevel.DEBUG, "Global state: $globalState")

        val cameraState = globalState.currentCameraState.value
        Logger.log("MOVE_CAMERA", LogLevel.DEBUG, "Camera state: $cameraState")

        if (cameraState != null) {
            // Create new camera position
            val newPosition = CameraPosition(
                target = Position(latitude = latitude, longitude = longitude),
                zoom = 8.0, // Fixed zoom level for friend targeting
                bearing = 0.0, // North up
                tilt = 0.0
            )
            Logger.log("MOVE_CAMERA", LogLevel.DEBUG, "New position: $newPosition")

            // Animate camera to new position
            kotlinx.coroutines.MainScope().launch {
                try {
                    Logger.log("MOVE_CAMERA", LogLevel.INFO, "Starting camera animation")
                    cameraState.animateTo(newPosition)
                    Logger.log("MOVE_CAMERA", LogLevel.INFO, "Camera moved to aircraft successfully")
                } catch (e: Exception) {
                    Logger.log("MOVE_CAMERA", LogLevel.ERROR, "Failed to move camera to aircraft: ${e.message}", e)
                }
            }
        } else {
            Logger.log("MOVE_CAMERA", LogLevel.WARN, "Camera state not available, cannot move to aircraft")
        }
    }

    /**
     * Find aircraft data from current GeoJSON by deviceId
     */
    private fun findAircraftData(deviceId: String): Map<String, Any>? {
        // Find the feature directly from the aircraftFeatures map
        val feature = state.value.aircraftFeatures[deviceId] ?: return null

        try {
            // Extract all properties and geometry coordinates
            val aircraftData = mutableMapOf<String, Any>()

            // Extract properties from the Feature object
            feature.properties.entries.forEach { (key, value) ->
                when (value) {
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        if (value.isString) {
                            aircraftData[key] = value.content
                        } else {
                            aircraftData[key] = value.toString()
                        }
                    }
                    else -> aircraftData[key] = value.toString()
                }
            }

            // Extract coordinates from Feature geometry (Point)
            val point = feature.geometry
            if (point is org.maplibre.spatialk.geojson.Point) {
                val position = point.coordinates
                aircraftData["coordinates"] = listOf(position.longitude, position.latitude)
            }

            return aircraftData
        } catch (e: Exception) {
            Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.ERROR, "Failed to extract aircraft data for deviceId $deviceId: ${e.message}")
        }

        return null
    }

    // Update custom name for a friend
    fun updateFriendName(deviceId: String, newCustomName: String) {
        Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.INFO, "Updating friend $deviceId name to: $newCustomName")
        updateState { currentState ->
            val updatedList = currentState.friendlist.map { friend ->
                if (friend.deviceId == deviceId) {
                    friend.copy(customName = newCustomName.trim())
                } else {
                    friend
                }
            }

            // Also update the corresponding aircraft feature if it exists
            val updatedFeatures = currentState.aircraftFeatures.mapValues { (_, feature) ->
                // Check if this feature corresponds to the updated friend
                val featureDeviceId = when (val prop = feature.properties["deviceId"]) {
                    is kotlinx.serialization.json.JsonPrimitive -> if (prop.isString) prop.content else prop.toString()
                    else -> prop?.toString()
                }

                if (featureDeviceId == deviceId) {
                    // Recalculate the display name for this feature
                    val friendEntry = updatedList.find { it.deviceId == deviceId }
                    val displayName = friendEntry?.customName ?: friendEntry?.registrationShort ?: ""

                    // Update the feature properties with the new display name
                    val updatedProperties = buildJsonObject {
                        feature.properties.entries.forEach { (key, value) ->
                            if (key == "displayName") {
                                put(key, displayName) // Update displayName with new custom name
                            } else {
                                put(key, value)
                            }
                        }
                    }

                    feature.copy(properties = updatedProperties)
                } else {
                    feature
                }
            }

            // Save to persistent storage
            val scope = kotlinx.coroutines.MainScope()
            scope.launch {
                try {
                    val jsonString = kotlinx.serialization.json.Json.encodeToString(updatedList)
                    settingPersistence.saveString("friendlist", jsonString)
                    Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.DEBUG, "Saved updated friendlist to persistent storage")
                } catch (e: Exception) {
                    Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.ERROR, "Failed to save updated friendlist to persistent storage: ${e.message}")
                }
            }

            Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.DEBUG, "Updated friend name for $deviceId and corresponding aircraft feature")
            currentState.copy(friendlist = updatedList, aircraftFeatures = updatedFeatures)
        }
    }

    // Module actions for main menu
    override fun getModuleActions(): List<ModuleMenuAction> {
        return getLiveTrackingModuleActions(moduleId, currentState.isInitialized)
    }

    // PopupClosable implementation
    override fun onPopupClosed() {
        Logger.log("LIVETRACKING_POPUP", LogLevel.INFO, "🧹 Generic popup close triggered live tracking cleanup")
        businessService.hideAircraftPopup()
    }
}
