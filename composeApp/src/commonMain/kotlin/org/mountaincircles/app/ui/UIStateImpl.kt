package org.mountaincircles.app.ui

import kotlinx.coroutines.flow.StateFlow
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.state.NavigationState

/**
 * Implementation of UIState that wraps GlobalState
 * Provides backward compatibility during the decoupling transition
 */
class UIStateImpl(private val globalState: GlobalState) : UIState {
    override val navigationState: NavigationState = globalState.navigationState
    override val modulesAvailableForUI: StateFlow<List<ModuleBase>> = globalState.moduleManager.modulesAvailableForUI
}
