package org.mountaincircles.app.modules.maps.logic.data

import org.mountaincircles.app.ui.components.UnifiedProgress

/**
 * Conversion functions for Maps module progress data to UnifiedProgress
 */
object MapsProgressConverters {
    /**
     * Convert Maps DownloadProgress to UnifiedProgress
     */
    fun DownloadProgress.toUnifiedProgress(): UnifiedProgress {
        return UnifiedProgress(
            status = this.status,
            isDownloading = true,
            current = this.current,
            total = this.total,
            percentComplete = this.percentComplete,
            fileName = this.mapName,
            bytesDownloaded = this.bytesDownloaded,
            totalBytes = this.totalBytes
        )
    }
}

/**
 * Extension function to convert DownloadProgress to UnifiedProgress
 */
fun DownloadProgress.toUnifiedProgress(): UnifiedProgress {
    return UnifiedProgress(
        status = this.status,
        isDownloading = true,
        current = this.current,
        total = this.total,
        percentComplete = this.percentComplete,
        fileName = this.mapName,
        bytesDownloaded = this.bytesDownloaded,
        totalBytes = this.totalBytes
    )
}
