package org.mountaincircles.app.modules.airspace.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.mountaincircles.app.error.ErrorHandler
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airspace.AirspaceModule
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceSources
import org.mountaincircles.app.ui.components.UnifiedProgress
import org.mountaincircles.app.utils.ScopeManager

/**
 * ViewModel implementation for Airspace import section
 * Handles business logic and state management for airspace import UI
 */
class AirspaceImportViewModelImpl(
    private val airspaceModule: AirspaceModule
) : AirspaceImportViewModel {

    private val _uiState = MutableStateFlow(AirspaceImportUiState())
    override val uiState: StateFlow<AirspaceImportUiState> = _uiState

    override val progressFlow: StateFlow<UnifiedProgress> = airspaceModule.progressFlow

    init {
        // Combine module state into UI state
        ScopeManager.uiScope.launch {
            airspaceModule.airspaceState.collect { airspaceState ->
                _uiState.value = AirspaceImportUiState(
                    selectedCountries = airspaceState.selectedCountries,
                    hasData = airspaceState.hasDataToRender ?: false,
                    importedAt = airspaceState.importedAt,
                    hasError = airspaceState.hasError,
                    errorMessage = airspaceState.errorMessage,
                    isDownloading = airspaceState.isDownloading
                )
            }
        }
    }

    override fun handleAction(action: AirspaceImportUiAction) {
        when (action) {
            is AirspaceImportUiAction.UpdateCountrySelection -> {
                handleUpdateCountrySelection(action.countryCodes)
            }
            is AirspaceImportUiAction.ToggleCountry -> {
                handleToggleCountry(action.countryCode, action.selected)
            }
            is AirspaceImportUiAction.SelectAllCountries -> {
                handleSelectAllCountries()
            }
            is AirspaceImportUiAction.SelectNoCountries -> {
                handleSelectNoCountries()
            }
            is AirspaceImportUiAction.ImportAirspace -> {
                handleImportAirspace()
            }
            is AirspaceImportUiAction.ClearAirspaceData -> {
                handleClearAirspaceData()
            }
            is AirspaceImportUiAction.RefreshStatus -> {
                handleRefreshStatus()
            }
        }
    }

    private fun handleUpdateCountrySelection(countryCodes: List<String>) {
        try {
            Logger.log("AIRSPACE_UI", LogLevel.INFO, "Updating country selection to: ${countryCodes.joinToString()}")
            airspaceModule.updateSelectedCountries(countryCodes)
        } catch (e: Exception) {
            val error = AirspaceError.CountrySelectionError("Failed to update country selection", e)
            ErrorHandler.handle(error.toAppError())
        }
    }

    private fun handleToggleCountry(countryCode: String, selected: Boolean) {
        try {
            val currentSelection = _uiState.value.selectedCountries
            val newSelection = if (selected) {
                currentSelection + countryCode
            } else {
                currentSelection - countryCode
            }
            Logger.log("AIRSPACE_UI", LogLevel.INFO, "Toggling country $countryCode to $selected")
            airspaceModule.updateSelectedCountries(newSelection)
        } catch (e: Exception) {
            val error = AirspaceError.CountrySelectionError("Failed to toggle country selection", e)
            ErrorHandler.handle(error.toAppError())
        }
    }

    private fun handleSelectAllCountries() {
        try {
            val allCodes = AirspaceSources.countries.map { it.code }
            Logger.log("AIRSPACE_UI", LogLevel.INFO, "Selecting all countries: ${allCodes.size} countries")
            airspaceModule.updateSelectedCountries(allCodes)
        } catch (e: Exception) {
            val error = AirspaceError.CountrySelectionError("Failed to select all countries", e)
            ErrorHandler.handle(error.toAppError())
        }
    }

    private fun handleSelectNoCountries() {
        try {
            Logger.log("AIRSPACE_UI", LogLevel.INFO, "Clearing all country selections")
            airspaceModule.updateSelectedCountries(emptyList())
        } catch (e: Exception) {
            val error = AirspaceError.CountrySelectionError("Failed to clear country selections", e)
            ErrorHandler.handle(error.toAppError())
        }
    }

    private fun handleImportAirspace() {
        try {
            val selectedCountries = _uiState.value.selectedCountries
            Logger.log("AIRSPACE_UI", LogLevel.INFO, "Starting airspace import for countries: ${selectedCountries.joinToString()}")

            if (selectedCountries.isEmpty()) {
                val error = AirspaceError.ValidationError("No countries selected for import")
                ErrorHandler.handle(error.toAppError())
                return
            }

            ScopeManager.uiScope.launch {
                try {
                    airspaceModule.downloadAirspacePipeline()
                    Logger.log("AIRSPACE_UI", LogLevel.INFO, "Airspace import pipeline started successfully")
                } catch (e: Exception) {
                    val error = AirspaceError.ImportError("Import pipeline failed", selectedCountries, e)
                    ErrorHandler.handle(error.toAppError())
                }
            }
        } catch (e: Exception) {
            val error = AirspaceError.ModuleError("Failed to initiate import", "import_initiation", e)
            ErrorHandler.handle(error.toAppError())
        }
    }

    private fun handleClearAirspaceData() {
        try {
            Logger.log("AIRSPACE_UI", LogLevel.INFO, "Clearing airspace data")

            ScopeManager.uiScope.launch {
                try {
                    airspaceModule.clearAirspaceData()
                    Logger.log("AIRSPACE_UI", LogLevel.INFO, "Airspace data cleared successfully")
                } catch (e: Exception) {
                    val error = AirspaceError.ClearError("Failed to clear airspace data", e)
                    ErrorHandler.handle(error.toAppError())
                }
            }
        } catch (e: Exception) {
            val error = AirspaceError.ModuleError("Failed to initiate clear operation", "clear_initiation", e)
            ErrorHandler.handle(error.toAppError())
        }
    }

    private fun handleRefreshStatus() {
        try {
            Logger.log("AIRSPACE_UI", LogLevel.INFO, "Refreshing airspace status")
            // Status is automatically refreshed through state collection
            // This action can be used for manual refresh triggers if needed
        } catch (e: Exception) {
            val error = AirspaceError.ModuleError("Failed to refresh status", "status_refresh", e)
            ErrorHandler.handle(error.toAppError())
        }
    }

    override fun resetDownloadState() {
        // Reset any UI-specific state if needed
        Logger.log("AIRSPACE_UI", LogLevel.DEBUG, "Resetting download state")
    }
}
