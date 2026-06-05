package org.mountaincircles.app.settings

/**
 * Central registry for module settings definitions.
 * Maintains single source of truth for all settings across modules.
 */
object ModuleSettingsRegistry {

    private val settingsDefinitions = mutableMapOf<String, List<SettingDefinition>>()

    /**
     * Register settings definitions for a module
     */
    fun registerModuleSettings(moduleId: String, settings: List<SettingDefinition>) {
        settingsDefinitions[moduleId] = settings
    }

    /**
     * Get settings definitions for a module
     */
    fun getModuleSettings(moduleId: String): List<SettingDefinition> {
        return settingsDefinitions[moduleId] ?: emptyList()
    }

    /**
     * Get default value for a specific setting
     */
    fun getDefaultValue(moduleId: String, settingName: String): Float? {
        return getModuleSettings(moduleId)
            .find { it.name == settingName }
            ?.defaultValue
    }

    /**
     * Check if a module has registered settings
     */
    fun hasModuleSettings(moduleId: String): Boolean {
        return settingsDefinitions.containsKey(moduleId)
    }

    /**
     * Get all registered module IDs
     */
    fun getRegisteredModules(): Set<String> {
        return settingsDefinitions.keys
    }
}
