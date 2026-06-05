package org.mountaincircles.app.state

import org.mountaincircles.app.modules.ModuleState
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.utils.currentTimeMillis

/**
 * Base interface for all state actions
 */
interface StateAction {
    val actionType: String
    val moduleId: String
    val timestamp: Long
        get() = currentTimeMillis()
}

/**
 * Generic module actions that apply to all modules
 */
sealed class ModuleAction(override val moduleId: String) : StateAction {

    override val actionType: String = this::class.simpleName ?: "UnknownAction"

    /**
     * Initialize the module
     */
    data class Initialize(override val moduleId: String) : ModuleAction(moduleId)

    /**
     * Set the module to loading state
     */
    data class SetLoading(override val moduleId: String, val isLoading: Boolean = true) : ModuleAction(moduleId)

    /**
     * Set error state
     */
    data class SetError(override val moduleId: String, val message: String) : ModuleAction(moduleId)

    /**
     * Clear error state
     */
    data class ClearError(override val moduleId: String) : ModuleAction(moduleId)

    /**
     * Set module visibility
     */
    data class SetVisibility(override val moduleId: String, val visible: Boolean) : ModuleAction(moduleId)

    /**
     * Set module enabled state
     */
    data class SetEnabled(override val moduleId: String, val enabled: Boolean) : ModuleAction(moduleId)

    /**
     * Reset module to initial state
     */
    data class Reset(override val moduleId: String) : ModuleAction(moduleId)

    /**
     * Custom module action for module-specific operations
     */
    data class Custom(override val moduleId: String, val action: String, val data: Map<String, Any?> = emptyMap()) : ModuleAction(moduleId)
}

/**
 * Action creators for generating module actions
 */
object ModuleActionCreators {

    /**
     * Create initialize action
     */
    fun initialize(moduleId: String): ModuleAction.Initialize = ModuleAction.Initialize(moduleId)

    /**
     * Create loading action
     */
    fun setLoading(moduleId: String, isLoading: Boolean = true): ModuleAction.SetLoading = ModuleAction.SetLoading(moduleId, isLoading)

    /**
     * Create error action
     */
    fun setError(moduleId: String, message: String): ModuleAction.SetError = ModuleAction.SetError(moduleId, message)

    /**
     * Create clear error action
     */
    fun clearError(moduleId: String): ModuleAction.ClearError = ModuleAction.ClearError(moduleId)

    /**
     * Create visibility action
     */
    fun setVisibility(moduleId: String, visible: Boolean): ModuleAction.SetVisibility = ModuleAction.SetVisibility(moduleId, visible)

    /**
     * Create enabled action
     */
    fun setEnabled(moduleId: String, enabled: Boolean): ModuleAction.SetEnabled = ModuleAction.SetEnabled(moduleId, enabled)

    /**
     * Create reset action
     */
    fun reset(moduleId: String): ModuleAction.Reset = ModuleAction.Reset(moduleId)

    /**
     * Create custom action
     */
    fun custom(moduleId: String, action: String, data: Map<String, Any?> = emptyMap()): ModuleAction.Custom = ModuleAction.Custom(moduleId, action, data)
}

/**
 * Action dispatcher for sending actions to reducers
 */
class ActionDispatcher {
    private val listeners = mutableListOf<suspend (StateAction) -> Unit>()

    /**
     * Dispatch an action to all listeners
     */
    suspend fun dispatch(action: StateAction) {
        listeners.forEach { listener ->
            try {
                listener(action)
            } catch (e: Exception) {
                // Log error but don't crash
                Logger.error("STATE", "Error dispatching action ${action.actionType}", e)
            }
        }
    }

    /**
     * Add a listener for dispatched actions
     */
    fun addListener(listener: suspend (StateAction) -> Unit) {
        listeners.add(listener)
    }

    /**
     * Remove a listener
     */
    fun removeListener(listener: suspend (StateAction) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Clear all listeners
     */
    fun clear() {
        listeners.clear()
    }
}

/**
 * Global action dispatcher instance
 */
val globalActionDispatcher = ActionDispatcher()
