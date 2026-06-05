package org.mountaincircles.app.modules.airspace.ui

import kotlinx.coroutines.flow.StateFlow
import org.mountaincircles.app.ui.components.UnifiedProgress

/**
 * ViewModel interface for Airspace import section.
 * Defines the UI state and actions for the Airspace import screen.
 */
interface AirspaceImportViewModel {
    val uiState: StateFlow<AirspaceImportUiState>
    val progressFlow: StateFlow<UnifiedProgress>
    fun handleAction(action: AirspaceImportUiAction)
    fun resetDownloadState()
}
