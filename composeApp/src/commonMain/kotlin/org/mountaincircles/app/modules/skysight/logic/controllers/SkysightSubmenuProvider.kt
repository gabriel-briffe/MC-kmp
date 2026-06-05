package org.mountaincircles.app.modules.skysight.logic.controllers

import org.mountaincircles.app.modules.skysight.SkysightModule

/**
 * Provider for Skysight module submenu logic
 */
object SkysightSubmenuProvider {

    /**
     * Check if Skysight module has submenu UI available
     * Always returns true since we want to show an empty submenu
     */
    fun hasSubmenuUI(module: SkysightModule): Boolean {
        // Always show submenu when logged in (hasDataToRender is true)
        return module.isLoggedIn.value
    }
}