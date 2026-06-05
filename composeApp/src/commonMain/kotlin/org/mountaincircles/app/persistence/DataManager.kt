package org.mountaincircles.app.persistence

import kotlinx.coroutines.flow.Flow

/**
 * Unified interface for data persistence operations.
 * Provides consistent async patterns across the application.
 *
 * @param T The type of data to manage
 */
interface DataManager<T : Any> {
    /**
     * Load data from persistent storage.
     * Returns default data if loading fails.
     */
    suspend fun load(): T

    /**
     * Save data to persistent storage.
     * Throws exception if saving fails.
     */
    suspend fun save(data: T)

    /**
     * Observe data changes as a reactive flow.
     * Emits updated data whenever persistence changes.
     */
    fun observe(): Flow<T>

    /**
     * Update data using a transform function.
     * Provides atomic update operations.
     */
    suspend fun update(transform: (T) -> T) {
        val current = load()
        val updated = transform(current)
        save(updated)
    }
}
