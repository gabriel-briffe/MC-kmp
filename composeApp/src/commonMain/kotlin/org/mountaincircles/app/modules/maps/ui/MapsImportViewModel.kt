package org.mountaincircles.app.modules.maps.ui

import kotlinx.coroutines.flow.StateFlow
import org.mountaincircles.app.ui.components.UnifiedProgress

/**
 * ViewModel interface for Maps import section
 * Defines the UI state and actions for the Maps import screen
 */
interface MapsImportViewModel {
    val uiState: StateFlow<MapsImportUiState>
    val progressFlow: StateFlow<UnifiedProgress>
    fun handleAction(action: MapsImportUiAction)
    fun resetDownloadState()
}
