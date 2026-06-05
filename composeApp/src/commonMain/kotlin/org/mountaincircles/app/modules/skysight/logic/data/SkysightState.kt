package org.mountaincircles.app.modules.skysight.logic.data

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.Serializable
import org.mountaincircles.app.modules.ModuleState
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import kotlinx.serialization.json.JsonObject
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Time pair for atomic hour/minute updates
 */
@Serializable
data class TimePair(
    val hour: Int,
    val minute: Int
) {
    val timestamp: Long
        get() = (hour * 3600 + minute * 60).toLong()

    val display: String
        get() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        val DEFAULT = TimePair(12, 0) // Default to noon
    }
}

/**
 * Login response from Skysight API
 */
@Serializable
data class SkysightLoginResponse(
    val key: String,
    val valid_until: Long,
    val allowed_regions: List<String>
)

/**
 * Color stop in layer legend
 */
@Serializable
data class LayerColorStop(
    val name: String,
    val value: String,
    val color: List<Int>
)

/**
 * Layer legend information
 */
@Serializable
data class LayerLegend(
    val color_mode: String,
    val units: String,
    val unit_type: String,
    val units_scale_factor: Int,
    val colors: List<LayerColorStop>
)

/**
 * Available layer information from Skysight API
 */
@Serializable
data class SkysightLayer(
    val name: String,
    val legend: LayerLegend,
    val projection: String,
    val data_type: String,
    val id: String,
    val description: String,
    val dataUrls: Map<String, List<SkysightDataUrl>> = emptyMap() // date -> list of data URLs with timestamps
)

@Serializable
data class SkysightDataUrl(
    val time: Long,  // Unix timestamp (seconds)
    val link: String,  // Direct download URL
    val layer_id: String  // Layer identifier
)

/**
 * Download status for tile downloads
 */
enum class DownloadStatusType {
    PENDING,    // Download is in progress
    FAILED      // Download failed
}

@Serializable
data class TileDownloadStatus(
    val status: DownloadStatusType
)

/**
 * Extension function to format layer values based on layer type
 */
fun SkysightLayer.formatValue(value: Float): String {
    return when {
        // Specific layers: divide by 100 and show as integer
        id == "hwcrit" || id == "dwcrit" || id == "zsfclclmask" -> (value / 100).toInt().toString()

        // Specific layer: blwind - multiply by 3.6 (m/s to km/h) and show 0 decimals
        id == "blwind" -> (value * 3.6).toInt().toString()

        // Specific layers: show 1 decimal
        id == "wblmaxmin" || id == "ridge" -> "%.2f".format(value)

        // Wind layers (w_*) - show 1 decimal
        id.startsWith("w_") -> "%.1f".format(value)

        // Specific layers: show 0 decimals
        id == "pfdtot" || id == "potfd" -> value.toInt().toString()

        // Specific layer: wstar_bsratio - always show 1 decimal
        id == "wstar_bsratio" -> "%.1f".format(value)

        // Temperature layers - show 1 decimal
        id.contains("temp", ignoreCase = true) -> "%.1f".format(value)

        // Pressure layers - show as integers
        id.contains("press", ignoreCase = true) -> value.toInt().toString()

        // Default formatting - show 1 decimal or integer if whole
        else -> {
            val rounded = (kotlin.math.round(value * 10) / 10)
            if (rounded == rounded.toInt().toFloat()) {
                rounded.toInt().toString()
            } else {
                rounded.toString()
            }
        }
    }
}

/**
 * Extension function to get unit string for layer popup display
 */
fun SkysightLayer.getUnitString(): String {
    return when {
        // Wind speed layers
        id == "wstar_bsratio" || id == "blwind" || id == "ridge" || id.startsWith("w_") -> "X m/s"

        // Altitude layers
        id == "hwcrit" || id == "dwcrit" || id == "zsfclclmask" -> "XX00 m"

        // Convergence layers (no unit)
        id == "wblmaxmin" -> ""

        // Precipitation layers
        id == "pfdtot" -> "X km"

        // Potential forecast layers
        id == "potfd" -> "X km/h"

        // Default (should not happen but fallback)
        else -> ""
    }
}

/**
 * Individual tile layer for geographic tiling system
 */
data class TileLayer(
    val tileId: String,  // "45N_10E"
    val bitmap: ImageBitmap,
    val bounds: List<Double>  // [west, south, east, north]
)

/**
 * Combined layer data for reactivity (similar to wave module's RasterData)
 */
data class SkysightLayerData(
    val isVisible: Boolean,
    val isLabelsVisible: Boolean,
    val hasData: Boolean,
    val selectedLayerId: String,
    val selectedDate: kotlinx.datetime.LocalDate?,
    val currentTime: TimePair
)

/**
 * State for the Skysight module
 */
data class SkysightState(
    override val isInitialized: Boolean = false,
    override val hasError: Boolean = false,
    override val errorMessage: String? = null,
    override val hasDataToRender: Boolean? = null,
    val email: String = "",
    val password: String = "",
    val isLoggedIn: Boolean = false,
    val allowedRegions: List<String> = emptyList(),
    val selectedRegion: String = "", // No default - user must choose
    val isLoggingIn: Boolean = false,
    val apiKey: String? = null, // Cached API key
    val apiKeyValidUntil: Long? = null, // API key expiration timestamp
    val availableLayers: List<SkysightLayer> = emptyList(), // Layers available for selected region
    val isLoadingLayers: Boolean = false,
    val selectedLayerId: String = "", // Selected forecast layer (only one at a time)
    val isVisible: Boolean = false, // Whether any Skysight layer is visible
    val lastSelectedLayer: String = "", // Last selected layer ("forecast:{id}", "satellite", "rain", "satellite+rain")
    val submenuMode: String = "forecast", // Submenu mode: "realtime", "forecast", or "wave"
    val isLabelsVisible: Boolean = true, // Whether Skysight label layer is visible
    val layerOpacity: Float = 0.75f, // Layer opacity (0.0-1.0, default 75%)
    val labelSize: Float = 12.0f, // Label text size in sp
    val forecastMinZoom: Float = 6.0f, // Minimum zoom level for forecast layer and labels
    val waveFilterMin: Float = -0.5f, // Wind filter minimum value (m/s)
    val waveFilterMax: Float = 0.5f, // Wind filter maximum value (m/s)
    val wblmaxminFilterMin: Float = -0.1f, // WBL max/min filter minimum value
    val wblmaxminFilterMax: Float = 0.1f, // WBL max/min filter maximum value
    val importTimeStart: Float = 0f, // Import time range start (hours 0-24)
    val importTimeEnd: Float = 24f, // Import time range end (hours 0-24)
    val layersToImport: Set<String> = emptySet(), // Set of layer IDs selected for import
    val downloadingLayers: Set<String> = emptySet(), // Set of layer IDs currently being downloaded
    val layerImportCounts: Map<String, Pair<Int, Int>> = emptyMap(), // Map of layer ID to (imported, total) file counts
    val selectedDate: kotlinx.datetime.LocalDate? = Clock.System.now().toLocalDateTime(TimeZone.UTC).date, // Selected date for forecast data (defaults to today in UTC)
    val realTimeTimestamp: kotlinx.datetime.Instant = Clock.System.now(), // Selected timestamp for realtime data (defaults to now)
    val currentTime: TimePair = TimePair.DEFAULT, // Selected time (hour:minute) - defaults to noon
    val viewportData: FeatureCollection<Point, JsonObject>? = null, // Calculated viewport data FeatureCollection for labels
    val activeTileLayers: MutableMap<String, TileLayer> = mutableMapOf(), // Currently active tile layers for geographic tiling
    val tileDownloadStatuses: MutableMap<String, TileDownloadStatus> = mutableMapOf(), // Download status tracking for tiles
    val tileDownloadList: MutableList<String> = mutableListOf(), // Queue of tile keys waiting to be downloaded
    val tilesToRender: MutableSet<String> = mutableSetOf(), // Tiles that should be rendered for current view
    val activeDownloadCount: Int = 0, // Number of currently active downloads (max 5)
    val isDownloading: Boolean = false, // Whether any download is in progress (for forecast/historical data)
    val cancelledBatchImport: Boolean = false, // Whether batch import was recently cancelled/stopped
    val batchImportCancelled: Boolean = false, // Whether the current batch import has been cancelled
    val satelliteEnabled: Boolean = false, // Whether satellite imagery layer is enabled
    val localRainEnabled: Boolean = false // Whether local rain layer is enabled
) : ModuleState()