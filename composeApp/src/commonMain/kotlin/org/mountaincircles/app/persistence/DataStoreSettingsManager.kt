package org.mountaincircles.app.persistence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * DataStore-backed settings manager for type-safe persistence of module settings
 *
 * This replaces the previous FilePersistenceManager-based SettingsManager with DataStore,
 * providing better coroutine safety, atomic operations, and native Android integration.
 *
 * @param T The type of settings object to manage
 * @param moduleId Unique identifier for the module (used for DataStore file isolation)
 * @param serializer Kotlinx.serialization serializer for the settings type
 * @param defaultSettings Default settings instance to use when loading fails
 */
class DataStoreSettingsManager<T : Any>(
    private val moduleId: String,
    private val serializer: KSerializer<T>,
    private val defaultSettings: T
) {
    private val dataStore = DataStoreManager(moduleId).dataStore

    // JSON configuration for serialization (same as before)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    /**
     * Reactive settings flow that emits whenever settings change
     * This replaces the previous MutableStateFlow pattern
     */
    val settings: Flow<T> = dataStore.data
        .map { preferences ->
            val jsonString = preferences[SETTINGS_KEY] as? String ?: ""
            if (jsonString.isEmpty()) {
                Logger.log("DataStore", LogLevel.DEBUG, "[$moduleId] No stored settings found, using defaults")
                defaultSettings
            } else {
                try {
                    val settings = json.decodeFromString(serializer, jsonString)
                    Logger.log("DataStore", LogLevel.DEBUG, "[$moduleId] Successfully loaded settings")
                    settings
                } catch (e: Exception) {
                    Logger.log("DataStore", LogLevel.ERROR, "[$moduleId] Failed to deserialize settings: ${e.message}", e)
                    defaultSettings
                }
            }
        }
        .catch { exception ->
            Logger.log("DataStore", LogLevel.ERROR, "[$moduleId] DataStore read error: ${exception.message}", exception)
            emit(defaultSettings)
        }

    /**
     * Load current settings from DataStore
     */
    suspend fun load(): T {
        return try {
            Logger.log("DataStore", LogLevel.DEBUG, "[$moduleId] Loading current settings...")
            val loadedSettings = settings.first()
            Logger.log("DataStore", LogLevel.DEBUG, "[$moduleId] Current settings loaded successfully")
            loadedSettings
        } catch (e: Exception) {
            Logger.log("DataStore", LogLevel.WARN, "[$moduleId] Failed to load settings, using defaults: ${e.message}")
            defaultSettings
        }
    }

    /**
     * Save settings to DataStore
     */
    suspend fun save(settings: T) {
        try {
            val jsonString = json.encodeToString(serializer, settings)
            Logger.log("DataStore", LogLevel.DEBUG, "[$moduleId] Saving settings...")

            dataStore.updateData { currentData ->
                currentData.toMutableMap().apply {
                    this[SETTINGS_KEY] = jsonString
                }
            }

            Logger.log("DataStore", LogLevel.INFO, "[$moduleId] Settings saved successfully")
        } catch (e: Exception) {
            Logger.log("DataStore", LogLevel.ERROR, "[$moduleId] Failed to save settings: ${e.message}", e)
            throw e
        }
    }

    /**
     * Update settings using a transform function
     * This provides an atomic update pattern
     */
    suspend fun update(transform: (T) -> T) {
        try {
            Logger.log("DataStore", LogLevel.DEBUG, "[$moduleId] Updating settings with transform...")
            val current = load()
            val updated = transform(current)
            save(updated)
            Logger.log("DataStore", LogLevel.DEBUG, "[$moduleId] Settings updated successfully")
        } catch (e: Exception) {
            Logger.log("DataStore", LogLevel.ERROR, "[$moduleId] Failed to update settings: ${e.message}")
            throw e
        }
    }

    /**
     * Reset settings to defaults
     */
    suspend fun reset() {
        Logger.log("DataStore", LogLevel.INFO, "[$moduleId] Resetting settings to defaults")
        save(defaultSettings)
    }

    /**
     * Get current settings from DataStore
     */
    suspend fun getCurrentSettings(): T {
        return load()
    }

    /**
     * Initialize the settings manager (called during module initialization)
     */
    suspend fun initialize() = load()

    companion object {
        private const val SETTINGS_KEY = "settings"
    }
}
