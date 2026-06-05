package org.mountaincircles.app.modules.airports.logic.business

import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.modules.airports.logic.AirportsConstants
import org.mountaincircles.app.utils.currentTimeMillis

/**
 * Airports Business Service - orchestrates airport download operations
 * Cloned from AirspaceBusinessService
 */
class AirportsBusinessService(private val module: AirportsModule) {

    /**
     * Import airports for selected countries
     */
    suspend fun importAirportsForSelectedCountries(): Result<Unit> {
        Logger.log("AIRPORTS", LogLevel.DEBUG, "Importing airports for selected countries")
        return importAirportsForSelectedCountries(module)
    }

    /**
     * Import airports for specific countries
     */
    suspend fun importAirportsForCountries(countries: Set<String>): Result<Unit> {
        Logger.log("AIRPORTS", LogLevel.DEBUG, "Importing airports for countries: $countries")
        return importAirportsForCountries(module, countries.toList())
    }

    /**
     * Clear all airport data
     */
    suspend fun clearAirportsData(): Result<Unit> {
        Logger.log("AIRPORTS", LogLevel.DEBUG, "Clearing all airport data")
        return clearAirportsData(module)
    }

    /**
     * Rescan existing airport data
     */
    suspend fun rescanData(): Result<Unit> {
        Logger.log("AIRPORTS", LogLevel.DEBUG, "Rescanning airport data")
        return rescanData(module)
    }

    /**
     * Toggle airport type visibility (show/hide)
     */
    fun toggleAirportType(type: String, visible: Boolean) {
        Logger.log("AIRPORTS", LogLevel.INFO, "toggleAirportType: $type -> $visible")

        val currentState = module.airportsState.value
        val currentVisibleTypes = currentState.currentVisibleTypes.toMutableSet()

        if (visible) {
            // User wants to show this type → add to visible list
            currentVisibleTypes.add(type)
        } else {
            // User wants to hide this type → remove from visible list
            currentVisibleTypes.remove(type)
        }

        val newState = currentState.copy(currentVisibleTypes = currentVisibleTypes)
        module.updateState { newState }

        Logger.log("AIRPORTS", LogLevel.DEBUG, "Updated current visible types: ${currentVisibleTypes}")
        Logger.log("AIRPORTS", LogLevel.INFO, "Updated visible types: ${currentVisibleTypes}")

        // ✅ MapLibre filtering: State change triggers reactive UI updates automatically

        // Persist the currentVisibleTypes immediately
        val visibleTypesString = currentVisibleTypes.joinToString(",")
        org.mountaincircles.app.utils.ScopeManager.ioScope.launch {
            module.settingPersistence.saveString("currentVisibleTypes", visibleTypesString)
        }
    }

    /**
     * Toggle the disabled state of an airport by ICAO code
     */
    suspend fun toggleAirportDisabledState(airportId: String): Result<Unit> {
        Logger.log("AIRPORTS", LogLevel.INFO, "🔄 STARTED: Toggling disabled state for airport $airportId")

        return try {
            val currentState = module.airportsState.value
            val currentDisabledIds = currentState.disabledAirportIds.toMutableSet()

            val wasDisabled = currentDisabledIds.contains(airportId)
            if (wasDisabled) {
                // Currently disabled, enable it
                currentDisabledIds.remove(airportId)
                Logger.log("AIRPORTS", LogLevel.INFO, "Enabling airport $airportId")
            } else {
                // Currently enabled, disable it
                currentDisabledIds.add(airportId)
                Logger.log("AIRPORTS", LogLevel.INFO, "Disabling airport $airportId")
            }

            // Update state
            val newState = currentState.copy(disabledAirportIds = currentDisabledIds)
            module.updateState { newState }

            // Persist the disabled IDs immediately
            val disabledIdsString = currentDisabledIds.joinToString(",")
            org.mountaincircles.app.utils.ScopeManager.ioScope.launch {
                module.settingPersistence.saveString("disabledAirportIds", disabledIdsString)
            }

            Logger.log("AIRPORTS", LogLevel.INFO, "✅ COMPLETED: Successfully toggled disabled state for airport $airportId")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.log("AIRPORTS", LogLevel.ERROR, "❌ ERROR: Exception while toggling disabled state for airport $airportId: ${e.message}", e)
            Result.failure(e)
        }
    }


    /**
     * Get airports directory path
     */
    fun getAirportsDirectory(): String {
        val dataDir = org.mountaincircles.app.io.getGlobalFileManager().getAppDataDirectory()
        return "$dataDir/${org.mountaincircles.app.modules.airports.logic.AirportsConstants.AIRPORTS_DIR}"
    }




    /**
     * Show airport popup with clicked features
     */
    fun showAirportPopup(features: List<org.mountaincircles.app.modules.airports.logic.data.AirportFeatureData>) {
        Logger.log("AIRPORTS_POPUP", LogLevel.DEBUG, "Showing airport popup with ${features.size} features")
        module.updateState { state ->
            state.copy(
                showPopup = true,
                popupFeatures = features
            )
        }
        Logger.log("AIRPORTS_POPUP", LogLevel.INFO, "Airport popup shown with ${features.size} features")
    }

    /**
     * Hide airport popup
     */
    fun hideAirportPopup() {
        Logger.log("AIRPORTS_POPUP", LogLevel.INFO, "🔴 STARTING: Hiding airport popup")
        module.updateState { state ->
            state.copy(
                showPopup = false,
                popupFeatures = emptyList()
            )
        }
        Logger.log("AIRPORTS_POPUP", LogLevel.INFO, "✅ COMPLETED: Airport popup hidden")
    }


    /**
     * Get list of CUP files in the airports directory
     */
    suspend fun getCupFilesList(): List<String> {
        return module.airportsStorage.getCupFilesList()
    }

    // ✅ Disabled airports persistence removed - now stored in reactive state

    // ✅ Disabled airports persistence removed - now handled via reactive state

}
