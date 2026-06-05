package org.mountaincircles.app.modules.circles.logic

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleMenuAction
import org.mountaincircles.app.ui.AppIcons

/**
 * Circles module actions for main menu.
 * Extracted from CirclesModule/CirclesUIInterface for Phase E.
 */
fun getCirclesModuleActions(
    moduleId: String,
    isInitialized: Boolean,
    isDownloading: Boolean,
    onOpenImportSheet: (String) -> Unit
): List<ModuleMenuAction> {
    return listOf(
        ModuleMenuAction(
            id = "import_circles_pack",
            title = "Import Circles Pack",
            description = "Offline glide ratio circles data",
            getIcon = { AppIcons.Target() },
            isEnabled = isInitialized,
            action = {
                Logger.log("CIRCLES", LogLevel.INFO, "Circles action triggered - opening import sheet")
                onOpenImportSheet(moduleId)
            }
        )
    )
}
