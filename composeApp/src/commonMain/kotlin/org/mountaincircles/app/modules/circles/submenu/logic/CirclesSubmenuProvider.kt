package org.mountaincircles.app.modules.circles.submenu.logic

import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.circles.CirclesModule

/**
 * Provider for Circles module submenu logic
 */
object CirclesSubmenuProvider {

    /**
     * Check if Circles module has submenu UI available
     */
    fun hasSubmenuUI(module: ModuleBase): Boolean {
        return module is CirclesModule && run {
            // Only show submenu when circles data is available
            val state = module.circlesState.value
            state.installedPacks.isNotEmpty()
        }
    }
}
