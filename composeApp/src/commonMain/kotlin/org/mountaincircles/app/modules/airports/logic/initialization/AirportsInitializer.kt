package org.mountaincircles.app.modules.airports.logic.initialization

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.modules.airports.import.logic.AirportsDownloadManager
import org.mountaincircles.app.modules.airports.import.logic.AirportsStorage
import org.mountaincircles.app.modules.airports.logic.business.AirportsBusinessService
import org.mountaincircles.app.network.createDownloadManager
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.settings.SettingPersistence
import org.mountaincircles.app.modules.airports.overlay.ui.AirportsPopupOverlay
import org.mountaincircles.app.modules.airports.settings.registerAirportsSettings
import org.mountaincircles.app.modules.airports.settings.logic.AirportsSettingsProvider
import org.mountaincircles.app.ui.overlay.MapOverlayRegistry
import org.mountaincircles.app.modules.airports.logic.data.AirportSources
import kotlinx.coroutines.launch
import org.mountaincircles.app.error.AppError
import org.mountaincircles.app.error.ErrorHandler

/**
 * Airports Module Initialization
 * Extracted initialization logic for better separation of concerns
 */

/**
 * Initialize the airports module - complete initialization including business logic setup
 */
suspend fun AirportsModule.initializeAirports() {
    // Initialize SettingPersistence
    settingPersistence = SettingPersistence(moduleId)

    // Get global file manager (initialized in MainActivity)
    Logger.log("AIRPORTS", LogLevel.DEBUG, "Getting global file manager")
    val fileManager = getGlobalFileManager()

    // Initialize storage
    Logger.log("AIRPORTS", LogLevel.DEBUG, "Initializing airports storage")
    storage = AirportsStorage(fileManager)

    // Initialize unified download manager
    Logger.log("AIRPORTS", LogLevel.DEBUG, "Initializing unified download manager")
    val downloadManager = createDownloadManager()

    // Initialize airports download manager
    Logger.log("AIRPORTS", LogLevel.DEBUG, "Initializing airports download manager")
    this.downloadManager = AirportsDownloadManager(downloadManager, storage)

    // Initialize airports business service
    Logger.log("AIRPORTS", LogLevel.DEBUG, "Initializing airports business service")
    businessService = AirportsBusinessService(this)


    // Register settings using the standardized pattern
    registerModuleSettings(
        settingsRegistration = { registerAirportsSettings(this) },
        metadataProvider = AirportsSettingsProvider(this)
    )
    Logger.log("AIRPORTS", LogLevel.DEBUG, "Settings registered using standardized pattern")

    // Load persisted module state after settings registration
    loadPersistedModuleState()

    // Register airports popup overlay
    Logger.log("AIRPORTS", LogLevel.DEBUG, "Registering airports popup overlay")
    MapOverlayRegistry.register(moduleId, AirportsPopupOverlay())

    // Scan for existing airport data
    rescanData()
    Logger.log("AIRPORTS", LogLevel.DEBUG, "Rescan completed")

    // ✅ MapLibre filtering: No filtered URI initialization needed - raw data used with dynamic filtering

    Logger.log("AIRPORTS", LogLevel.INFO, "Airports module initialization completed")
}

/**
 * Load persisted module state and apply to runtime state
 */
internal suspend fun AirportsModule.loadPersistedModuleState() {
    Logger.log("AIRPORTS", LogLevel.DEBUG, "Loading all persisted settings for Airports module")

    // Load defaults from current state
    var airportIconSize = currentState.airportIconSize
    var airportLabelSize = currentState.airportLabelSize
    var airportIconsMinZoom = currentState.airportIconsMinZoom
    var airportLabelsMinZoom = currentState.airportLabelsMinZoom
    var airportsVisibility = currentState.airportsVisibility
    var currentVisibleTypes: Set<String> = emptySet()
    var disabledAirportIds: Set<String> = emptySet()
    var selectedCountries: List<String> = AirportSources.defaultSelectedCountries

    persistentStateKeys.forEach { stateKey ->
        if (stateKey.type.name == "FLOAT") {
            val loadedValue = settingPersistence.getFloat(stateKey.key, stateKey.defaultValue as Float)
            when (stateKey.key) {
                "airportIconSize" -> airportIconSize = loadedValue
                "airportLabelSize" -> airportLabelSize = loadedValue
                "airportIconsMinZoom" -> airportIconsMinZoom = loadedValue
                "airportLabelsMinZoom" -> airportLabelsMinZoom = loadedValue
            }
        } else if (stateKey.type.name == "BOOLEAN") {
            val loadedValue = settingPersistence.getBoolean(stateKey.key, stateKey.defaultValue as Boolean)
            when (stateKey.key) {
                "airportsVisibility" -> airportsVisibility = loadedValue
            }
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
                "disabledAirportIds" -> {
                    disabledAirportIds = if (!loadedValue.isNullOrEmpty()) {
                        loadedValue.split(",").toSet()
                    } else {
                        emptySet()
                    }
                }
                "selectedCountries" -> {
                    selectedCountries = if (!loadedValue.isNullOrEmpty()) {
                        loadedValue.split(",").filter { it.isNotEmpty() }
                    } else {
                        AirportSources.defaultSelectedCountries
                    }
                }
            }
        }
        // INT settings are not used in this module
    }

    // Apply loaded values (including currentVisibleTypes, disabledAirportIds and selectedCountries) to trigger state update
    updateState { currentState.copy(
        airportIconSize = airportIconSize,
        airportLabelSize = airportLabelSize,
        airportIconsMinZoom = airportIconsMinZoom,
        airportLabelsMinZoom = airportLabelsMinZoom,
        airportsVisibility = airportsVisibility,
        currentVisibleTypes = currentVisibleTypes,
        disabledAirportIds = disabledAirportIds,
        selectedCountries = selectedCountries
    ) }
    Logger.log("AIRPORTS", LogLevel.DEBUG, "Applied loaded settings with currentVisibleTypes: $currentVisibleTypes, disabledAirportIds: $disabledAirportIds")

    // ✅ MapLibre filtering: No cache warming needed - state changes trigger reactive updates
}

/**
 * Rescan for existing airport data
 */
internal suspend fun AirportsModule.rescanData() {
    Logger.log("AIRPORTS", LogLevel.INFO, "Rescanning airports data")

    try {
        val result = businessService.rescanData()
        if (result.isSuccess) {
            Logger.log("AIRPORTS", LogLevel.DEBUG, "Airports data rescan completed")
        } else {
            val exception = result.exceptionOrNull() ?: Exception("Unknown rescan error")
            val error = AppError.ModuleError(moduleId, "Failed to rescan airports data: ${exception.message}", exception)
            ErrorHandler.handle(error, "AirportsModule.rescanData")
        }
    } catch (e: Exception) {
        val error = AppError.ModuleError(moduleId, "Exception rescanning airports data: ${e.message}", e)
        ErrorHandler.handle(error, "AirportsModule.rescanData")
    }
}
