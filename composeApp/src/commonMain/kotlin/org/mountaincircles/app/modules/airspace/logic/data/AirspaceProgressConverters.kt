package org.mountaincircles.app.modules.airspace.logic.data

import org.mountaincircles.app.ui.components.UnifiedProgress

/**
 * Extension function to convert AirspaceProgress to UnifiedProgress
 * For Airspace: Multi-file download (current/total files with success/fail counts)
 * Supports both file-level and byte-level progress for segmented progress bar
 */
fun AirspaceProgress.toUnifiedProgress(): UnifiedProgress {
    return UnifiedProgress(
        status = this.status,
        isDownloading = true,
        current = this.current,
        total = this.total,
        percentComplete = this.percent,
        fileName = "" // Not used in simplified version
    )
}
