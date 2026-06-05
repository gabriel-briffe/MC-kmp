package org.mountaincircles.app.ui

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for north lock functionality that UI components need
 */
interface NorthLockState {
    val northLocked: StateFlow<Boolean>

    suspend fun toggleNorthLock()
}
