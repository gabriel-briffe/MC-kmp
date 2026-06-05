package org.mountaincircles.app.persistence

import kotlinx.coroutines.flow.Flow

/**
 * Simple data store interface for cross-platform compatibility
 */
interface SimpleDataStore {
    val data: Flow<Map<String, Any?>>
    suspend fun updateData(transform: suspend (t: Map<String, Any?>) -> Map<String, Any?>): Map<String, Any?>
}

/**
 * Platform-specific DataStore manager for module-scoped data persistence
 *
 * Each module gets its own DataStore instance for isolation and better organization.
 */
expect class DataStoreManager(moduleId: String) {
    val dataStore: SimpleDataStore
}
