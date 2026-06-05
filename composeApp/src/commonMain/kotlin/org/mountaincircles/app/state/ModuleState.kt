package org.mountaincircles.app.state

import kotlinx.coroutines.flow.StateFlow

abstract class ModuleState {
    abstract val available: StateFlow<Boolean>
    abstract val visibility: StateFlow<Boolean>
    open val hasDataToRender: Boolean? = null  // Optional for backwards compatibility

    abstract fun setVisibility(visible: Boolean)
}
