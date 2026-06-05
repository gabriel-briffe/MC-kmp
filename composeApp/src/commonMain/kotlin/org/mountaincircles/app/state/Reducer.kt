package org.mountaincircles.app.state

/**
 * Reducer interface for centralized state mutation patterns
 *
 * This follows the Redux pattern where state changes are predictable and testable.
 * All state mutations should go through reducers to ensure consistency.
 */
interface Reducer<TState, TAction> {
    /**
     * Reduce the current state with the given action
     *
     * @param state The current state
     * @param action The action to apply
     * @return The new state after applying the action
     */
    fun reduce(state: TState, action: TAction): TState

    /**
     * Validate the state after reduction
     *
     * @param state The state to validate
     * @return true if state is valid, false otherwise
     */
    fun validate(state: TState): Boolean {
        return true // Default implementation - override for specific validation
    }

    /**
     * Get the reducer name for debugging and logging
     */
    val reducerName: String
        get() = this::class.simpleName ?: "UnknownReducer"
}

/**
 * Composable reducer that combines multiple reducers
 */
interface CompositeReducer<TState, TAction> : Reducer<TState, TAction> {
    val reducers: List<Reducer<TState, TAction>>

    override fun reduce(state: TState, action: TAction): TState {
        return reducers.fold(state) { currentState, reducer ->
            reducer.reduce(currentState, action)
        }
    }

    override fun validate(state: TState): Boolean {
        return reducers.all { it.validate(state) }
    }
}

/**
 * Reducer registry for managing multiple reducers
 */
class ReducerRegistry {
    private val reducers = mutableMapOf<String, Reducer<*, *>>()

    /**
     * Register a reducer with a unique key
     */
    fun <TState, TAction> register(key: String, reducer: Reducer<TState, TAction>) {
        reducers[key] = reducer
    }

    /**
     * Get a reducer by key
     */
    @Suppress("UNCHECKED_CAST")
    fun <TState, TAction> get(key: String): Reducer<TState, TAction>? {
        return reducers[key] as? Reducer<TState, TAction>
    }

    /**
     * Get all registered reducers
     */
    fun getAllReducers(): Map<String, Reducer<*, *>> = reducers.toMap()

    /**
     * Unregister a reducer
     */
    fun unregister(key: String) {
        reducers.remove(key)
    }

    /**
     * Clear all reducers
     */
    fun clear() {
        reducers.clear()
    }

    /**
     * Get reducer count
     */
    fun size(): Int = reducers.size
}

/**
 * Global reducer registry instance
 */
val globalReducerRegistry = ReducerRegistry()
