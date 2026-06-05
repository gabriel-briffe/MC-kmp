package org.mountaincircles.app.modules.skysight.logic.ui

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleMenuAction
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.ui.AppIcons

/**
 * Skysight module actions for top menu button and main menu.
 * Extracted from SkysightModule for clearer separation of concerns.
 */

fun getSkysightButtonAction(module: SkysightModule): (() -> Unit)? {
    return {
        val navigationState = org.mountaincircles.app.state.getGlobalState().navigationState
        if (navigationState.submenuVisible.value == "skysight") {
            Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Top menu button clicked with submenu open - closing skysight submenu")
            navigationState.closeSubmenu()
        } else {
            val lastSelected = module.state.value.lastSelectedLayer
            if (lastSelected.isEmpty()) {
                Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Top menu button clicked with no lastSelected - opening sidebar and submenu with skysight target")
                navigationState.openSidebar("skysight")
                navigationState.openSubmenu("skysight")
            } else {
                Logger.log("SKYSIGHT_MODULE", LogLevel.INFO, "Top menu button clicked with lastSelected '$lastSelected' - toggling submenu")
                navigationState.toggleSubmenu(module.moduleId)
            }
        }
    }
}

fun getSkysightModuleActions(module: SkysightModule): List<ModuleMenuAction> {
    return listOf(
        ModuleMenuAction(
            id = "skysight_action",
            title = "SkySight",
            description = "Login, batch import for the day",
            getIcon = { AppIcons.Skysight() },
            action = {
                Logger.log("SKYSIGHT", LogLevel.INFO, "Skysight action triggered from main menu - opening import sheet")
                org.mountaincircles.app.state.getGlobalState().navigationState.openImportSheet(module.moduleId)
            },
            isEnabled = module.currentState.isInitialized
        )
    )
}
