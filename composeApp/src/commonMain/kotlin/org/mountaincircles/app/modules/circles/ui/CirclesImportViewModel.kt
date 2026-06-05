package org.mountaincircles.app.modules.circles.ui

import kotlinx.coroutines.flow.StateFlow
import org.mountaincircles.app.ui.components.UnifiedProgress

/**
 * ViewModel interface for Circles import section
 */
interface CirclesImportViewModel {
    val uiState: StateFlow<CirclesImportUiState>
    val progressFlow: StateFlow<UnifiedProgress>

    fun handleAction(action: CirclesImportUiAction)
    fun resetDownloadState() // For cleanup when composable is disposed
}
