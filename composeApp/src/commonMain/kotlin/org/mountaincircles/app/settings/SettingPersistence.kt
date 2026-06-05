package org.mountaincircles.app.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.persistence.DataStoreManager
import org.mountaincircles.app.persistence.SimpleDataStore

// Setting types for generic persistence
enum class SettingType {
    FLOAT, BOOLEAN, STRING, INT
}

// Generic setting definition
data class Setting<T>(
    val key: String,
    val defaultValue: T,
    val type: SettingType
)

/**
 * Ultra-simple key-value persistence for module settings
 * Each setting saved as individual DataStore key-value pair
 */
class SettingPersistence(private val moduleId: String) {

    private val dataStore: SimpleDataStore = DataStoreManager(moduleId).dataStore

    // Save individual setting
    suspend fun saveFloat(key: String, value: Float) {
        try {
            Logger.log("PERSISTENCE", LogLevel.DEBUG, "Saving $moduleId.$key = $value")
            dataStore.updateData { currentData ->
                currentData + ("$moduleId.$key" to value)
            }
            Logger.log("PERSISTENCE", LogLevel.DEBUG, "Successfully saved $moduleId.$key")
        } catch (e: Exception) {
            Logger.log("PERSISTENCE", LogLevel.ERROR, "Failed to save $moduleId.$key = $value: ${e.message}", e)
        }
    }

    suspend fun getFloat(key: String, default: Float = 0f): Float {
        Logger.log("PERSISTENCE", LogLevel.DEBUG, "Getting $moduleId.$key (Float), default: $default")
        return dataStore.data.first()["$moduleId.$key"] as? Float ?: default
    }

    // Save individual setting
    suspend fun saveBoolean(key: String, value: Boolean) {
        try {
            Logger.log("PERSISTENCE", LogLevel.DEBUG, "Saving $moduleId.$key = $value")
            dataStore.updateData { currentData ->
                currentData + ("$moduleId.$key" to value)
            }
            Logger.log("PERSISTENCE", LogLevel.DEBUG, "Successfully saved $moduleId.$key")
        } catch (e: Exception) {
            Logger.log("PERSISTENCE", LogLevel.ERROR, "Failed to save $moduleId.$key = $value: ${e.message}", e)
        }
    }

    suspend fun getBoolean(key: String, default: Boolean = false): Boolean {
        Logger.log("PERSISTENCE", LogLevel.DEBUG, "Getting $moduleId.$key (Boolean), default: $default")
        return dataStore.data.first()["$moduleId.$key"] as? Boolean ?: default
    }

    suspend fun saveString(key: String, value: String?) {
        try {
            Logger.log("PERSISTENCE", LogLevel.DEBUG, "Saving $moduleId.$key = $value")
            dataStore.updateData { currentData ->
                if (value != null) {
                    currentData + ("$moduleId.$key" to value)
                } else {
                    currentData - "$moduleId.$key"
                }
            }
            Logger.log("PERSISTENCE", LogLevel.DEBUG, "Successfully saved $moduleId.$key")
        } catch (e: Exception) {
            Logger.log("PERSISTENCE", LogLevel.ERROR, "Failed to save $moduleId.$key = $value: ${e.message}", e)
        }
    }

    suspend fun saveInt(key: String, value: Int) {
        try {
            Logger.log("PERSISTENCE", LogLevel.DEBUG, "Saving $moduleId.$key = $value")
            dataStore.updateData { currentData ->
                currentData + ("$moduleId.$key" to value)
            }
            Logger.log("PERSISTENCE", LogLevel.DEBUG, "Successfully saved $moduleId.$key")
        } catch (e: Exception) {
            Logger.log("PERSISTENCE", LogLevel.ERROR, "Failed to save $moduleId.$key = $value: ${e.message}", e)
        }
    }

    suspend fun getInt(key: String, default: Int = 0): Int {
        Logger.log("PERSISTENCE", LogLevel.DEBUG, "Getting $moduleId.$key (Int), default: $default")
        return dataStore.data.first()["$moduleId.$key"] as? Int ?: default
    }

    suspend fun getString(key: String, default: String? = null): String? {
        Logger.log("PERSISTENCE", LogLevel.DEBUG, "Getting $moduleId.$key (String), default: $default")
        return dataStore.data.first()["$moduleId.$key"] as? String ?: default
    }
}
