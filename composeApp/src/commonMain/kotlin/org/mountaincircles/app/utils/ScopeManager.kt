package org.mountaincircles.app.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Centralized coroutine scope management for the entire application.
 *
 * This eliminates the scope proliferation issue by providing pre-configured,
 * properly managed scopes for different types of operations.
 *
 * Usage Guidelines:
 * - UI operations: use uiScope (Dispatchers.Main.immediate)
 * - I/O operations: use ioScope (Dispatchers.IO)
 * - CPU-intensive computation: use computationScope (Dispatchers.Default)
 * - Module-specific operations: createChildScope() for proper lifecycle management
 */
object ScopeManager {

    /**
     * Scope for UI-related operations that need immediate execution.
     * Uses Dispatchers.Main.immediate to avoid unnecessary delays.
     */
    val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Scope for I/O operations (network, file access, database).
     * Uses Dispatchers.IO for optimal I/O performance.
     */
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Scope for CPU-intensive computations and processing.
     * Uses Dispatchers.Default for balanced computation performance.
     */
    val computationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Creates a child scope that inherits from the specified parent scope.
     * Child scopes can be cancelled independently for fine-grained lifecycle control.
     * Note: Child scopes are not automatically cancelled when parent is cancelled.
     *
     * @param parentScope The parent scope to inherit dispatcher from
     * @return A new child scope with SupervisorJob for error isolation
     */
    fun createChildScope(parentScope: CoroutineScope): CoroutineScope {
        return CoroutineScope(SupervisorJob() + parentScope.coroutineContext)
    }

    /**
     * Creates a child scope for UI operations.
     * Useful for module-specific UI operations that should be cancelled when the module is destroyed.
     */
    fun createUIChildScope(): CoroutineScope = createChildScope(uiScope)

    /**
     * Creates a child scope for I/O operations.
     * Useful for module-specific I/O operations that should be cancelled when the module is destroyed.
     */
    fun createIOChildScope(): CoroutineScope = createChildScope(ioScope)

    /**
     * Creates a child scope for computation operations.
     * Useful for module-specific computation that should be cancelled when the module is destroyed.
     */
    fun createComputationChildScope(): CoroutineScope = createChildScope(computationScope)

    /**
     * Cancels all active scopes and their operations.
     * Should be called during app shutdown or critical error scenarios.
     * This will also cancel all child scopes.
     */
    fun cancelAll() {
        uiScope.cancel()
        ioScope.cancel()
        computationScope.cancel()
    }

}
