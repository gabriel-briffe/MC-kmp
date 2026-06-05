package org.mountaincircles.app.modules.circles.logic.data

import kotlinx.serialization.Serializable
import org.mountaincircles.app.modules.ModuleState

// PackMetadata is now in the same package

/**
 * Pack configuration info
 */
data class PackConfig(
    val packId: String,
    val configId: String,
    val folderName: String, // The actual folder name (policy_prefix)
    val metadata: PackMetadata? = null
)

/**
 * State for the circles module
 * Handles circles pack data and display settings
 */
data class CirclesState(
    val circlesVisibility: Boolean = false, // Controls circle lines and their labels visibility
    val sectorsOpacity: Float = 0.1f, // Controls sectors (polygon) opacity only - Default 10%
    val airfieldsVisibility: Boolean = true, // Controls airfield points and labels visibility
    val airfieldRadius: Float = 8.0f, // Airfield circle radius in pixels
    val labelOffset: Float = 16.0f, // Airfield label offset (2x radius)
    val circlesLabelSize: Float = 14.0f, // Circle line labels text size in sp
    val circlesLabelSpacing: Float = 240.0f, // Circle line labels spacing in dp
    val airfieldLabelSize: Float = 12.0f, // Airfield labels text size in sp
    val circlesLineWidth: Float = 2.0f, // Circle lines width in dp
    val airfieldIconSize: Float = 6.0f, // Airfield icon size in dp (same as airport icons)
    val circlesMinZoom: Float = 7.0f, // Min zoom for circle lines
    val circleLabelsMinZoom: Float = 9.0f, // Min zoom for circle labels
    val airfieldIconsMinZoom: Float = 18.0f, // Min zoom for airfield icons
    val airfieldLabelsMinZoom: Float = 18.0f, // Min zoom for airfield labels
    val sectorsMinZoom: Float = 1.0f, // Min zoom for sectors
    val airfieldClickSize: Float = 30.0f, // Airfield click area size in pixels (10-100)
    val installedPacks: List<String> = emptyList(), // Pack names that are cached/available offline
    val availableConfigs: List<PackConfig> = emptyList(), // All available pack configurations
    val activeConfig: PackConfig? = null, // Currently selected configuration
    val isDownloading: Boolean = false,
    val downloadProgress: DownloadProgress? = null, // Download progress information
    val activeDownloadPackId: String? = null, // ID of currently downloading pack
    val isDownloadActive: Boolean = false, // Whether any download is currently active
    override val isInitialized: Boolean = false,
    override val hasError: Boolean = false,
    override val errorMessage: String? = null,
    override val hasDataToRender: Boolean? = false
) : ModuleState()

/**
 * Circles module settings for persistence
 */
@Serializable
data class CirclesSettings(
    val circlesVisibility: Boolean = false, // Controls circle lines and their labels visibility
    val sectorsOpacity: Float = 0.1f, // Controls sectors (polygon) opacity only - Default 10%
    val airfieldsVisibility: Boolean = true, // Controls airfield points and labels visibility
    val airfieldRadius: Float = 8.0f, // Airfield circle radius in pixels
    val labelOffset: Float = 16.0f, // Airfield label offset (2x radius)
    val circlesLabelSize: Float = 14.0f, // Circle line labels text size in sp
    val circlesLabelSpacing: Float = 100.0f, // Circle line labels spacing in dp
    val airfieldLabelSize: Float = 12.0f, // Airfield labels text size in sp
    val circlesLineWidth: Float = 2.0f, // Circle lines width in dp
    val airfieldIconSize: Float = 6.0f, // Airfield icon size in dp (same as airport icons)
    val circlesMinZoom: Float = 7.0f, // Min zoom for circle lines
    val circleLabelsMinZoom: Float = 9.0f, // Min zoom for circle labels
    val airfieldIconsMinZoom: Float = 18.0f, // Min zoom for airfield icons
    val airfieldLabelsMinZoom: Float = 18.0f, // Min zoom for airfield labels
    val sectorsMinZoom: Float = 1.0f, // Min zoom for sectors
    val airfieldClickSize: Float = 30.0f, // Airfield click area size in pixels (10-100)
    val activePackId: String? = null,
    val activeConfigId: String? = null
)