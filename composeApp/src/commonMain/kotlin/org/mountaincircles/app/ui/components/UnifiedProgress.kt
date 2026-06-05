package org.mountaincircles.app.ui.components

import org.mountaincircles.app.utils.formatFileSize

/**
 * Unified progress data structure for all import operations across all modules
 *
 * This consolidates the different progress structures used by:
 * - Wave module (WaveProgress)
 * - Circles module (DownloadProgress)
 * - Maps module (DownloadProgress)
 * - Airspace module (AirspaceProgress)
 */
data class UnifiedProgress(
    // Basic fields (all implementations)
    val status: String,
    val isDownloading: Boolean,

    // File-level progress (Wave, Airspace, Maps)
    val current: Int = 0,
    val total: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0,

    // Byte-level progress (Circles, Maps)
    val percentComplete: Int = -1,
    val fileName: String = "",
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0
) {
    /**
     * Computed property for progress percentage based on available data
     */
    val computedPercentComplete: Int
        get() = when {
            // Prioritize byte-level progress when available (for current file progress)
            totalBytes > 0 -> ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
            // Use explicit percentComplete if provided (fallback)
            percentComplete >= 0 -> percentComplete
            // Calculate from file counts if available
            total > 0 -> ((current * 100) / total).toInt().coerceIn(0, 100)
            // Indeterminate
            else -> -1
        }

    /**
     * Whether this progress has byte-level information available
     */
    val hasByteProgress: Boolean
        get() = totalBytes > 0

    /**
     * Whether this progress has file-level information available
     */
    val hasFileProgress: Boolean
        get() = total > 0

    /**
     * Whether this progress has detailed success/failed counts
     */
    val hasDetailedCounts: Boolean
        get() = successCount > 0 || failedCount > 0

    /**
     * Format bytes for display
     */
    fun formatBytes(): String {
        if (!hasByteProgress) return ""

        val downloaded = if (bytesDownloaded < 1024) "${bytesDownloaded} B" else formatFileSize(bytesDownloaded)
        val total = if (totalBytes < 1024) "${totalBytes} B" else formatFileSize(totalBytes)
        return "$downloaded / $total"
    }

    /**
     * Get display info based on available data
     */
    fun getDisplayInfo(): String {
        return when {
            fileName.isNotEmpty() -> fileName
            hasFileProgress -> "File $current of $total"
            else -> "Downloading..."
        }
    }

    companion object {
        /**
         * Create idle/no-progress state
         */
        fun idle(): UnifiedProgress {
            return UnifiedProgress(
                status = "Idle",
                isDownloading = false
            )
        }

        /**
         * Create indeterminate progress state
         */
        fun indeterminate(status: String): UnifiedProgress {
            return UnifiedProgress(
                status = status,
                isDownloading = true
            )
        }
    }
}

// File size formatting is now handled by cross-platform utility
