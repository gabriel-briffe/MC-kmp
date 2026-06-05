package org.mountaincircles.app.modules

/**
 * Interface for modules that need to perform cleanup when their popup is closed
 * by another module (e.g., when airport popup replaces airspace popup).
 *
 * This maintains modularity by keeping module-specific popup cleanup logic
 * within the modules themselves, while allowing generic components to trigger
 * the appropriate cleanup.
 */
interface PopupClosable {
    /**
     * Called when this module's popup is closed by generic popup management
     * (e.g., when another module opens a popup that replaces this one).
     *
     * Implementations should perform any module-specific cleanup such as:
     * - Hiding markers
     * - Updating module state
     * - Cleaning up resources
     */
    fun onPopupClosed()
}