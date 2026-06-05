package org.mountaincircles.app.ui

import kotlinx.coroutines.flow.StateFlow
import org.mountaincircles.app.modules.ModuleBase

/**
 * Interface for module access functionality that UI components need
 */
interface ModuleAccessState {
    val modulesAvailableForUI: StateFlow<List<ModuleBase>>

    fun getAvailableModules(): List<ModuleBase>
}
