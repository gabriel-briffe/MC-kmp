package org.mountaincircles.app.ui.modules

import androidx.compose.runtime.Composable
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.modules.wave.logic.ui.WaveSubmenuComposable
import org.mountaincircles.app.modules.wave.logic.controllers.WaveSubmenuProvider
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.modules.circles.submenu.ui.CirclesSubmenuComposable
import org.mountaincircles.app.modules.circles.submenu.logic.CirclesSubmenuProvider
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.ui.SkysightSubmenuComposable
import org.mountaincircles.app.modules.skysight.logic.controllers.SkysightSubmenuProvider

/**
 * Registry for module-specific submenu UI components
 *
 * This handles the rendering of submenus that appear below the top menu
 * when module buttons are pressed.
 *
 * To add a new module's submenu:
 * 1. Import the module and its submenu composable.
 * 2. Add a `when` clause in `RenderModuleSubmenu` to call the module's composable.
 * 3. Add a `when` clause in `hasSubmenuUI` to return true for the module.
 * Add new module submenus here instead of modifying core UI files
 */
object ModuleSubmenuRegistry {
    
    @Composable
    fun RenderModuleSubmenu(module: ModuleBase, globalState: org.mountaincircles.app.state.GlobalState) {
        when (module) {
            is WaveModule -> {
                WaveSubmenuComposable(module = module, globalState = globalState)
            }
            is CirclesModule -> {
                CirclesSubmenuComposable(module = module)
            }
            is SkysightModule -> {
                SkysightSubmenuComposable(module = module)
            }
            // Add new module submenus here:
            /*
            is AirspaceModule -> {
                AirspaceSubmenuComposable(module = module)
            }
            is ContourModule -> {
                ContourSubmenuComposable(module = module)
            }
            */
            else -> {
                // No submenu UI for this module
            }
        }
    }
    
    /**
     * Check if a module has a submenu UI available
     */
    fun hasSubmenuUI(module: ModuleBase): Boolean {
        return when (module) {
            is WaveModule -> WaveSubmenuProvider.hasSubmenuUI(module)
            is CirclesModule -> CirclesSubmenuProvider.hasSubmenuUI(module)
            is SkysightModule -> SkysightSubmenuProvider.hasSubmenuUI(module)
            // Add checks for other modules here
            /*
            is AirspaceModule -> true
            is ContourModule -> true
            */
            else -> false
        }
    }
}
