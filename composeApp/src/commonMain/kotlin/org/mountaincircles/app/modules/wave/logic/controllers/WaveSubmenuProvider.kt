package org.mountaincircles.app.modules.wave.logic.controllers

import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.wave.WaveModule

/**
 * Provider for Wave module submenu logic
 */
object WaveSubmenuProvider {

    /**
     * Check if Wave module has submenu UI available
     */
    fun hasSubmenuUI(module: ModuleBase): Boolean {
        return module is WaveModule && run {
            // Only show submenu when wave data is available
            val state = module.combinedStateFlow.value
            state.isInitialized && state.entries.isNotEmpty()
        }
    }
}
