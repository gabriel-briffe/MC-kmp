package org.mountaincircles.app.modules.maps.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.mountaincircles.app.error.AppError
import org.mountaincircles.app.error.ErrorHandler
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.maps.MapsModule
import org.mountaincircles.app.ui.components.UnifiedProgress
import org.mountaincircles.app.utils.ScopeManager

/**
 * ViewModel implementation for Maps import section
 * Handles business logic and state management for maps import UI
 */
class MapsImportViewModelImpl(
    private val mapsModule: MapsModule
) : MapsImportViewModel {

    private val _uiState = MutableStateFlow(MapsImportUiState())
    override val uiState: StateFlow<MapsImportUiState> = _uiState

    override val progressFlow: StateFlow<UnifiedProgress> = mapsModule.progressFlow

    init {
        // Combine module state into UI state
        ScopeManager.uiScope.launch {
            combine(
                mapsModule.importDisplayFlow,
                mapsModule.mapsState
            ) { importData, mapsState ->
                MapsImportUiState(
                    availableMaps = importData.availableMaps,
                    installedMaps = importData.installedMaps.toSet(),
                    downloadingMaps = mapsState.activeDownloadMapId?.let { setOf(it) } ?: emptySet(),
                    isLoading = importData.isDownloading,
                    error = if (importData.hasError) {
                        MapsError.ModuleError(importData.errorMessage ?: "Unknown error", "import_display")
                    } else null
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    override fun handleAction(action: MapsImportUiAction) {
        when (action) {
            is MapsImportUiAction.DownloadMap -> handleDownloadMap(action.mapId)
            is MapsImportUiAction.DeleteMap -> handleDeleteMap(action.mapId)
            is MapsImportUiAction.CancelDownload -> handleCancelDownload(action.mapId)
            is MapsImportUiAction.RefreshAvailability -> handleRefreshAvailability()
            is MapsImportUiAction.ClearAllMaps -> handleClearAllMaps()
        }
    }

    override fun resetDownloadState() {
        mapsModule.resetDownloadState()
    }

    private fun handleDownloadMap(mapId: String) {
        ScopeManager.uiScope.launch {
            try {
                Logger.log("MAPS_VIEWMODEL", LogLevel.INFO, "Starting download for map: $mapId")
                mapsModule.downloadMapPipeline(mapId)
            } catch (e: Exception) {
                val appError = MapsError.DownloadError("Failed to start download", mapId, e).toAppError()
                ErrorHandler.handle(appError, "MapsImportViewModel.downloadMap($mapId)")
            }
        }
    }

    private fun handleDeleteMap(mapId: String) {
        ScopeManager.uiScope.launch {
            try {
                Logger.log("MAPS_VIEWMODEL", LogLevel.INFO, "Deleting map: $mapId")
                mapsModule.businessService.deleteMap(mapId)
            } catch (e: Exception) {
                val appError = MapsError.DeleteError("Failed to delete map", mapId, e).toAppError()
                ErrorHandler.handle(appError, "MapsImportViewModel.deleteMap($mapId)")
            }
        }
    }

    private fun handleCancelDownload(mapId: String) {
        try {
            Logger.log("MAPS_VIEWMODEL", LogLevel.INFO, "Cancelling download for map: $mapId")
            mapsModule.cancelDownload(mapId)
        } catch (e: Exception) {
            val appError = MapsError.ModuleError("Failed to cancel download", "cancel_download", e).toAppError()
            ErrorHandler.handle(appError, "MapsImportViewModel.cancelDownload($mapId)")
        }
    }

    private fun handleRefreshAvailability() {
        // For now, just log. Maps availability is static
        Logger.log("MAPS_VIEWMODEL", LogLevel.INFO, "Refresh availability requested")
    }

    private fun handleClearAllMaps() {
        ScopeManager.uiScope.launch {
            try {
                Logger.log("MAPS_VIEWMODEL", LogLevel.INFO, "Clearing all maps")
                mapsModule.clearAllMaps()
            } catch (e: Exception) {
                val appError = MapsError.ModuleError("Failed to clear all maps", "clear_all", e).toAppError()
                ErrorHandler.handle(appError, "MapsImportViewModel.clearAllMaps")
            }
        }
    }
}
