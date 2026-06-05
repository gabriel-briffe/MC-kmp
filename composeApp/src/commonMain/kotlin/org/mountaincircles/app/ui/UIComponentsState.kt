package org.mountaincircles.app.ui

import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.state.NavigationState

/**
 * Specialized interface for UI components that provides all properties they need
 * This allows UI components to be independent of GlobalState by composing
 * specific interfaces for different concerns
 */
interface UIComponentsState : NorthLockState, ModuleAccessState {
    val navigationState: NavigationState

    // TODO: Phase 4.3 - Remove this GlobalState exposure
    // UI components should use the interface methods above instead
    val globalState: GlobalState
}
