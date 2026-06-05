package org.mountaincircles.app.modules.airports.logic.data

import org.mountaincircles.app.ui.components.UnifiedProgress

/**
 * Extension function to convert AirportsProgress to UnifiedProgress
ss * Simple file-level progress (1/2, 2/2) without byte tracking
 */
fun AirportsProgress.toUnifiedProgress(): UnifiedProgress {
    return UnifiedProgress(
        status = this.status,
        isDownloading = true,
        current = this.current,
        total = this.total,
        percentComplete = this.percent,
        fileName = "${this.current}/${this.total} files"
    )
}