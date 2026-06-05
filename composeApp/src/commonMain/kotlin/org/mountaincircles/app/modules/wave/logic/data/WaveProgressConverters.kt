package org.mountaincircles.app.modules.wave.logic.data

import org.mountaincircles.app.ui.components.UnifiedProgress

/**
 * Conversion functions for Wave module progress data to UnifiedProgress
 */
object WaveProgressConverters {
    /**
     * Convert Wave ProgressData to UnifiedProgress
     */
    fun ProgressData.toUnifiedProgress(): UnifiedProgress {
        val waveProgress = this.currentProgress
        return UnifiedProgress(
            status = waveProgress?.status ?: "Idle",
            isDownloading = this.isDownloading,
            current = waveProgress?.current ?: 0,
            total = waveProgress?.total ?: 0,
            successCount = waveProgress?.successCount ?: 0,
            failedCount = waveProgress?.failedCount ?: 0,
            percentComplete = waveProgress?.percent ?: -1,
            fileName = waveProgress?.label ?: ""
        )
    }

    /**
     * Convert WaveProgress to UnifiedProgress
     */
    fun WaveProgress.toUnifiedProgress(): UnifiedProgress {
        return UnifiedProgress(
            status = this.status,
            isDownloading = true,
            current = this.current,
            total = this.total,
            successCount = this.successCount,
            failedCount = this.failedCount,
            percentComplete = this.percent,
            fileName = this.label
        )
    }
}

/**
 * Extension function to convert ProgressData to UnifiedProgress
 */
fun ProgressData.toUnifiedProgress(): UnifiedProgress {
    val waveProgress = this.currentProgress
    return UnifiedProgress(
        status = waveProgress?.status ?: "Idle",
        isDownloading = this.isDownloading,
        current = waveProgress?.current ?: 0,
        total = waveProgress?.total ?: 0,
        successCount = waveProgress?.successCount ?: 0,
        failedCount = waveProgress?.failedCount ?: 0,
        percentComplete = waveProgress?.percent ?: -1,
        fileName = waveProgress?.label ?: ""
    )
}

/**
 * Extension function to convert WaveProgress to UnifiedProgress
 */
fun WaveProgress.toUnifiedProgress(): UnifiedProgress {
    return UnifiedProgress(
        status = this.status,
        isDownloading = true,
        current = this.current,
        total = this.total,
        successCount = this.successCount,
        failedCount = this.failedCount,
        percentComplete = this.percent,
        fileName = this.label
    )
}
