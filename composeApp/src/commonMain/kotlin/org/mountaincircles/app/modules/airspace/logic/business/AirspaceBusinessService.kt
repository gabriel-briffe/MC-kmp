package org.mountaincircles.app.modules.airspace.logic.business

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airspace.AirspaceModule
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceSources
import org.mountaincircles.app.modules.airspace.logic.AirspaceConstants
import org.mountaincircles.app.state.getGlobalState
import org.mountaincircles.app.utils.currentTimeMillis
import kotlinx.coroutines.launch

/**
 * Airspace Business Service
 * Handles core business logic for airspace operations
 */
class AirspaceBusinessService(private val module: AirspaceModule) {

    /**
     * Refresh airspace data
     */
    /**
     * Save airspace settings
     */
    suspend fun saveAirspaceSettings(): Result<Unit> {
        return try {
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Saving airspace settings")
            saveAirspaceSettings(module)
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.log("AIRSPACE", LogLevel.ERROR, "Failed to save airspace settings: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update airspace visibility
     */
    suspend fun updateVisibility(visible: Boolean): Result<Unit> {
        return try {
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Updating airspace visibility: $visible")
            module.updateState { it.copy(isVisible = visible) }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.log("AIRSPACE", LogLevel.ERROR, "Failed to update airspace visibility: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Caching removed - MapLibre handles filtering dynamically

    // Cache warming removed - filtering is dynamic

    // Cache invalidation removed - no caching

    /**
     * Rescan airspace data
     */
    suspend fun rescanData(): Boolean {
        Logger.log("AIRSPACE", LogLevel.DEBUG, "Rescanning airspace data")
        return rescanData(module)
    }

    /**
     * Import airspace for selected countries
     */
    suspend fun importAirspaceForSelectedCountries(): Result<Unit> {
        Logger.log("AIRSPACE", LogLevel.DEBUG, "Importing airspace for selected countries")
        return importAirspaceForSelectedCountries(module)
    }

    /**
     * Import airspace for specific countries
     */
    suspend fun importAirspaceForCountries(countries: Set<String>): Result<Unit> {
        Logger.log("AIRSPACE", LogLevel.DEBUG, "Importing airspace for countries: $countries")
        return importAirspaceForCountries(module, countries.toList())
    }

    /**
     * Clear all airspace data
     */
    suspend fun clearAirspaceData() {
        Logger.log("AIRSPACE", LogLevel.INFO, "Clearing all airspace data")
        try {
            val result = module.airspaceStorage.clearAirspaceData()
            if (result.isSuccess) {
                Logger.log("AIRSPACE", LogLevel.INFO, "Airspace data cleared successfully")

                // No cached URI to clear - using raw data

                // Reset state to indicate no data
                module.updateState {
                    it.copy(
                        isInitialized = true,
                        hasError = false,
                        errorMessage = null,
                        hasDataToRender = false,
                        isVisible = false
                    )
                }
            } else {
                Logger.log("AIRSPACE", LogLevel.ERROR, "Failed to clear airspace data: ${result.exceptionOrNull()?.message}")
                throw result.exceptionOrNull() ?: Exception("Failed to clear airspace data")
            }
        } catch (e: Exception) {
            Logger.log("AIRSPACE", LogLevel.ERROR, "Exception clearing airspace data: ${e.message}", e)
            throw e
        }
    }

    /**
     * Check if airspace data is available
     */
    suspend fun hasAirspaceData(): Boolean {
        Logger.log("AIRSPACE", LogLevel.DEBUG, "Checking if airspace data is available")
        return module.airspaceStorage.hasAirspaceData()
    }

    /**
     * Toggle airspace layer visibility
     */
    suspend fun toggleVisibility(): Result<Unit> {
        Logger.log("AIRSPACE", LogLevel.DEBUG, "Toggling airspace visibility")
        val currentVisibility = module.airspaceState.value.isVisible
        Logger.log("AIRSPACE", LogLevel.INFO, "Airspace visibility toggled: $currentVisibility -> ${!currentVisibility}")
        return updateVisibility(!currentVisibility)
    }

    /**
     * Toggle airspace type visibility
     */
    fun toggleAirspaceType(type: String, visible: Boolean) {
        Logger.log("AIRSPACE", LogLevel.INFO, "toggleAirspaceType: $type -> $visible")

        val currentState = module.airspaceState.value
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

        Logger.log("AIRSPACE", LogLevel.DEBUG, "Updated visible types: ${currentVisibleTypes}")
        Logger.log("AIRSPACE", LogLevel.INFO, "Updated current visible types: ${currentVisibleTypes}")

        // Persist the currentVisibleTypes immediately
        val visibleTypesString = currentVisibleTypes.joinToString(",")
        org.mountaincircles.app.utils.ScopeManager.ioScope.launch {
            module.settingPersistence.saveString("currentVisibleTypes", visibleTypesString)
        }

        // Apply filter to the layer if it's currently visible
        module.layerManagerInstance?.applyTypeFilter(currentVisibleTypes)

        // Save the updated filter settings
        org.mountaincircles.app.utils.ScopeManager.ioScope.launch {
            saveAirspaceSettings().onFailure { e ->
                Logger.log("AIRSPACE", LogLevel.ERROR, "Failed to save airspace settings after filter change: ${e.message}", e)
            }
        }
    }

    /**
     * Get airspace directory path
     */
    fun getAirspaceDirectory(): String {
        val dataDir = org.mountaincircles.app.io.getGlobalFileManager().getAppDataDirectory()
        return "$dataDir/${AirspaceConstants.AIRSPACE_DIR}"
    }


    /**
     * Update selected countries
     */
    fun updateSelectedCountries(countryCodes: List<String>) {
        Logger.log("AIRSPACE", LogLevel.INFO, "Updating selected countries: $countryCodes")

        // Update state with new countries
        val currentState = module.airspaceState.value
        val newState = currentState.copy(selectedCountries = countryCodes)
        module.updateState { newState }

        // ✅ SAVE SETTINGS immediately after state update (in coroutine)
        org.mountaincircles.app.utils.ScopeManager.uiScope.launch {
            try {
                val countriesString = countryCodes.joinToString(",")
                module.settingPersistence.saveString("selectedCountries", countriesString)
                Logger.log("AIRSPACE", LogLevel.DEBUG, "Saved selected countries to settings: ${countryCodes.size} countries")
            } catch (e: Exception) {
                Logger.log("AIRSPACE", LogLevel.ERROR, "Failed to save selected countries to settings: ${e.message}")
            }
        }

        // ❌ REMOVED: No data refresh on country selection - only on actual import
    }


    /**
     * Show airspace popup with clicked features
     */
    fun showAirspacePopup(features: List<org.mountaincircles.app.modules.airspace.logic.data.AirspaceFeatureData>) {
        Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "Showing airspace popup with ${features.size} features")

        if (features.isNotEmpty()) {
            val currentState = module.airspaceState.value
            val newState = currentState.copy(
                showPopup = true,
                popupFeatures = features
            )
            module.updateState { newState }

            Logger.log("AIRSPACE_POPUP", LogLevel.INFO, "Airspace popup shown with ${features.size} features")
        } else {
            Logger.log("AIRSPACE_POPUP", LogLevel.WARN, "showAirspacePopup called with no features")
        }
    }

    /**
     * Hide airspace popup
     */
    fun hideAirspacePopup() {
        Logger.log("AIRSPACE_POPUP", LogLevel.INFO, "🔴 STARTING: Hiding airspace popup")

        // 🎯 STEP 1: Hide marker FIRST for immediate visual feedback
        Logger.log("AIRSPACE_POPUP", LogLevel.INFO, "🎯 Hiding marker first...")
        try {
            val globalState = getGlobalState()
            globalState.hideMarker()
            Logger.log("AIRSPACE_POPUP", LogLevel.INFO, "✅ Marker hidden immediately")
        } catch (e: Exception) {
            Logger.log("AIRSPACE_POPUP", LogLevel.ERROR, "❌ Failed to hide marker: ${e.message}", e)
        }

        // 🎯 STEP 2: Update popup state (this will hide the overlay)
        val currentState = module.airspaceState.value
        Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "Current state - showPopup: ${currentState.showPopup}, features: ${currentState.popupFeatures.size}")

        val newState = currentState.copy(
            showPopup = false,
            popupFeatures = emptyList()
        )
        module.updateState { newState }
        Logger.log("AIRSPACE_POPUP", LogLevel.DEBUG, "State updated - popup hidden")

        // 🎯 STEP 3: Any layer readjustments happen automatically as overlay is removed
        Logger.log("AIRSPACE_POPUP", LogLevel.INFO, "✅ COMPLETED: Marker hidden, popup closed, layers readjusted")
    }

}
