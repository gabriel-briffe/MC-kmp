package org.mountaincircles.app.modules.wave.logic

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import kotlinx.serialization.json.JsonObject
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.ModuleUIState
import org.mountaincircles.app.modules.wave.logic.data.FontSettingsData
import org.mountaincircles.app.modules.wave.logic.data.NavigationData
import org.mountaincircles.app.modules.wave.logic.data.ProgressData
import org.mountaincircles.app.modules.wave.logic.data.RasterData
import org.mountaincircles.app.modules.wave.logic.data.TimeAndPressureDisplayData
import org.mountaincircles.app.ui.components.UnifiedProgress
import org.mountaincircles.app.modules.wave.logic.data.WaveSelection
import org.mountaincircles.app.modules.wave.logic.data.WaveState
import org.mountaincircles.app.modules.wave.logic.data.toUnifiedProgress
import org.mountaincircles.app.utils.ScopeManager

/**
 * State flows derived from WaveState.
 * Extracted from WaveModule for Phase E (clearer separation of concerns).
 */
class WaveStateFlows(state: StateFlow<WaveState>) {

    val moduleState: StateFlow<WaveState> = state.map { waveState ->
        waveState.copy(hasDataToRender = waveState.entries.isNotEmpty())
    }.stateIn(ScopeManager.uiScope, SharingStarted.Eagerly, state.value.copy(hasDataToRender = state.value.entries.isNotEmpty()))

    val uiState: StateFlow<ModuleUIState> = ModuleBase.createUIStateFlow(
        state,
        hasContentPredicate = { (it as? WaveState)?.entries?.isNotEmpty() == true },
        isLoadingPredicate = { (it as? WaveState)?.isDownloading ?: false },
        showReadyWhen = { it.isInitialized && (it as? WaveState)?.entries?.isNotEmpty() == true }
    )

    val navigationFlow: StateFlow<NavigationData> = ModuleBase.createSelectiveFlow(
        state,
        { s -> NavigationData(s.canPrevHour, s.canNextHour, s.canPressureUp, s.canPressureDown) },
        NavigationData(false, false, false, false)
    )

    val progressFlow: StateFlow<UnifiedProgress> = ModuleBase.createSelectiveFlow(
        state,
        { s -> ProgressData(s.isDownloading, s.currentProgress).toUnifiedProgress() },
        UnifiedProgress.idle()
    )

    val entriesFlow: StateFlow<Int> = ModuleBase.createSelectiveFlow(
        state,
        { it.entries.size },
        0
    )

    val timeAndPressureDisplayFlow: StateFlow<TimeAndPressureDisplayData> = ModuleBase.createSelectiveFlow(
        state,
        { s -> TimeAndPressureDisplayData(
            hour = s.selection.hour,
            targetDate = s.selection.targetDate,
            forecastDate = s.selection.forecastDate,
            pressure = s.selection.pressure
        ) },
        TimeAndPressureDisplayData(12, "", "", 500)
    )

    val fontSettingsFlow: StateFlow<FontSettingsData> = ModuleBase.createSelectiveFlow(
        state,
        { s -> FontSettingsData(s.mainLabelFontSize, s.subLabelFontSize) },
        FontSettingsData(13.0f, 10.0f)
    )

    val layerDataFlow: StateFlow<RasterData> = ModuleBase.createSelectiveFlow(
        state,
        { s -> RasterData(s.isVisible, s.opacity, s.selection, s.entries.size, s.entries.isNotEmpty()) },
        RasterData(false, 0.75f, WaveSelection("", "", 12, 500), 0, false)
    )

    val windLayerVisibilityFlow: StateFlow<Boolean> = ModuleBase.createSelectiveFlow(
        state,
        { it.windLayerVisible },
        false
    )

    val windVectorsFeatureCollectionFlow: StateFlow<FeatureCollection<Point, JsonObject>?> = ModuleBase.createSelectiveFlow(
        state,
        { it.windVectorsFeatureCollection },
        null
    )

    val windBarbSizeFlow: StateFlow<Float> = ModuleBase.createSelectiveFlow(
        state,
        { it.windBarbSize },
        0.5f
    )

    val windSpeedScaleDistortionFlow: StateFlow<Float> = ModuleBase.createSelectiveFlow(
        state,
        { it.windSpeedScaleDistortion },
        0.3f
    )

    val barbIntervalFlow: StateFlow<Float> = ModuleBase.createSelectiveFlow(
        state,
        { it.barbInterval },
        10f
    )

    val showZeroWindBarbsFlow: StateFlow<Boolean> = ModuleBase.createSelectiveFlow(
        state,
        { it.showZeroWindBarbs },
        false
    )

    val combinedStateFlow: StateFlow<WaveState> = state
}
