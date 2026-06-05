package org.mountaincircles.app.persistence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.cinterop.ExperimentalForeignApi
import org.mountaincircles.app.logger.Logger
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSNumber
import platform.Foundation.NSArray

/**
 * iOS implementation of DataStore manager using NSUserDefaults
 *
 * Since iOS doesn't have the same DataStore API as Android, we create a compatibility layer
 * that implements the same interface using NSUserDefaults with module-scoped keys.
 */
actual class DataStoreManager actual constructor(private val moduleId: String) {

    private val userDefaults = NSUserDefaults.standardUserDefaults

    // Create a simple preferences implementation that wraps NSUserDefaults
    actual val dataStore: SimpleDataStore = object : SimpleDataStore {
        override val data: Flow<Map<String, Any?>> = flow {
            // Emit current preferences
            emit(getCurrentPreferences())
        }

        override suspend fun updateData(transform: suspend (t: Map<String, Any?>) -> Map<String, Any?>): Map<String, Any?> {
            return try {
                val currentPrefs = getCurrentPreferences()
                val newPrefs = transform(currentPrefs)
                savePreferences(newPrefs)
                newPrefs
            } catch (e: Exception) {
                Logger.warn("DATASTORE_IOS", "Error updating data: ${e.message}")
                getCurrentPreferences()
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun getCurrentPreferences(): Map<String, Any?> {
        val prefs = mutableMapOf<String, Any?>()

        // Get all keys for this module
        val allKeys = userDefaults.dictionaryRepresentation()?.keys?.map { it.toString() } ?: emptyList()
        val moduleKeys = allKeys.filter { it.startsWith("$moduleId.") }

        for (key in moduleKeys) {
            val value = userDefaults.objectForKey(key)
            val prefKey = parseKey(key)

            when (value) {
                is String -> prefs[prefKey] = value
                is NSNumber -> {
                    // Try to convert to different types, using the most appropriate one
                    prefs[prefKey] = when {
                        value.boolValue == true || value.boolValue == false -> value.boolValue
                        else -> {
                            // Try as int first, then long, then float, then double
                            val intValue = value.intValue
                            val longValue = value.longValue
                            val floatValue = value.floatValue
                            val doubleValue = value.doubleValue

                            when {
                                intValue.toLong() == longValue -> intValue.toInt()
                                longValue.toFloat() == floatValue -> longValue
                                floatValue.toDouble() == doubleValue -> floatValue
                                else -> doubleValue
                            }
                        }
                    }
                }
                is NSArray -> {
                    val stringSet = mutableSetOf<String>()
                    val count = value.count.toInt()
                    for (i in 0 until count) {
                        (value.objectAtIndex(i.toULong()) as? String)?.let { stringSet.add(it) }
                    }
                    prefs[prefKey] = stringSet
                }
            }
        }

        return prefs
    }

    private fun savePreferences(preferences: Map<String, Any?>) {
        val keysToRemove = mutableSetOf<String>()

        // First, collect all existing module keys to potentially remove them
        val allKeys = userDefaults.dictionaryRepresentation()?.keys?.map { it.toString() } ?: emptyList()
        keysToRemove.addAll(allKeys.filter { it.startsWith("$moduleId.") })

        // Save new preferences
        preferences.forEach { (key, value) ->
            val storageKey = "$moduleId.$key"
            keysToRemove.remove(storageKey)

            when (value) {
                is String -> userDefaults.setObject(value, storageKey)
                is Boolean -> userDefaults.setBool(value, storageKey)
                is Int -> userDefaults.setInteger(value.toLong(), storageKey)
                is Long -> userDefaults.setInteger(value, storageKey)
                is Float -> userDefaults.setFloat(value, storageKey)
                is Double -> userDefaults.setDouble(value, storageKey)
                is Set<*> -> {
                    val array = value.mapNotNull { it as? String }.toTypedArray()
                    userDefaults.setObject(array, storageKey)
                }
            }
        }

        // Remove keys that are no longer in preferences
        keysToRemove.forEach { key ->
            userDefaults.removeObjectForKey(key)
        }

        userDefaults.synchronize()
    }

    private fun parseKey(fullKey: String): String {
        return fullKey.removePrefix("$moduleId.")
    }
}
