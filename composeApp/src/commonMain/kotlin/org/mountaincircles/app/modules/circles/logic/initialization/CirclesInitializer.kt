package org.mountaincircles.app.modules.circles.logic.initialization

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.settings.SettingType
import org.mountaincircles.app.modules.circles.logic.data.CirclesState
import org.mountaincircles.app.modules.circles.import.logic.CirclesManager
import org.mountaincircles.app.modules.circles.import.ui.CirclesFilePicker
import org.mountaincircles.app.modules.circles.logic.controllers.CirclesBusinessController
import org.mountaincircles.app.modules.circles.logic.controllers.CirclesFileOperations
import org.mountaincircles.app.modules.circles.overlay.ui.CirclesParameterOverlay
import org.mountaincircles.app.modules.circles.settings.registerCirclesSettings
import org.mountaincircles.app.modules.circles.settings.logic.CirclesSettingsProvider
import org.mountaincircles.app.ui.overlay.MapOverlayRegistry
import org.mountaincircles.app.utils.ScopeManager
import org.mountaincircles.app.modules.circles.logic.data.PackConfig
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

/**
 * Circles Module Initialization
 * Extracted initialization logic for better separation of concerns
 */

/**
 * Initialize the circles module - complete initialization including business logic setup
 */
suspend fun CirclesModule.initializeCircles() {
    // Initialize platform-specific manager
    circlesManager = CirclesManager()

    // Initialize file picker
    filePicker = CirclesFilePicker()

    // Settings are handled via metadata in SettingsComposable.kt
    Logger.log("CIRCLES", LogLevel.DEBUG, "Settings handled via metadata approach")

    // Initialize file operations manager with temporary callbacks
    // These will be replaced when the business controller is created
    fileOperations = CirclesFileOperations(
        circlesManager = circlesManager,
        filePicker = filePicker,
        onStateUpdate = { newState ->
            Logger.log("CIRCLES", LogLevel.WARN, "File operations state update called before initialization")
        },
        onProgressUpdate = { progress ->
            Logger.log("CIRCLES", LogLevel.WARN, "File operations progress update called before initialization")
        },
        onRescanPacks = {
            Logger.log("CIRCLES", LogLevel.WARN, "Rescan packs called before business controller initialization")
        },
        onSelectPackConfig = { packId, configId ->
            Logger.log("CIRCLES", LogLevel.WARN, "Select pack config called before business controller initialization")
        },
        onGetCurrentState = {
            Logger.log("CIRCLES", LogLevel.WARN, "Get current state called before state initialization")
            CirclesState() // Return default state
        },
        onCheckPackInstalled = { packId, configId ->
            Logger.log("CIRCLES", LogLevel.WARN, "Check pack installed called before business controller initialization")
            false
        }
    )

    // Register parameter overlay
    Logger.log("CIRCLES_OVERLAY", LogLevel.INFO, "🎯 Registering circles parameter overlay")
    MapOverlayRegistry.register(moduleId, CirclesParameterOverlay())
    Logger.log("CIRCLES_OVERLAY", LogLevel.INFO, "🎯 Circles parameter overlay registered successfully")

    // Register settings using the standardized pattern
    registerModuleSettings(
        settingsRegistration = { registerCirclesSettings(this) },
        metadataProvider = CirclesSettingsProvider(this)
    )

    // Load persisted module state after settings registration
    loadPersistedModuleState()

    // ✅ Initialize reactive submenu state AFTER globalState is available
    circlesSubmenuOpen = globalState.navigationState.submenuVisible
        .map { submenu ->
            val isCircles = submenu == "circles"
            Logger.log("CIRCLES_SUBMENU", LogLevel.DEBUG, "Circles submenu state: submenu='$submenu', isCircles=$isCircles")
            isCircles
        }
        .stateIn(ScopeManager.uiScope, SharingStarted.Eagerly, false)

    Logger.log("CIRCLES", LogLevel.INFO, "Circles module initialization completed")
}

/**
 * Load persisted module state and apply to runtime state
 */
internal suspend fun CirclesModule.loadPersistedModuleState() {
    Logger.log("CIRCLES", LogLevel.DEBUG, "Loading all persisted settings for Circles module")

    // Load defaults from current state
    var circlesVisibility = currentState.circlesVisibility
    var sectorsOpacity = currentState.sectorsOpacity
    var airfieldsVisibility = currentState.airfieldsVisibility
    var airfieldRadius = currentState.airfieldRadius
    var airfieldClickSize = currentState.airfieldClickSize
    var labelOffset = currentState.labelOffset
    var circlesLabelSize = currentState.circlesLabelSize
    var circlesLabelSpacing = currentState.circlesLabelSpacing
    var airfieldLabelSize = currentState.airfieldLabelSize
    var circlesLineWidth = currentState.circlesLineWidth
    var airfieldIconSize = currentState.airfieldIconSize
    var circlesMinZoom = currentState.circlesMinZoom
    var circleLabelsMinZoom = currentState.circleLabelsMinZoom
    var airfieldIconsMinZoom = currentState.airfieldIconsMinZoom
    var airfieldLabelsMinZoom = currentState.airfieldLabelsMinZoom
    var sectorsMinZoom = currentState.sectorsMinZoom
    var activePackId: String? = null
    var activeConfigId: String? = null

    persistentStateKeys.forEach { stateKey ->
        if (stateKey.type.name == "FLOAT") {
            val loadedValue = settingPersistence.getFloat(stateKey.key, stateKey.defaultValue as Float)
            when (stateKey.key) {
                "sectorsOpacity" -> sectorsOpacity = loadedValue
                "airfieldRadius" -> airfieldRadius = loadedValue
                "airfieldClickSize" -> airfieldClickSize = loadedValue
                "labelOffset" -> labelOffset = loadedValue
                "circlesLabelSize" -> circlesLabelSize = loadedValue
                "circlesLabelSpacing" -> circlesLabelSpacing = loadedValue
                "airfieldLabelSize" -> airfieldLabelSize = loadedValue
                "circlesLineWidth" -> circlesLineWidth = loadedValue
                "airfieldIconSize" -> airfieldIconSize = loadedValue
                "circlesMinZoom" -> circlesMinZoom = loadedValue
                "circleLabelsMinZoom" -> circleLabelsMinZoom = loadedValue
                "airfieldIconsMinZoom" -> airfieldIconsMinZoom = loadedValue
                "airfieldLabelsMinZoom" -> airfieldLabelsMinZoom = loadedValue
                "sectorsMinZoom" -> sectorsMinZoom = loadedValue
            }
        } else if (stateKey.type.name == "BOOLEAN") {
            val loadedValue = settingPersistence.getBoolean(stateKey.key, stateKey.defaultValue as Boolean)
            when (stateKey.key) {
                "circlesVisibility" -> circlesVisibility = loadedValue
                "airfieldsVisibility" -> airfieldsVisibility = loadedValue
            }
        } else if (stateKey.type.name == "STRING") {
            val loadedValue = settingPersistence.getString(stateKey.key, stateKey.defaultValue as String)
            when (stateKey.key) {
                "activePackId" -> activePackId = loadedValue?.takeIf { it.isNotEmpty() }
                "activeConfigId" -> activeConfigId = loadedValue?.takeIf { it.isNotEmpty() }
            }
        }
        // INT settings are not used in this module
    }

    // Find the active config from the loaded pack/config IDs
    var activeConfig: PackConfig? = null
    if (activePackId != null && activeConfigId != null) {
        activeConfig = currentState.availableConfigs.find { config ->
            config.packId == activePackId && config.configId == activeConfigId
        }
        if (activeConfig != null) {
            Logger.log("CIRCLES", LogLevel.DEBUG, "Restored active config: $activePackId/$activeConfigId")
        } else {
            Logger.log("CIRCLES", LogLevel.WARN, "Could not restore active config: $activePackId/$activeConfigId not found in available configs")
            // Clear invalid persisted settings so they don't persist
            activePackId = null
            activeConfigId = null
            // Save the cleared settings
            settingPersistence.saveString("activePackId", null)
            settingPersistence.saveString("activeConfigId", null)
        }
    }

    Logger.log("CIRCLES", LogLevel.INFO, "Applying loaded persisted state: activeConfig=${activeConfig?.configId}")
    // Apply all loaded values at once
    updateState { currentState ->
        currentState.copy(
            circlesVisibility = circlesVisibility,
            sectorsOpacity = sectorsOpacity,
            airfieldsVisibility = airfieldsVisibility,
            airfieldRadius = airfieldRadius,
            airfieldClickSize = airfieldClickSize,
            labelOffset = labelOffset,
            circlesLabelSize = circlesLabelSize,
            circlesLabelSpacing = circlesLabelSpacing,
            airfieldLabelSize = airfieldLabelSize,
            circlesLineWidth = circlesLineWidth,
            airfieldIconSize = airfieldIconSize,
            circlesMinZoom = circlesMinZoom,
            circleLabelsMinZoom = circleLabelsMinZoom,
            airfieldIconsMinZoom = airfieldIconsMinZoom,
            airfieldLabelsMinZoom = airfieldLabelsMinZoom,
            sectorsMinZoom = sectorsMinZoom,
            activeConfig = activeConfig,
        )
    }
    Logger.log("CIRCLES", LogLevel.DEBUG, "Applied all loaded settings")
}