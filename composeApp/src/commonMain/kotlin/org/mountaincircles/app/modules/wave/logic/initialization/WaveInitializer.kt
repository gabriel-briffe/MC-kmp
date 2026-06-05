package org.mountaincircles.app.modules.wave.logic.initialization

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.modules.wave.logic.controllers.WaveManager
import org.mountaincircles.app.modules.wave.settings.registerWaveSettings
import org.mountaincircles.app.modules.wave.settings.logic.WaveSettingsProvider
import org.mountaincircles.app.ui.settings.ModuleSettingsRegistry
import org.mountaincircles.app.modules.wave.logic.data.WaveSelection
import org.mountaincircles.app.settings.SettingPersistence
import org.mountaincircles.app.settings.SettingType

/**
 * Wave Module Initialization
 * Extracted initialization logic for better separation of concerns
 */

/**
 * Initialize the wave module - scan for existing files and load settings
 */
suspend fun WaveModule.initializeWave() {
    // Scan for existing wave files to populate the module state FIRST
    try {
        val existingEntries = waveManager.scan()
        Logger.log("WAVE", LogLevel.INFO, "Found ${existingEntries.size} existing wave entries during initialization")

        // Set initialized state with existing entries
        updateState {
            it.copy(
                entries = existingEntries,
                isInitialized = true,
            )
        }
    } catch (e: Exception) {
        Logger.log("WAVE", LogLevel.ERROR, "Failed to scan existing wave files during initialization: ${e.message}", e)
        // Still set initialized even if scan fails
        updateState {
            it.copy(
                isInitialized = true,
            )
        }
    }

    // Register settings using the standardized pattern
    registerModuleSettings(
        settingsRegistration = { registerWaveSettings(this) },
        metadataProvider = WaveSettingsProvider(this)
    )
    Logger.log("WAVE", LogLevel.DEBUG, "Wave settings registered using standardized pattern")

    // Load persisted module state after settings registration
    loadPersistedModuleState()

    Logger.log("WAVE", LogLevel.INFO, "Wave module initialization complete")
}

/**
 * Load persisted module state and apply to runtime state
 */
internal suspend fun WaveModule.loadPersistedModuleState() {
    Logger.log("WAVE", LogLevel.DEBUG, "Loading all persisted settings for Wave module")

    // Load defaults from current state
    var opacity = currentState.opacity
    var mainLabelFontSize = currentState.mainLabelFontSize
    var subLabelFontSize = currentState.subLabelFontSize
    var windBarbSize = currentState.windBarbSize
    var windSpeedScaleDistortion = currentState.windSpeedScaleDistortion
    var barbInterval = currentState.barbInterval
    var showZeroWindBarbs = currentState.showZeroWindBarbs
    var waveVisibility = currentState.isVisible
    var windLayerVisibility = currentState.windLayerVisible
    var selectedForecastDate = currentState.selection.forecastDate
    var selectedTargetDate = currentState.selection.targetDate
    var selectedHour = currentState.selection.hour
    var selectedPressure = currentState.selection.pressure
    var selectedFilePath = currentState.selection.filePath

    persistentStateKeys.forEach { stateKey ->
        if (stateKey.type.name == "FLOAT") {
            val loadedValue = settingPersistence.getFloat(stateKey.key, stateKey.defaultValue as Float)
            when (stateKey.key) {
                "opacity" -> opacity = loadedValue
                "mainLabelFontSize" -> mainLabelFontSize = loadedValue
                "subLabelFontSize" -> subLabelFontSize = loadedValue
                "windBarbSize" -> windBarbSize = loadedValue
                "windSpeedScaleDistortion" -> windSpeedScaleDistortion = loadedValue
                "barbInterval" -> barbInterval = loadedValue
            }
        } else if (stateKey.type.name == "BOOLEAN") {
            val loadedValue = settingPersistence.getBoolean(stateKey.key, stateKey.defaultValue as Boolean)
            when (stateKey.key) {
                "waveVisibility" -> waveVisibility = loadedValue
                "windLayerVisibility" -> windLayerVisibility = false // Always start as false, not persistent
                "showZeroWindBarbs" -> showZeroWindBarbs = loadedValue
            }
        } else if (stateKey.type.name == "INT") {
            val loadedValue = settingPersistence.getInt(stateKey.key, stateKey.defaultValue as Int)
            when (stateKey.key) {
                "selectedHour" -> selectedHour = loadedValue
                "selectedPressure" -> selectedPressure = loadedValue
            }
        } else if (stateKey.type.name == "STRING") {
            val loadedValue = settingPersistence.getString(stateKey.key, stateKey.defaultValue as String)
            when (stateKey.key) {
                "selectedForecastDate" -> selectedForecastDate = loadedValue ?: ""
                "selectedTargetDate" -> selectedTargetDate = loadedValue ?: ""
                "selectedFilePath" -> selectedFilePath = loadedValue ?: ""
            }
        }
    }

    // Apply basic settings first (without selection to avoid navigation capability issues)
    updateState { currentState.copy(
        opacity = opacity,
        mainLabelFontSize = mainLabelFontSize,
        subLabelFontSize = subLabelFontSize,
        windBarbSize = windBarbSize,
        windSpeedScaleDistortion = windSpeedScaleDistortion,
        barbInterval = barbInterval,
        showZeroWindBarbs = showZeroWindBarbs,
        isVisible = waveVisibility,
        windLayerVisible = windLayerVisibility
    ) }

    // Now set selection properly with navigation capabilities calculation
    val loadedSelection = WaveSelection(
        forecastDate = selectedForecastDate,
        targetDate = selectedTargetDate,
        hour = selectedHour,
        pressure = selectedPressure,
        filePath = selectedFilePath
    )

    // Use the existing updateSelection method that calculates navigation capabilities
    if (loadedSelection.isValid()) {
        updateSelection(loadedSelection, persist = false) // Don't re-persist during loading
        Logger.log("WAVE", LogLevel.DEBUG, "Restored selection with navigation capabilities: $loadedSelection")
    } else {
        Logger.log("WAVE", LogLevel.WARN, "Loaded invalid selection, skipping: $loadedSelection")
    }

    Logger.log("WAVE", LogLevel.DEBUG, "Applied all loaded settings")
}
