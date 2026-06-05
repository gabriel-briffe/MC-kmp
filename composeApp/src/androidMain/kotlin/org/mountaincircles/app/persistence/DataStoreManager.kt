package org.mountaincircles.app.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/**
 * Android implementation of DataStore manager
 *
 * Uses PreferenceDataStore with module-specific file names for isolation.
 */

// Global context holder - initialized from Application
private var appContext: Context? = null

/**
 * Initialize DataStore with Android context
 * Called from Application.onCreate() so it is available for widgets.
 */
fun initializeDataStore(context: Context) {
    appContext = context.applicationContext
}

/**
 * Android DataStore manager implementation
 */
actual class DataStoreManager actual constructor(private val moduleId: String) {

    private val androidDataStore: DataStore<Preferences> by lazy {
        val context = appContext ?: throw IllegalStateException(
            "DataStore not initialized. Call initializeDataStore(context) in Application.onCreate()"
        )

        androidx.datastore.preferences.core.PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                // Create module-specific datastore files
                // Format: /data/data/org.mountaincircles.app/files/datastore/wave.preferences_pb
                "${context.filesDir.absolutePath}/datastore/${moduleId}.preferences_pb".toPath()
            }
        )
    }

    actual val dataStore: SimpleDataStore = object : SimpleDataStore {
        override val data: Flow<Map<String, Any?>> = androidDataStore.data.map { prefs ->
            prefs.asMap().mapKeys { it.key.name }.mapValues { it.value }
        }

        override suspend fun updateData(transform: suspend (t: Map<String, Any?>) -> Map<String, Any?>): Map<String, Any?> {
            val currentPrefs = androidDataStore.data.first()
            val currentMap = currentPrefs.asMap().mapKeys { it.key.name }.mapValues { it.value }
            val newMap = transform(currentMap)

            androidDataStore.edit { prefs ->
                newMap.forEach { (key, value) ->
                    when (value) {
                        is String -> prefs[stringPreferencesKey(key)] = value
                        is Int -> prefs[intPreferencesKey(key)] = value
                        is Long -> prefs[longPreferencesKey(key)] = value
                        is Float -> prefs[floatPreferencesKey(key)] = value
                        is Double -> prefs[doublePreferencesKey(key)] = value
                        is Boolean -> prefs[booleanPreferencesKey(key)] = value
                        else -> {
                            // For complex objects, serialize to string
                            if (value != null) {
                                prefs[stringPreferencesKey(key)] = value.toString()
                            }
                        }
                    }
                }
            }

            return newMap
        }
    }
}
