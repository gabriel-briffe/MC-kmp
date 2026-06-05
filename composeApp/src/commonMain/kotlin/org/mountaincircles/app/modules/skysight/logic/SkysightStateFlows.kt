package org.mountaincircles.app.modules.skysight.logic

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.mountaincircles.app.modules.skysight.logic.data.SkysightLayer
import org.mountaincircles.app.modules.skysight.logic.data.SkysightLayerData
import org.mountaincircles.app.modules.skysight.logic.data.SkysightState
import org.mountaincircles.app.modules.skysight.logic.data.TimePair
import org.mountaincircles.app.utils.ScopeManager
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import kotlinx.serialization.json.JsonObject

/**
 * Holds all state flows derived from SkysightState.
 * Uses Eagerly + uiScope so subscriptions stay active and the real-time tile
 * pipeline is not triggered multiple times for the same tile (avoids duplicate layer crash).
 */
class SkysightStateFlows(state: StateFlow<SkysightState>) {

    val email: StateFlow<String> = state.map { it.email }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = ""
    )
    val password: StateFlow<String> = state.map { it.password }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = ""
    )
    val isLoggedIn: StateFlow<Boolean> = state.map { it.isLoggedIn }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )
    val hasDataToRender: StateFlow<Boolean> = state.map { it.isLoggedIn }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )
    val isLoggingIn: StateFlow<Boolean> = state.map { it.isLoggingIn }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )
    val selectedRegion: StateFlow<String> = state.map { it.selectedRegion }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = ""
    )
    val availableLayers: StateFlow<List<SkysightLayer>> = state.map { it.availableLayers }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )
    val isLoadingLayers: StateFlow<Boolean> = state.map { it.isLoadingLayers }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )
    val selectedLayerId: StateFlow<String> = state.map { it.selectedLayerId }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = ""
    )
    val isVisible: StateFlow<Boolean> = state.map { it.isVisible }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )
    val isLabelsVisible: StateFlow<Boolean> = state.map { it.isLabelsVisible }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )
    val isDownloading: StateFlow<Boolean> = state.map { it.isDownloading || it.activeDownloadCount > 0 }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )
    val cancelledBatchImport: StateFlow<Boolean> = state.map { it.cancelledBatchImport }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )
    val satelliteEnabled: StateFlow<Boolean> = state.map { it.satelliteEnabled }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )
    val localRainEnabled: StateFlow<Boolean> = state.map { it.localRainEnabled }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )
    val layerOpacity: StateFlow<Float> = state.map { it.layerOpacity }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = 0.75f
    )
    val labelSize: StateFlow<Float> = state.map { it.labelSize }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = 12.0f
    )
    val forecastMinZoom: StateFlow<Float> = state.map { it.forecastMinZoom }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = 6.0f
    )
    val waveFilterMin: StateFlow<Float> = state.map { it.waveFilterMin }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = -0.5f
    )
    val waveFilterMax: StateFlow<Float> = state.map { it.waveFilterMax }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = 0.5f
    )
    val wblmaxminFilterMin: StateFlow<Float> = state.map { it.wblmaxminFilterMin }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = -0.1f
    )
    val wblmaxminFilterMax: StateFlow<Float> = state.map { it.wblmaxminFilterMax }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = 0.1f
    )
    val importTimeStart: StateFlow<Float> = state.map { it.importTimeStart }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = 14f
    )
    val importTimeEnd: StateFlow<Float> = state.map { it.importTimeEnd }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = 40f
    )
    val layersToImport: StateFlow<Set<String>> = state.map { it.layersToImport }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )
    val downloadingLayers: StateFlow<Set<String>> = state.map { it.downloadingLayers }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet()
    )
    val layerImportCounts: StateFlow<Map<String, Pair<Int, Int>>> = state.map { it.layerImportCounts }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )
    val selectedDate: StateFlow<kotlinx.datetime.LocalDate?> = state.map { it.selectedDate }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )
    val lastSelectedLayer: StateFlow<String> = state.map { it.lastSelectedLayer }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = ""
    )
    val submenuMode: StateFlow<String> = state.map { it.submenuMode }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = "forecast"
    )
    val realTimeTimestamp: StateFlow<kotlinx.datetime.Instant> = state.map { it.realTimeTimestamp }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = Clock.System.now()
    )
    val currentTime: StateFlow<TimePair> = state.map { it.currentTime }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = TimePair.DEFAULT
    )
    val viewportDataFlow: StateFlow<FeatureCollection<Point, JsonObject>?> = state.map { it.viewportData }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    val layerDataFlow: StateFlow<SkysightLayerData> = state.map { s ->
        SkysightLayerData(
            isVisible = s.isVisible,
            isLabelsVisible = s.isLabelsVisible,
            hasData = s.hasDataToRender == true,
            selectedLayerId = s.selectedLayerId,
            selectedDate = s.selectedDate,
            currentTime = s.currentTime
        )
    }.stateIn(
        scope = ScopeManager.uiScope,
        started = SharingStarted.Eagerly,
        initialValue = SkysightLayerData(
            isVisible = false,
            isLabelsVisible = true,
            hasData = false,
            selectedLayerId = "",
            selectedDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            currentTime = TimePair.DEFAULT
        )
    )
}
