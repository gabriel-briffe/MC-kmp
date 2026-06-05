package org.mountaincircles.app.modules.maps.logic.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.mountaincircles.app.modules.*
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.state.getGlobalState

/**
 * Maps Module UI Interface
 * Extracted UI-related methods for better separation of concerns
 */

/**
 * Get the icon for the maps module
 */
@Composable
fun getMapsIcon(): Painter {
    return AppIcons.Layers()
}


/**
 * Get the module actions for the maps module
 */
fun getMapsModuleActions(moduleId: String, isInitialized: Boolean): List<ModuleMenuAction> {
    Logger.log("MAPS", LogLevel.DEBUG, "MapsUIInterface: Creating action with isEnabled=$isInitialized")

    return listOf(
        ModuleMenuAction(
            id = "import_maps",
            title = "Import Hillshaded Maps",
            description = "Offline hillshaded maps",
            getIcon = { AppIcons.Layers() },
            action = {
                Logger.log("MAPS", LogLevel.INFO, "Maps action triggered - opening import sheet")
                val globalState = org.mountaincircles.app.state.getGlobalState()
                globalState.navigationState.openImportSheet(moduleId)
            },
            isEnabled = isInitialized  // Allow opening sheet even during downloads
        )
    )
}