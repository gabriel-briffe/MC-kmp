package org.mountaincircles.app.modules.airspace.logic.initialization

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airspace.AirspaceModule
import org.mountaincircles.app.modules.airspace.import.logic.AirspaceDownloadManager
import org.mountaincircles.app.modules.airspace.import.logic.OpenAirParser
import org.mountaincircles.app.modules.airspace.import.logic.AirspaceStorage
import org.mountaincircles.app.modules.airspace.overlay.ui.AirspacePopupOverlay
import org.mountaincircles.app.ui.overlay.MapOverlayRegistry
import org.mountaincircles.app.settings.SettingPersistence
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceSources
import org.mountaincircles.app.ui.modules.ComposableProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.network.createDownloadManager

/**
 * Airspace Module Initialization
 * Extracted initialization logic for better separation of concerns
 */

/**
 * Initialize the airspace module - set up managers, controllers, and overlays
 */
suspend fun AirspaceModule.initializeAirspace() {
    // Get the global file manager (already initialized in MainActivity)
    Logger.log("AIRSPACE", LogLevel.DEBUG, "Getting global file manager")
    fileManager = getGlobalFileManager()

    // Initialize OpenAir parser
    Logger.log("AIRSPACE", LogLevel.DEBUG, "Initializing OpenAir parser")
    parser = OpenAirParser()

    // Initialize storage
    Logger.log("AIRSPACE", LogLevel.DEBUG, "Initializing airspace storage")
    storage = AirspaceStorage(fileManager)

    // Initialize unified download manager
    Logger.log("AIRSPACE", LogLevel.DEBUG, "Initializing unified download manager")
    downloadManager = createDownloadManager()

    // Initialize airspace download manager
    Logger.log("AIRSPACE", LogLevel.DEBUG, "Initializing airspace download manager")
    airspaceDownloadManager = AirspaceDownloadManager(downloadManager, parser, storage)

    // Initialize controllers
    Logger.log("AIRSPACE", LogLevel.DEBUG, "Initializing controllers")

    // Scan for existing airspace data
    val hasExistingData = rescanData()
    Logger.log("AIRSPACE", LogLevel.DEBUG, "After rescanData, importedAt: ${currentState.importedAt}, selectedCountries: ${currentState.selectedCountries}, hasData: $hasExistingData")

    // Register airspace popup overlay
    Logger.log("AIRSPACE", LogLevel.DEBUG, "Registering airspace popup overlay")
    MapOverlayRegistry.register(moduleId, AirspacePopupOverlay())

    // Load persisted module state after settings registration
    loadPersistedModuleState()

    // Mark data availability (module initialization status set in onInitialize)
    updateState { currentState.copy(
        hasDataToRender = hasExistingData
    ) }
}

/**
 * Load persisted module state and apply to runtime state
 */
internal suspend fun AirspaceModule.loadPersistedModuleState() {
    Logger.log("AIRSPACE", LogLevel.DEBUG, "Loading all persisted settings for Airspace module")

    // Load defaults from current state
    var airspaceVisibility = false
    var currentVisibleTypes: Set<String> = emptySet()
    var selectedCountries: List<String> = AirspaceSources.defaultSelectedCountries

    persistentStateKeys.forEach { stateKey ->
        if (stateKey.type.name == "BOOLEAN") {
            val loadedValue = settingPersistence.getBoolean(stateKey.key, stateKey.defaultValue as Boolean)
            when (stateKey.key) {
                "airspaceVisibility" -> airspaceVisibility = loadedValue
            }
        } else if (stateKey.type.name == "FLOAT") {
            // Handle float settings here if needed
        } else if (stateKey.type.name == "STRING") {
            val loadedValue = settingPersistence.getString(stateKey.key, stateKey.defaultValue as String)
            when (stateKey.key) {
                "currentVisibleTypes" -> {
                    currentVisibleTypes = if (!loadedValue.isNullOrEmpty()) {
                        loadedValue.split(",").toSet()
                    } else {
                        emptySet()
                    }
                }
                "selectedCountries" -> {
                    selectedCountries = if (!loadedValue.isNullOrEmpty()) {
                        loadedValue.split(",").filter { it.isNotEmpty() }
                    } else {
                        AirspaceSources.defaultSelectedCountries
                    }
                }
            }
        } else if (stateKey.type.name == "INT") {
            // Handle int settings here if needed
        }
    }

    // Apply loaded values (including currentVisibleTypes and selectedCountries) to trigger state update
    updateState { currentState.copy(
        isVisible = airspaceVisibility,
        currentVisibleTypes = currentVisibleTypes,
        selectedCountries = selectedCountries
    ) }
    Logger.log("AIRSPACE", LogLevel.DEBUG, "Applied loaded settings with currentVisibleTypes: $currentVisibleTypes")
}

/**
 * Rescan for existing airspace data
 */
internal suspend fun AirspaceModule.rescanData(): Boolean = businessService.rescanData()
