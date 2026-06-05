package org.mountaincircles.app.modules.wave.logic.data

import kotlinx.serialization.Serializable
import org.mountaincircles.app.modules.ModuleState
import org.mountaincircles.app.modules.wave.import.logic.WaveImportType
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import kotlinx.serialization.json.JsonObject


/**
 * Wave forecast entry representing a downloaded wave data file
 */
data class WaveEntry(
    val forecastDate: String,        // yyyy-MM-dd
    val targetDate: String,          // yyyy-MM-dd
    val hour: Int,                   // 5-19
    val pressure: Int,               // 500,600,700,800,900 hPa
    val filePath: String             // Full path to wave data file
)

/**
 * Wave selection state (persisted)
 */
@Serializable
data class WaveSelection(
    val forecastDate: String,
    val targetDate: String,
    val hour: Int,
    val pressure: Int,
    val filePath: String = ""
) {
    fun isValid(): Boolean {
        return forecastDate.isNotBlank() &&
               targetDate.isNotBlank() &&
               hour in 5..19 &&
               pressure in listOf(500, 600, 700, 800, 900, 1000)
    }

    // ✅ OPTIMIZED: Custom equals for distinctUntilChanged performance
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WaveSelection) return false

        // Optimize: compare most frequently changing fields first
        if (filePath != other.filePath) return false
        if (hour != other.hour) return false
        if (pressure != other.pressure) return false
        if (forecastDate != other.forecastDate) return false
        if (targetDate != other.targetDate) return false

        return true
    }

    // ✅ OPTIMIZED: Cached hashCode for better performance
    private var _hashCode: Int? = null
    override fun hashCode(): Int {
        if (_hashCode == null) {
            _hashCode = filePath.hashCode() * 31 +
                       hour.hashCode() * 29 +
                       pressure.hashCode() * 23 +
                       forecastDate.hashCode() * 19 +
                       targetDate.hashCode() * 17
        }
        return _hashCode!!
    }
}

/**
 * Wave module state
 */
data class WaveState(
    override val isInitialized: Boolean = false,
    override val hasError: Boolean = false,
    override val errorMessage: String? = null,
    override val hasDataToRender: Boolean? = false,
    val entries: List<WaveEntry> = emptyList(),
    val selection: WaveSelection = WaveSelection("", "", 12, 500),
    val isVisible: Boolean = false,
    val canPrevHour: Boolean = false,
    val canNextHour: Boolean = false,
    val canPressureUp: Boolean = false,
    val canPressureDown: Boolean = false,
    val isDownloading: Boolean = false,
    val opacity: Float = 0.75f,
    val currentProgress: WaveProgress? = null,
    val mainLabelFontSize: Float = 13.0f,  // Main labels: time, FC, altitude (in sp)
    val subLabelFontSize: Float = 10.0f,   // Sub labels: date, NOW, pressure (in sp)
    val windBarbSize: Float = 0.5f,        // Wind barb base size multiplier
    val windSpeedScaleDistortion: Float = 0.3f, // Wind speed scaling distortion factor
    val barbInterval: Float = 10f,         // Wind barb spacing interval in mm
    val showZeroWindBarbs: Boolean = false, // Whether to show wind barbs for zero wind speed
    // NEW: Download UI state management
    val activeDownloadType: WaveImportType? = null,  // Which import is currently active
    val isDownloadActive: Boolean = false,           // Whether any download is active
    // Consolidated state fields (previously separate flows)
    val importProgress: ImportProgressState = ImportProgressState.Idle,
    val cacheClearRequested: Boolean = false,
    val windVectorsFeatureCollection: FeatureCollection<Point, JsonObject>? = null, // Calculated wind vector FeatureCollection
    val windLayerVisible: Boolean = false   // Wind vectors layer visibility
) : ModuleState()

/**
 * Import progress states for reactive flow
 */
sealed class ImportProgressState {
    object Idle : ImportProgressState()
    object Starting : ImportProgressState()
    data class Downloading(val progress: WaveProgress) : ImportProgressState()
    object Scanning : ImportProgressState()
    data class Completed(val entries: List<WaveEntry>, val selection: WaveSelection) : ImportProgressState()
    data class Error(val message: String) : ImportProgressState()
}

/**
 * Wave import progress
 */
data class WaveProgress(
    val current: Int,
    val total: Int,
    val status: String,
    val percent: Int = -1,
    val label: String = "",
    val successCount: Int = 0,
    val failedCount: Int = 0
)
