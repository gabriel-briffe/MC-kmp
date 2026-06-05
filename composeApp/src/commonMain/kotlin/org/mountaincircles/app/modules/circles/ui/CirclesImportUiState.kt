package org.mountaincircles.app.modules.circles.ui

import org.mountaincircles.app.modules.circles.logic.data.PackConfig
import org.mountaincircles.app.modules.circles.logic.data.DownloadProgress

/**
 * UI state for Circles import section
 */
data class CirclesImportUiState(
    val availablePacks: List<String> = emptyList(),
    val installedPacks: List<String> = emptyList(),
    val activePackId: String? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: DownloadProgress? = null,
    val error: CirclesError? = null,
    val activeDownloadPackId: String? = null
) {
    /**
     * Get the total number of available packs (derived property)
     */
    val entriesCount: Int get() = availablePacks.size
}
