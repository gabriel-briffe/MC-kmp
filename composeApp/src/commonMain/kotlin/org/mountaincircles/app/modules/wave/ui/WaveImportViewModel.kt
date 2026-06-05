package org.mountaincircles.app.modules.wave.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.utils.ScopeManager
import org.mountaincircles.app.modules.wave.import.logic.WaveImportType
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.utils.ErrorHandlerUtils

interface WaveImportViewModel {
    val uiState: StateFlow<WaveImportUiState>
    val progressFlow: StateFlow<org.mountaincircles.app.ui.components.UnifiedProgress>

    fun handleAction(action: WaveImportUiAction)
    fun resetDownloadState() // For cleanup when composable is disposed

    fun getTotalWaveFilesInMemory(): Int
    fun getTotalWindFilesInMemory(): Int
    fun getTotalWaveFileSize(): Long
    fun getTotalWindFileSize(): Long
}

class WaveImportViewModelImpl(
    private val waveModule: WaveModule
) : WaveImportViewModel {
    private val viewModelScope = ScopeManager.uiScope
    private val calculator = ForecastAvailabilityCalculator(waveModule)

    private val _uiState = MutableStateFlow(WaveImportUiState())

    // Expose progress flow directly from module for UI
    override val progressFlow = waveModule.progressFlow

    override fun resetDownloadState() {
        waveModule.resetDownloadState()
    }

    override val uiState = combine(
        waveModule.progressFlow,
        waveModule.entriesFlow,
        _uiState
    ) { unifiedProgress, entriesCount, currentState ->
        currentState.copy(
            entriesCount = entriesCount as Int,
            availableForecasts = calculator.getAvailableForecasts(),
            downloadedForecasts = calculator.getAvailableForecasts().toSet(),
            forecastFileCounts = calculator.getFileCounts(),
            forecastWaveFileCounts = calculator.getWaveFileCounts(),
            forecastWindFileCounts = calculator.getWindFileCounts(),
            forecastWindFileCountsByRegion = calculator.getWindFileCountsByRegion()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = WaveImportUiState()
    )

    override fun handleAction(action: WaveImportUiAction) {
        when (action) {
            is WaveImportUiAction.DownloadForecast -> {
                handleDownloadForecast(action.forecastType)
            }
            is WaveImportUiAction.ToggleIncludeWindFiles -> {
                handleToggleIncludeWindFiles(action.include)
            }
            is WaveImportUiAction.ToggleWindRegion -> {
                handleToggleWindRegion(action.region, action.selected)
            }
            WaveImportUiAction.CancelDownload -> {
                handleCancelDownload()
            }
            WaveImportUiAction.ClearAllFiles -> {
                handleClearAllFiles()
            }
            WaveImportUiAction.RefreshAvailability -> {
                handleRefreshAvailability()
            }
        }
    }

    /**
     * Update forecast availability in UI state
     */
    private fun updateForecastAvailability() {
                _uiState.value = _uiState.value.copy(
                    availableForecasts = calculator.getAvailableForecasts(),
                    downloadedForecasts = calculator.getAvailableForecasts().toSet(),
                    forecastFileCounts = calculator.getFileCounts(),
                    forecastWaveFileCounts = calculator.getWaveFileCounts(),
                    forecastWindFileCounts = calculator.getWindFileCounts(),
                    forecastWindFileCountsByRegion = calculator.getWindFileCountsByRegion(),
                    error = null
                )
    }

    /**
     * Handle errors consistently across all operations
     */
    private fun handleError(message: String, exception: Exception, operation: String) {
        val waveError = ErrorHandlerUtils.handleWaveError(message, exception, operation)
        _uiState.value = _uiState.value.copy(error = waveError)
    }

    private fun handleDownloadForecast(forecastType: String) {
        viewModelScope.launch {
            try {
                // Clear any previous error and set active download
                _uiState.value = _uiState.value.copy(
                    error = null,
                    activeDownload = WaveImportType.valueOf(forecastType)
                )

                // Start download
                waveModule.downloadWavePipeline(WaveImportType.valueOf(forecastType), _uiState.value.includeWindFiles, _uiState.value.selectedWindRegions)

            } catch (e: Exception) {
                handleError("Download failed for $forecastType: ${e.message}", e, "downloadForecast")
                _uiState.value = _uiState.value.copy(activeDownload = null)
            } finally {
                // Clear active download
                _uiState.value = _uiState.value.copy(activeDownload = null)
            }
        }
    }

    private fun handleCancelDownload() {
        viewModelScope.launch {
            try {
                // Cancel ALL active downloads (user can click any button to cancel)
                val cancelledCount = waveModule.cancelAllWaveDownloads()

                if (cancelledCount > 0) {
                    Logger.log("WaveImportViewModel", LogLevel.INFO, "Cancelled $cancelledCount active downloads")
                } else {
                    Logger.log("WaveImportViewModel", LogLevel.INFO, "No active downloads to cancel")
                }

                // Clear the active download state
                _uiState.value = _uiState.value.copy(activeDownload = null)

                // Rescan files to update the UI with current state after cancellation
                try {
                    waveModule.rescanFiles()
                    Logger.log("WaveImportViewModel", LogLevel.INFO, "Rescanned files after download cancellation")

                    // Auto-select first available date/time/isobar if available wave files exist
                    // Same logic as successful import completion
                    val currentState = waveModule.currentState
                    if (currentState.entries.isNotEmpty()) {
                        val waveLogic = org.mountaincircles.app.modules.wave.logic.controllers.WaveLogic()
                        val initialSelection = waveLogic.findInitialSelection(currentState.entries)
                        if (initialSelection != null) {
                            waveModule.updateSelection(initialSelection)
                            Logger.log("WaveImportViewModel", LogLevel.INFO, "Auto-selected initial wave data after cancellation: ${initialSelection.targetDate} ${initialSelection.hour}h ${initialSelection.pressure}mb")
                        }
                    }
                } catch (e: Exception) {
                    Logger.log("WaveImportViewModel", LogLevel.WARN, "Failed to rescan after cancellation: ${e.message}")
                    // Don't treat rescan failure as a critical error
                }
            } catch (e: Exception) {
                handleError("Failed to cancel download: ${e.message}", e, "cancelDownload")
            }
        }
    }

    private fun handleClearAllFiles() {
        viewModelScope.launch {
            try {
                waveModule.clearAllFiles()
                updateForecastAvailability()
                Logger.log("WaveImportViewModel", LogLevel.INFO, "Cleared all files - refreshed availability")
            } catch (e: Exception) {
                handleError("Clear all files failed: ${e.message}", e, "clearAllFiles")
            }
        }
    }

    private fun handleRefreshAvailability() {
        viewModelScope.launch {
            try {
                updateForecastAvailability()
                val availableCount = calculator.getAvailableForecasts().size
                Logger.log("WaveImportViewModel", LogLevel.INFO,
                    "Refreshed forecast availability: $availableCount available")

            } catch (e: Exception) {
                handleError("Failed to refresh forecast availability: ${e.message}", e, "refreshAvailability")
            }
        }
    }

    private fun handleToggleIncludeWindFiles(include: Boolean) {
        _uiState.value = _uiState.value.copy(includeWindFiles = include)
        Logger.log("WaveImportViewModel", LogLevel.INFO, "Wind files inclusion toggled: $include")
    }

    private fun handleToggleWindRegion(region: WindRegion, selected: Boolean) {
        val currentRegions = _uiState.value.selectedWindRegions
        val newRegions = if (selected) {
            currentRegions + region
        } else {
            currentRegions - region
        }
        _uiState.value = _uiState.value.copy(selectedWindRegions = newRegions)
        Logger.log("WaveImportViewModel", LogLevel.INFO, "Wind region ${region} toggled: $selected")
    }

    override fun getTotalWaveFilesInMemory(): Int = waveModule.getTotalWaveFilesInMemory()

    override fun getTotalWindFilesInMemory(): Int = waveModule.getTotalWindFilesInMemory()

    override fun getTotalWaveFileSize(): Long = waveModule.getTotalWaveFileSize()

    override fun getTotalWindFileSize(): Long = waveModule.getTotalWindFileSize()
}
