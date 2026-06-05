package org.mountaincircles.app.ui

import kotlinx.coroutines.flow.StateFlow
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.state.GlobalState

/**
 * Implementation of UIComponentsState that wraps GlobalState
 * Provides backward compatibility during the decoupling transition
 */
class UIComponentsStateImpl(
    private val _globalState: GlobalState
) : UIComponentsState {
    // NavigationState delegation
    override val navigationState: org.mountaincircles.app.state.NavigationState = _globalState.navigationState

    // NorthLockState implementation
    override val northLocked: StateFlow<Boolean> = _globalState.northLocked
    override suspend fun toggleNorthLock() = _globalState.toggleNorthLock()

    // ModuleAccessState implementation
    override val modulesAvailableForUI: StateFlow<List<ModuleBase>> = _globalState.moduleManager.modulesAvailableForUI
    override fun getAvailableModules(): List<ModuleBase> = _globalState.moduleManager.modulesAvailableForUI.value


    // Backward compatibility
    override val globalState: GlobalState = _globalState
}
