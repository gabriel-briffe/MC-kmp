package org.mountaincircles.app.modules.circles.logic

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.ModuleUIState
import org.mountaincircles.app.modules.circles.logic.data.CirclesState
import org.mountaincircles.app.modules.circles.logic.data.DownloadProgress
import org.mountaincircles.app.modules.circles.logic.data.PackConfig
import org.mountaincircles.app.utils.ScopeManager

/**
 * State flows derived from CirclesState.
 * Extracted from CirclesModule for Phase E (clearer separation of concerns).
 * Uses ModuleBase.createSelectiveFlow for consistency (§5).
 */
class CirclesStateFlows(state: StateFlow<CirclesState>) {

    val circlesVisibility: StateFlow<Boolean> = ModuleBase.createSelectiveFlow(state, { it.circlesVisibility }, false)
    val sectorsOpacity: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.sectorsOpacity }, 0.1f)
    val airfieldsVisibility: StateFlow<Boolean> = ModuleBase.createSelectiveFlow(state, { it.airfieldsVisibility }, true)
    val airfieldRadius: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.airfieldRadius }, 8.0f)
    val labelOffset: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.labelOffset }, 16.0f)
    val circlesLabelSize: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.circlesLabelSize }, 14.0f)
    val circlesLabelSpacing: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.circlesLabelSpacing }, 240.0f)
    val airfieldLabelSize: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.airfieldLabelSize }, 12.0f)
    val circlesLineWidth: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.circlesLineWidth }, 2.0f)
    val airfieldIconSize: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.airfieldIconSize }, 6.0f)
    val circlesMinZoom: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.circlesMinZoom }, 7.0f)
    val circleLabelsMinZoom: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.circleLabelsMinZoom }, 9.0f)
    val airfieldIconsMinZoom: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.airfieldIconsMinZoom }, 18.0f)
    val airfieldLabelsMinZoom: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.airfieldLabelsMinZoom }, 18.0f)
    val sectorsMinZoom: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.sectorsMinZoom }, 1.0f)
    val airfieldClickSize: StateFlow<Float> = ModuleBase.createSelectiveFlow(state, { it.airfieldClickSize }, 30.0f)
    val installedPacks: StateFlow<List<String>> = ModuleBase.createSelectiveFlow(state, { it.installedPacks }, emptyList())
    val availableConfigs: StateFlow<List<PackConfig>> = ModuleBase.createSelectiveFlow(state, { it.availableConfigs }, emptyList())
    val activeConfig: StateFlow<PackConfig?> = ModuleBase.createSelectiveFlow(state, { it.activeConfig }, null)
    val isDownloading: StateFlow<Boolean> = ModuleBase.createSelectiveFlow(state, { it.isDownloading }, false)
    val downloadProgress: StateFlow<DownloadProgress?> = ModuleBase.createSelectiveFlow(state, { it.downloadProgress }, null)
    val activeDownloadPackId: StateFlow<String?> = ModuleBase.createSelectiveFlow(state, { it.activeDownloadPackId }, null)
    val isDownloadActive: StateFlow<Boolean> = ModuleBase.createSelectiveFlow(state, { it.isDownloadActive }, false)
    val isInitialized: StateFlow<Boolean> = ModuleBase.createSelectiveFlow(state, { it.isInitialized }, false)
    val hasError: StateFlow<Boolean> = ModuleBase.createSelectiveFlow(state, { it.hasError }, false)
    val errorMessage: StateFlow<String?> = ModuleBase.createSelectiveFlow(state, { it.errorMessage }, null)
    val hasDataToRender: StateFlow<Boolean> = ModuleBase.createSelectiveFlow(state, { it.activeConfig != null }, false)

    val moduleState: StateFlow<CirclesState> = state.map { circlesState ->
        val hasDataToRender = circlesState.activeConfig != null
        Logger.log("CIRCLES", LogLevel.DEBUG, "hasDataToRender update: activeConfig=${circlesState.activeConfig?.configId} -> hasDataToRender=$hasDataToRender")
        circlesState.copy(hasDataToRender = hasDataToRender)
    }.stateIn(ScopeManager.uiScope, SharingStarted.Eagerly, state.value.copy(hasDataToRender = state.value.activeConfig != null))

    val uiState: StateFlow<ModuleUIState> = ModuleBase.createUIStateFlow(
        state,
        hasContentPredicate = { (it as? CirclesState)?.installedPacks?.isNotEmpty() == true },
        isLoadingPredicate = { (it as? CirclesState)?.isDownloading ?: false }
    )
}
