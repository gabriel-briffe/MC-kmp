package org.mountaincircles.app.modules

import kotlinx.coroutines.flow.StateFlow

/**
 * UI-relevant state that modules expose for reactive UI components
 *
 * This separates UI concerns from business logic, allowing UI components
 * to react directly to changes in module state without depending on
 * global state managers.
 */
data class ModuleUIState(
    /** Whether the module should be visible in the UI */
    val isVisible: Boolean = false,

    /** Whether the module has content to display (e.g., circles packs, wave data) */
    val hasContent: Boolean = false,

    /** Whether the module is currently loading/processing data */
    val isLoading: Boolean = false,

    /** Current error message, if any */
    val errorMessage: String? = null,

    /** Whether the module is fully ready for UI interaction */
    val isReady: Boolean = false
) {
    /** Convenience property for UI components to check if they should render */
    val shouldShow: Boolean get() = isVisible && isReady

    /** Convenience property for UI components to check if they should show content */
    val hasDisplayableContent: Boolean get() = hasContent && !isLoading && errorMessage == null && isReady

    companion object {
        /** Default state for uninitialized modules */
        val Empty = ModuleUIState()

        /** Ready state for fully initialized modules */
        fun Ready(
            isVisible: Boolean = true,
            hasContent: Boolean = false,
            isLoading: Boolean = false,
            errorMessage: String? = null
        ) = ModuleUIState(
            isVisible = isVisible,
            hasContent = hasContent,
            isLoading = isLoading,
            errorMessage = errorMessage,
            isReady = true
        )
    }
}
