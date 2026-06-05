package org.mountaincircles.app.ui

import kotlinx.coroutines.flow.StateFlow
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.state.NavigationState

/**
 * Interface for UI-specific state that ModuleUIContainer needs
 * This isolates UI management concerns from global application state
 */
interface UIState {
    val navigationState: NavigationState
    val modulesAvailableForUI: StateFlow<List<ModuleBase>>
}
