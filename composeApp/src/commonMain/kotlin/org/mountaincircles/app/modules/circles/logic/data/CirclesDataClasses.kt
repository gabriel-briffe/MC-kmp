package org.mountaincircles.app.modules.circles.logic.data

import org.mountaincircles.app.ui.components.UnifiedProgress

/**
 * Progress information for circles downloads
 */
data class DownloadProgress(
    val fileName: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: String,
    val percentComplete: Int = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else -1
)

/**
 * Extension function to convert DownloadProgress to UnifiedProgress
 * For single file downloads, set current=1, total=1 (1-based indexing for UI)
 */
fun DownloadProgress.toUnifiedProgress(): UnifiedProgress {
    return UnifiedProgress(
        status = this.status,
        isDownloading = true,
        current = 0,  // Current file (1-based)
        total = 1,    // Total files (single file download)
        percentComplete = this.percentComplete,
        fileName = this.fileName,
        bytesDownloaded = this.bytesDownloaded,
        totalBytes = this.totalBytes
    )
}

/**
 * Specialized data classes for selective reactive streams
 */
data class PolygonData(
    val sectorsOpacity: Float,
    val sectorsMinZoom: Float,
    val isAvailable: Boolean,
    val activeConfig: PackConfig?
)

data class LinesData(
    val circlesVisibility: Boolean,
    val circlesLineWidth: Float,
    val circlesMinZoom: Float,
    val isAvailable: Boolean,
    val activeConfig: PackConfig?
)

data class PointsData(
    val airfieldsVisibility: Boolean,
    val airfieldIconSize: Float,
    val airfieldIconsMinZoom: Float,
    val isAvailable: Boolean,
    val activeConfig: PackConfig?
)

data class LabelsData(
    val circlesVisibility: Boolean,
    val circleLabelsMinZoom: Float,
    val circlesLabelSize: Float,
    val circlesLabelSpacing: Float,
    val isAvailable: Boolean,
    val activeConfig: PackConfig?
)

data class ClickData(
    val circlesVisibility: Boolean,
    val airfieldsVisibility: Boolean,
    val airfieldIconsMinZoom: Float,
    val airfieldClickSize: Float,
    val circlesMinZoom: Float,
    val isAvailable: Boolean,
    val activeConfig: PackConfig?
)

data class AirfieldLabelsData(
    val airfieldsVisibility: Boolean,
    val airfieldLabelsMinZoom: Float,
    val airfieldLabelSize: Float,
    val labelOffset: Float,
    val isAvailable: Boolean,
    val activeConfig: PackConfig?
)

/**
 * Data class for packs sidebar display
 * Groups available configs and active config for optimal recomposition
 */
data class PacksDisplayData(
    val availableConfigs: List<PackConfig>,
    val activeConfig: PackConfig?
)
