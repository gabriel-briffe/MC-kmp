package org.mountaincircles.app.modules.wave.logic

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleMenuAction
import org.mountaincircles.app.state.getGlobalState
import org.mountaincircles.app.ui.AppIcons

/**
 * Wave module actions for main menu.
 * Extracted from WaveModule/WaveUIInterface for Phase E.
 */
fun getWaveModuleActions(moduleId: String, isInitialized: Boolean): List<ModuleMenuAction> {
    return listOf(
        ModuleMenuAction(
            id = "wave_import",
            title = "Import Wave Forecasts",
            description = "Offline Arome vertical velocity data",
            getIcon = { AppIcons.Wave() },
            action = {
                Logger.log("WAVE", LogLevel.INFO, "Wave action triggered - opening import sheet")
                val globalState = getGlobalState()
                globalState.navigationState.openImportSheet(moduleId)
            },
            isEnabled = isInitialized
        )
    )
}
