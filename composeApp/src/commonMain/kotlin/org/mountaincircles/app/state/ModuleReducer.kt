package org.mountaincircles.app.state

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleState

/**
 * Generic module reducer that handles common module actions
 */
abstract class ModuleReducer<TState : ModuleState> : Reducer<TState, ModuleAction> {

    override val reducerName: String = "ModuleReducer"

    override fun reduce(state: TState, action: ModuleAction): TState {
        // Only process actions for this module
        if (action.moduleId != getModuleId()) {
            return state
        }

        Logger.log("MODULE_REDUCER", LogLevel.DEBUG,
            "Reducing action ${action.actionType} for module ${action.moduleId}")

        return when (action) {
            is ModuleAction.SetLoading -> reduceSetLoading(state, action)
            is ModuleAction.SetVisibility -> reduceSetVisibility(state, action)
            is ModuleAction.SetEnabled -> reduceSetEnabled(state, action)
            is ModuleAction.Reset -> reduceReset(state, action)
            is ModuleAction.Custom -> reduceCustom(state, action)
            else -> {
                Logger.log("MODULE_REDUCER", LogLevel.WARN,
                    "Unhandled action type: ${action.actionType} for module ${action.moduleId}")
                state
            }
        }
    }

    /**
     * Get the module ID this reducer handles
     */
    abstract fun getModuleId(): String

    /**
     * Create initial state for the module
     */
    abstract fun createInitialState(): TState

    /**
     * Handle loading state action
     */
    protected open fun reduceSetLoading(state: TState, action: ModuleAction.SetLoading): TState {
        // Default implementation - subclasses should override to handle loading state
        Logger.log("MODULE_REDUCER", LogLevel.WARN,
            "Module ${getModuleId()} does not support loading state changes")
        return state
    }

    /**
     * Handle visibility action
     */
    protected open fun reduceSetVisibility(state: TState, action: ModuleAction.SetVisibility): TState {
        // Default implementation - override if module state supports visibility
        Logger.log("MODULE_REDUCER", LogLevel.WARN,
            "Module ${action.moduleId} does not support visibility changes")
        return state
    }

    /**
     * Handle enabled action
     */
    protected open fun reduceSetEnabled(state: TState, action: ModuleAction.SetEnabled): TState {
        // Default implementation - override if module state supports enabled/disabled
        Logger.log("MODULE_REDUCER", LogLevel.WARN,
            "Module ${action.moduleId} does not support enabled/disabled changes")
        return state
    }

    /**
     * Handle reset action
     */
    protected open fun reduceReset(state: TState, action: ModuleAction.Reset): TState {
        return createInitialState()
    }

    /**
     * Handle custom actions
     */
    protected open fun reduceCustom(state: TState, action: ModuleAction.Custom): TState {
        Logger.log("MODULE_REDUCER", LogLevel.INFO,
            "Unhandled custom action '${action.action}' for module ${action.moduleId}")
        return state
    }

    /**
     * Validate state after reduction
     */
    override fun validate(state: TState): Boolean {
        return try {
            // Basic validation
            val isValid = !state.hasError || state.errorMessage != null
            if (!isValid) {
                Logger.log("MODULE_REDUCER", LogLevel.WARN,
                    "Invalid state for module ${getModuleId()}: hasError=${state.hasError}, errorMessage=${state.errorMessage}")
            }
            isValid
        } catch (e: Exception) {
            Logger.log("MODULE_REDUCER", LogLevel.ERROR,
                "State validation error for module ${getModuleId()}: ${e.message}")
            false
        }
    }
}

/**
 * Factory for creating module reducers
 */
object ModuleReducerFactory {

    /**
     * Create a reducer for a specific module
     * Note: This creates a basic reducer. For specific modules, create dedicated reducers.
     */
    fun createBasicModuleReducer(moduleId: String): ModuleReducer<ModuleState> {
        return object : ModuleReducer<ModuleState>() {
            override fun getModuleId(): String = moduleId

            override fun createInitialState(): ModuleState {
                // Create a concrete implementation of ModuleState
                return object : ModuleState() {
                    override val isInitialized: Boolean = false
                    override val hasError: Boolean = false
                    override val errorMessage: String? = null
                }
            }
        }
    }

    /**
     * Register all standard module reducers
     */
    fun registerStandardReducers(registry: ReducerRegistry) {
        val standardModules = listOf("circles", "wave", "geolocation")

        standardModules.forEach { moduleId ->
            val reducer = createBasicModuleReducer(moduleId)
            registry.register("module_$moduleId", reducer)
            Logger.log("MODULE_REDUCER", LogLevel.INFO, "Registered basic reducer for module: $moduleId")
        }
    }
}

/**
 * Action dispatcher with reducer integration
 */
class ReducerActionDispatcher(private val reducerRegistry: ReducerRegistry) {

    /**
     * Dispatch an action and apply it to all relevant reducers
     */
    suspend fun dispatch(action: ModuleAction) {
        Logger.log("ACTION_DISPATCHER", LogLevel.DEBUG,
            "Dispatching action ${action.actionType} for module ${action.moduleId}")

        // Find the reducer for this module
        val reducerKey = "module_${action.moduleId}"
        val reducer = reducerRegistry.get<ModuleState, ModuleAction>(reducerKey)

        if (reducer != null) {
            Logger.log("ACTION_DISPATCHER", LogLevel.DEBUG,
                "Found reducer for action: $reducerKey")
            // Note: In a real implementation, we would need access to the actual module state
            // This is a simplified example showing the pattern
        } else {
            Logger.log("ACTION_DISPATCHER", LogLevel.WARN,
                "No reducer found for module: ${action.moduleId}")
        }

        // Also dispatch to global dispatcher for other listeners
        globalActionDispatcher.dispatch(action)
    }
}

/**
 * Global reducer action dispatcher instance
 */
val globalReducerDispatcher = ReducerActionDispatcher(globalReducerRegistry)
