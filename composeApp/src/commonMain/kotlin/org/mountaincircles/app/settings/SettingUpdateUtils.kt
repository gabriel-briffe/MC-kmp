package org.mountaincircles.app.settings

import org.mountaincircles.app.modules.ModuleBase

/**
 * Type alias for update functions
 */
typealias SettingUpdateFunction = suspend (value: Any) -> Unit

/**
 * Registry for module-specific update function mappings
 */
object ModuleUpdateRegistry {

    private val updateFunctions = mutableMapOf<String, Map<String, SettingUpdateFunction>>()

    /**
     * Register update functions for a module
     */
    fun registerModuleUpdateFunctions(moduleId: String, functions: Map<String, SettingUpdateFunction>) {
        updateFunctions[moduleId] = functions
    }

    /**
     * Get update function for a specific setting
     */
    fun getUpdateFunction(moduleId: String, settingName: String): SettingUpdateFunction? {
        return updateFunctions[moduleId]?.get(settingName)
    }
}

/**
 * Utility for calling module-specific update methods generically
 */
object SettingUpdateUtils {

    /**
     * Call an update method on a module based on setting name
     */
    suspend fun updateSetting(module: ModuleBase, settingName: String, value: Any) {
        val updateFunction = ModuleUpdateRegistry.getUpdateFunction(module.moduleId, settingName)
            ?: throw IllegalArgumentException("No update function found for ${module.moduleId}:$settingName")

        try {
            updateFunction(value)
        } catch (e: Exception) {
            println("ERROR: Failed to update setting $settingName on ${module.moduleId}: ${e.message}")
            throw e
        }
    }

    /**
     * Reset all settings for a module to their defaults
     */
    suspend fun resetModuleSettingsToDefaults(module: ModuleBase) {
        val settings = ModuleSettingsRegistry.getModuleSettings(module.moduleId)
        for (setting in settings) {
            updateSetting(module, setting.name, setting.defaultValue)
        }
    }
}
