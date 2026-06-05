package org.mountaincircles.app.modules.circles.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.utils.ErrorHandlerUtils
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.utils.ScopeManager

/**
 * ViewModel implementation for Circles import section
 */
class CirclesImportViewModelImpl(
    private val circlesModule: CirclesModule
) : CirclesImportViewModel {

    private val calculator = CirclesPackAvailabilityCalculator(circlesModule)

    // UI State flows
    private val _uiState = MutableStateFlow(CirclesImportUiState())

    override val uiState: StateFlow<CirclesImportUiState> = combine(
        circlesModule.circlesState,
        circlesModule.progressFlow,
        _uiState
    ) { state, progress, currentUiState ->
        buildUiState(state, progress, currentUiState)
    }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CirclesImportUiState()
    )

    /**
     * Build UI state from module state and progress
     * Preserves existing error state set by actions
     * Extracted for better readability and testability
     */
    private fun buildUiState(
        state: org.mountaincircles.app.modules.circles.logic.data.CirclesState,
        progress: org.mountaincircles.app.ui.components.UnifiedProgress,
        currentUiState: CirclesImportUiState
    ): CirclesImportUiState {
        return CirclesImportUiState(
            availablePacks = calculator.getAvailablePacks(),
            installedPacks = calculator.getInstalledPacks(),
            activePackId = calculator.getActivePackId(),
            isDownloading = state.isDownloading,
            downloadProgress = state.downloadProgress,
            error = currentUiState.error, // Preserve error state set by actions
            activeDownloadPackId = state.activeDownloadPackId
        )
    }

    override val progressFlow = circlesModule.progressFlow

    override fun handleAction(action: CirclesImportUiAction) {
        when (action) {
            is CirclesImportUiAction.DownloadPack -> handleDownloadPack(action.packId, action.configId)
            is CirclesImportUiAction.SelectPack -> handleSelectPack(action.packId, action.configId)
            is CirclesImportUiAction.DeletePack -> handleDeletePack(action.packId, action.configId)
            is CirclesImportUiAction.CancelDownload -> handleCancelDownload(action.fullPackId)
            is CirclesImportUiAction.RefreshAvailability -> handleRefreshAvailability()
            is CirclesImportUiAction.ImportFromZip -> handleImportFromZip()
            is CirclesImportUiAction.ClearAllPacks -> handleClearAllPacks()
        }
    }

    private fun handleDownloadPack(packId: String, configId: String) {
        ScopeManager.uiScope.launch {
            try {
                Logger.log("CIRCLES_VIEWMODEL", LogLevel.INFO, "Starting download of pack: $packId/$configId")
                circlesModule.downloadPackPipeline(packId, configId)
            } catch (e: Exception) {
                handleError("Download failed for $packId/$configId: ${e.message}", e, "downloadPack")
            }
        }
    }

    private fun handleSelectPack(packId: String, configId: String) {
        ScopeManager.uiScope.launch {
            try {
                Logger.log("CIRCLES_VIEWMODEL", LogLevel.INFO, "Selecting pack: $packId/$configId")
                circlesModule.selectPackConfig(packId, configId)
            } catch (e: Exception) {
                handleError("Selection failed for $packId/$configId: ${e.message}", e, "selectPack")
            }
        }
    }

    private fun handleDeletePack(packId: String, configId: String) {
        ScopeManager.uiScope.launch {
            try {
                Logger.log("CIRCLES_VIEWMODEL", LogLevel.INFO, "Deleting pack: $packId/$configId")
                // Use the download config ID format (with -4210 suffix) as expected by the module
                val downloadConfigId = "${configId}-4210"
                val success = circlesModule.deletePack(packId, downloadConfigId)
                Logger.log("CIRCLES_VIEWMODEL", LogLevel.INFO, "Pack deletion result: $success for $packId/$downloadConfigId")

                // The UI will automatically update through the reactive state flows
                // No manual state update needed since combine block reads from module state
            } catch (e: Exception) {
                handleError("Deletion failed for $packId/$configId: ${e.message}", e, "deletePack")
            }
        }
    }

    private fun handleCancelDownload(fullPackId: String) {
        ScopeManager.uiScope.launch {
            try {
                Logger.log("CIRCLES_VIEWMODEL", LogLevel.INFO, "Cancelling download for pack: $fullPackId")
                circlesModule.cancelPackDownload(fullPackId)
            } catch (e: Exception) {
                handleError("Cancel download failed: ${e.message}", e, "cancelDownload")
            }
        }
    }

    private fun handleRefreshAvailability() {
        ScopeManager.uiScope.launch {
            try {
                Logger.log("CIRCLES_VIEWMODEL", LogLevel.INFO, "Refreshing pack availability")
                // Update UI state with latest availability
                _uiState.value = _uiState.value.copy(
                    availablePacks = calculator.getAvailablePacks(),
                    installedPacks = calculator.getInstalledPacks(),
                    activePackId = calculator.getActivePackId()
                )
            } catch (e: Exception) {
                handleError("Refresh availability failed: ${e.message}", e, "refreshAvailability")
            }
        }
    }

    private fun handleImportFromZip() {
        ScopeManager.uiScope.launch {
            try {
                Logger.log("CIRCLES_VIEWMODEL", LogLevel.INFO, "Starting ZIP import")
                circlesModule.importFromZip()
            } catch (e: Exception) {
                handleError("ZIP import failed: ${e.message}", e, "importFromZip")
            }
        }
    }

    private fun handleClearAllPacks() {
        ScopeManager.uiScope.launch {
            try {
                Logger.log("CIRCLES_VIEWMODEL", LogLevel.INFO, "Clearing all packs")
                circlesModule.clearAllFiles()
            } catch (e: Exception) {
                handleError("Clear all packs failed: ${e.message}", e, "clearAllPacks")
            }
        }
    }

    /**
     * Handle errors consistently across all operations
     */
    private fun handleError(message: String, exception: Exception, operation: String) {
        val circlesError = ErrorHandlerUtils.handleCirclesError(message, exception, operation)
        _uiState.value = _uiState.value.copy(error = circlesError)
    }

    override fun resetDownloadState() {
        _uiState.value = _uiState.value.copy(
            error = null,
            activeDownloadPackId = null
        )
    }
}
