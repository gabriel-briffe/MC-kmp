package org.mountaincircles.app.persistence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.mountaincircles.app.error.AppError
import org.mountaincircles.app.error.ErrorHandler

/**
 * DataStore-backed implementation of DataManager.
 * Provides type-safe, reactive data persistence with consistent error handling.
 *
 * @param T The type of data to manage
 * @param moduleId Unique identifier for the data (used for DataStore isolation)
 * @param serializer Kotlinx.serialization serializer for the data type
 * @param defaultData Default data instance to use when loading fails
 * @param dataKey Key used to store data in DataStore (defaults to "data")
 */
class DataStoreDataManager<T : Any>(
    private val moduleId: String,
    private val serializer: KSerializer<T>,
    private val defaultData: T,
    private val dataKey: String = "data"
) : DataManager<T> {

    private val dataStore = DataStoreManager(moduleId).dataStore

    // JSON configuration for consistent serialization
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    /**
     * Reactive data flow that emits whenever data changes.
     * Handles deserialization errors gracefully.
     */
    override fun observe(): Flow<T> = dataStore.data
        .map { preferences ->
            val jsonString = preferences[dataKey] as? String ?: ""
            if (jsonString.isEmpty()) {
                defaultData
            } else {
                try {
                    json.decodeFromString(serializer, jsonString)
                } catch (e: Exception) {
                    val error = AppError.DataParseError("Failed to parse stored data for $moduleId", jsonString, e)
                    ErrorHandler.handle(error, "DataStoreDataManager.observe")
                    defaultData
                }
            }
        }
        .catch { exception ->
            val error = AppError.PersistenceError("DataStore read failed for $moduleId", "observe", exception)
            ErrorHandler.handle(error, "DataStoreDataManager.observe")
            emit(defaultData)
        }

    /**
     * Load current data from DataStore.
     * Uses the reactive flow internally but returns a single value.
     */
    override suspend fun load(): T {
        return try {
            observe().first()
        } catch (e: Exception) {
            val error = AppError.PersistenceError("Failed to load data for $moduleId", "load", e)
            ErrorHandler.handle(error, "DataStoreDataManager.load")
            defaultData
        }
    }

    /**
     * Save data to DataStore with error handling.
     */
    override suspend fun save(data: T) {
        try {
            val jsonString = json.encodeToString(serializer, data)

            dataStore.updateData { currentData ->
                currentData.toMutableMap().apply {
                    this[dataKey] = jsonString
                }
            }
        } catch (e: Exception) {
            val error = AppError.PersistenceError("Failed to save data for $moduleId", "save", e)
            ErrorHandler.handle(error, "DataStoreDataManager.save")
            throw e
        }
    }
}
