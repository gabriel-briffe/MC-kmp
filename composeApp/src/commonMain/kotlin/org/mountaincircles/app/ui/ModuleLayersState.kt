package org.mountaincircles.app.ui

import kotlinx.coroutines.flow.StateFlow
import org.mountaincircles.app.modules.ModuleBase

/**
 * Specialized interface for module layer state that MapContainer needs
 * This isolates layer management concerns from global application state
 */
interface ModuleLayersState {
    val modulesAvailableForUI: StateFlow<List<ModuleBase>>
}
