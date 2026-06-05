package org.mountaincircles.app.modules.maps.logic.data

import kotlinx.serialization.Serializable
import org.mountaincircles.app.ui.components.UnifiedProgress
import org.mountaincircles.app.modules.maps.logic.data.toUnifiedProgress

/**
 * Map source definition for downloadable hillshade maps
 */
@Serializable
data class MapSource(
    val id: String,
    val name: String,
    val url: String,
    val description: String = ""
)

/**
 * Available map sources
 *
 * Following the pattern from android-native, these are predefined
 * maps available for download from GitHub releases
 */
object MapSources {
    val availableMaps = listOf(
        MapSource(
            id = "alps",
            name = "Alps",
            url = "https://github.com/gabriel-briffe/MountainCircles---map/releases/download/alpes/alpes.mbtiles",
            description = "Hillshaded topographic map covering the Alpine region"
        ),
        MapSource(
            id = "pyrenees",
            name = "Pyrenees",
            url = "https://github.com/gabriel-briffe/MountainCircles---map/releases/download/pyrenees/pyrenees.mbtiles",
            description = "Hillshaded topographic map covering the Pyrenees"
        ),
        MapSource(
            id = "jura_nord_vosges",
            name = "Jura Nord Vosges",
            url = "https://github.com/gabriel-briffe/MountainCircles---map/releases/download/jura_nord_vosges/jura_nord_vosges.mbtiles",
            description = "Hillshaded topographic map covering Jura and Northern Vosges"
        ),
        MapSource(
            id = "norway",
            name = "Norway",
            url = "https://github.com/gabriel-briffe/MountainCircles---map/releases/download/norway/norway.mbtiles",
            description = "Hillshaded topographic map covering Norway"
        )
    )
}

/**
 * Data classes for selective reactive flows
 */
data class MapsImportDisplayData(
    val installedMaps: List<String>,
    val isDownloading: Boolean,
    val downloadProgress: UnifiedProgress?,
    val hasError: Boolean,
    val errorMessage: String?,
    val availableMaps: List<MapSource>
)

data class MapsLayerDisplayData(
    val hasDataToRender: Boolean,
    val installedMaps: List<String>
)

/**
 * Progress information for map downloads
 */
data class DownloadProgress(
    val mapId: String,
    val mapName: String,
    val current: Int,
    val total: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: String,
    val percentComplete: Int = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else -1
)
