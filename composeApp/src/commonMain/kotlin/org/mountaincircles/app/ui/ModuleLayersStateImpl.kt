package org.mountaincircles.app.ui

import kotlinx.coroutines.flow.StateFlow
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.state.GlobalState

/**
 * Implementation of ModuleLayersState that wraps GlobalState
 * Provides backward compatibility during the decoupling transition
 */
class ModuleLayersStateImpl(private val globalState: GlobalState) : ModuleLayersState {
    override val modulesAvailableForUI: StateFlow<List<ModuleBase>> = globalState.moduleManager.modulesAvailableForUI
}
