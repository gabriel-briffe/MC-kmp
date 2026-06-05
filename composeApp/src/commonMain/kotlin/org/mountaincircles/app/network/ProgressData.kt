package org.mountaincircles.app.network

/**
 * Unified progress data structure for all download operations
 * Replaces inconsistent progress reporting across modules
 */
data class ProgressData(
    val downloaded: Long,
    val total: Long,
    val status: String = "",
    val percentage: Float = if (total > 0) (downloaded.toFloat() / total.toFloat()) * 100f else 0f
) {
    /**
     * Check if download is complete
     */
    val isComplete: Boolean
        get() = downloaded >= total && total > 0

    /**
     * Check if download has started (more than 0 bytes downloaded)
     */
    val hasStarted: Boolean
        get() = downloaded > 0

    /**
     * Get formatted progress string for logging/UI
     */
    fun getFormattedProgress(): String {
        return if (total > 0) {
            val percent = percentage
            "${downloaded}/${total} bytes (${percent.toInt()}%)"
        } else {
            "${downloaded} bytes"
        }
    }

    companion object {
        /**
         * Create progress data from downloaded/total bytes
         */
        fun fromBytes(downloaded: Long, total: Long, status: String = ""): ProgressData {
            return ProgressData(
                downloaded = downloaded,
                total = total,
                status = status
            )
        }

        /**
         * Create indeterminate progress (when total is unknown)
         */
        fun indeterminate(downloaded: Long, status: String): ProgressData {
            return ProgressData(
                downloaded = downloaded,
                total = -1L,
                status = status,
                percentage = 0f
            )
        }

        /**
         * Create completed progress
         */
        fun complete(total: Long, status: String = "Complete"): ProgressData {
            return ProgressData(
                downloaded = total,
                total = total,
                status = status,
                percentage = 100f
            )
        }
    }
}
