package org.mountaincircles.app.modules.livetracking.logic.initialization

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.livetracking.LiveTrackingModule
import org.mountaincircles.app.modules.livetracking.logic.data.FriendEntry
import org.mountaincircles.app.modules.livetracking.settings.registerLiveTrackingSettings
import org.mountaincircles.app.modules.livetracking.settings.logic.LiveTrackingSettingsProvider
import org.mountaincircles.app.settings.SettingPersistence
import org.mountaincircles.app.settings.Setting
import org.mountaincircles.app.settings.SettingType
import org.mountaincircles.app.ui.overlay.MapOverlayRegistry
import org.mountaincircles.app.modules.livetracking.overlay.ui.LiveTrackingPopupOverlay

/**
 * LiveTracking Module Initialization
 * Extracted initialization logic for better separation of concerns
 */

/**
 * Initialize the live tracking module - set up persistence and register settings
 */
suspend fun LiveTrackingModule.initializeLiveTracking() {
    Logger.log("LIVETRACKING", LogLevel.INFO, "Initializing live tracking module")

    // Initialize SettingPersistence
    settingPersistence = org.mountaincircles.app.settings.SettingPersistence(moduleId)

    // Load persisted module state after persistence is initialized
    loadPersistedModuleState()

    // Load friendlist from persistent storage
    loadFriendlistFromPersistence()

    // Register settings using the standardized pattern
    // Note: metadata provider was already registered in init block for early availability
    registerModuleSettings(
        settingsRegistration = { registerLiveTrackingSettings(this) },
        metadataProvider = LiveTrackingSettingsProvider(this)
    )

    // Register popup overlay
    MapOverlayRegistry.register(moduleId, LiveTrackingPopupOverlay())

    Logger.log("LIVETRACKING", LogLevel.INFO, "Live tracking module initialization complete")
}

/**
 * Load persisted module state and apply to runtime state
 */
internal suspend fun LiveTrackingModule.loadPersistedModuleState() {
    persistentStateKeys.forEach { stateKey ->
        if (stateKey.type.name == "FLOAT") {
            val loadedValue = settingPersistence.getFloat(stateKey.key, stateKey.defaultValue as Float)
            when (stateKey.key) {
                "aircraftIconSize" -> aircraftIconSize = loadedValue
                "aircraftIconMinZoom" -> aircraftIconMinZoom = loadedValue
                "aircraftLabelSize" -> aircraftLabelSize = loadedValue
                "aircraftLabelMinZoom" -> aircraftLabelMinZoom = loadedValue
                "aircraftLabelOffset" -> aircraftLabelOffset = loadedValue
                "aircraftDataTimeout" -> aircraftDataTimeout = loadedValue
            }
        } else if (stateKey.type.name == "STRING") {
            val loadedValue = settingPersistence.getString(stateKey.key, stateKey.defaultValue as String)
            when (stateKey.key) {
                "liveTrackingVisibilityMode" -> {
                    val visibilityMode = when (loadedValue) {
                        "ALL_VISIBLE" -> org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingVisibilityMode.ALL_VISIBLE
                        "FRIENDS_ONLY" -> org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingVisibilityMode.FRIENDS_ONLY
                        "ALL_HIDDEN" -> org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingVisibilityMode.ALL_HIDDEN
                        else -> org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingVisibilityMode.ALL_VISIBLE
                    }
                    updateState { it.copy(visibilityMode = visibilityMode) }
                }
            }
        }
    }
    Logger.log("LIVETRACKING", LogLevel.DEBUG, "Settings loaded: iconSize=$aircraftIconSize, iconMinZoom=$aircraftIconMinZoom, labelSize=$aircraftLabelSize, labelMinZoom=$aircraftLabelMinZoom, offset=$aircraftLabelOffset, timeout=$aircraftDataTimeout")
}

/**
 * Load friendlist from persistent storage
 */
internal suspend fun LiveTrackingModule.loadFriendlistFromPersistence() {
    try {
        val friendlistJson = settingPersistence.getString("friendlist", "[]") ?: "[]"
        val friendlist = kotlinx.serialization.json.Json.decodeFromString<List<FriendEntry>>(friendlistJson)
        updateState { it.copy(friendlist = friendlist) }
        Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.DEBUG, "Loaded friendlist from persistent storage: $friendlist")
    } catch (e: Exception) {
        Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.ERROR, "Failed to load friendlist from persistent storage: ${e.message}")
        // Continue with empty friendlist
    }
}