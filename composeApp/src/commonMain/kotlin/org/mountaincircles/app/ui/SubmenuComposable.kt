package org.mountaincircles.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.ui.modules.ModuleSubmenuRegistry

/**
 * Submenu component that displays module-specific controls below the top menu
 * 
 * This component:
 * - Observes the global submenu state
 * - Finds the appropriate module based on the open submenu ID
 * - Renders the module's submenu UI using ModuleSubmenuRegistry
 */
@Composable
fun SubmenuComposable(
    globalState: GlobalState,
    modifier: Modifier = Modifier
) {
    val submenuVisible by globalState.navigationState.submenuVisible.collectAsState()
    val availableModules by globalState.moduleManager.modulesAvailableForUI.collectAsState()
    
    // Only render if a submenu is open
    submenuVisible?.let { moduleId ->
        // Find the module with the matching ID
        val targetModule = availableModules.find { it.moduleId == moduleId }
        
        if (targetModule != null) {
            // Generic reactive logic for all modules: close submenu when no data to render
            val moduleState by targetModule.moduleState.collectAsState()

            // ✅ Reactive computation: should submenu be closed?
            val shouldCloseSubmenu by remember {
                derivedStateOf {
                    val hasDataToRender = moduleState.hasDataToRender ?: false
                    !hasDataToRender // Close if no data to render
                }
            }

            // Check if module has submenu UI
            val hasSubmenuUI = ModuleSubmenuRegistry.hasSubmenuUI(targetModule)

            if (hasSubmenuUI && !shouldCloseSubmenu) {
                Logger.log("UI", LogLevel.DEBUG, "Rendering submenu for module: $moduleId")

                Column(modifier = modifier) {
                    ModuleSubmenuRegistry.RenderModuleSubmenu(targetModule, globalState)
                }
            } else if (hasSubmenuUI && shouldCloseSubmenu) {
                // ✅ Reactive effect: close submenu when data disappears
                LaunchedEffect(shouldCloseSubmenu) {
                    if (shouldCloseSubmenu) {
                        Logger.log("UI", LogLevel.INFO, "Module $moduleId no longer has data to render, closing submenu")
                        globalState.navigationState.closeSubmenu()
                    }
                }
            }
        } else {
            Logger.log("UI", LogLevel.WARN, "No module found for submenu: $moduleId")
        }
    }
}
