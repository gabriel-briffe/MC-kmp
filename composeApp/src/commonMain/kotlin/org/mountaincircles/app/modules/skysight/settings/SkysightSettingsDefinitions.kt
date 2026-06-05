package org.mountaincircles.app.modules.skysight.settings

import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.settings.*

/**
 * Skysight module settings definitions - single source of truth
 */
val skysightSettingsDefinitions = listOf(
    // Layer Appearance
    SettingDefinition(
        name = "layerOpacity",
        label = "Layer Opacity",
        range = 0.0f..1.0f,
        defaultValue = 0.75f,
        unit = "%",
        group = "Layer Appearance",
        step = 0.05f,
        order = 1
    ),
    SettingDefinition(
        name = "labelSize",
        label = "Label Size",
        range = 8.0f..24.0f,
        defaultValue = 12.0f,
        unit = "sp",
        group = "Layer Appearance",
        step = 1.0f,
        order = 2
    ),
    // Wave Filtering - Range slider for filtering out values between min and max
    SettingDefinition(
        name = "waveFilterRange",
        label = "Wave Filter",
        range = -2.0f..2.0f,
        defaultValue = -0.5f, // Placeholder - actual range handled in provider
        unit = "m/s",
        group = "Layer Filtering",
        step = 0.1f,
        order = 2
    ),
    // WBL Max/Min Filtering - Specific range slider for wblmaxmin layer
    SettingDefinition(
        name = "wblmaxminFilterRange",
        label = "Convergence filter",
        range = -0.5f..0.5f,
        defaultValue = -0.1f, // Placeholder - actual range handled in provider
        unit = "",
        group = "Layer Filtering",
        step = 0.05f,
        order = 3
    ),

    // Zoom Levels
    SettingDefinition(
        name = "forecastMinZoom",
        label = "Forecast Min Zoom",
        range = 0.0f..20.0f,
        defaultValue = 6.0f,
        unit = "",
        group = "Zoom Levels",
        step = 1.0f,
        decimals = 1,
        order = 4
    )
)

/**
 * Register Skysight settings with the module settings registry
 */
fun registerSkysightSettings(module: SkysightModule) {
    ModuleSettingsRegistry.registerModuleSettings("skysight", skysightSettingsDefinitions)
    ModuleUpdateRegistry.registerModuleUpdateFunctions("skysight", createSkysightUpdateFunctions(module))
}

/**
 * Create update functions for Skysight settings
 */
private fun createSkysightUpdateFunctions(module: SkysightModule): Map<String, SettingUpdateFunction> {
    return mapOf(
        "layerOpacity" to { value ->
            val opacity = (value as? Number)?.toFloat() ?: 0.75f
            module.updateState { it.copy(layerOpacity = opacity) }
            module.settingPersistence.saveFloat("layerOpacity", opacity)
        },
        "labelSize" to { value ->
            val size = (value as? Number)?.toFloat() ?: 12.0f
            module.updateState { it.copy(labelSize = size) }
            module.settingPersistence.saveFloat("labelSize", size)
        },
        "waveFilterRange" to { value ->
            val range = value as? Pair<*, *> ?: Pair(-0.5f, 0.5f)
            val filterMin = (range.first as? Number)?.toFloat() ?: -0.5f
            val filterMax = (range.second as? Number)?.toFloat() ?: 0.5f

            module.updateState { it.copy(waveFilterMin = filterMin, waveFilterMax = filterMax) }
            module.settingPersistence.saveFloat("waveFilterMin", filterMin)
            module.settingPersistence.saveFloat("waveFilterMax", filterMax)
        },
        "wblmaxminFilterRange" to { value ->
            val range = value as? Pair<*, *> ?: Pair(-0.1f, 0.1f)
            val filterMin = (range.first as? Number)?.toFloat() ?: -0.1f
            val filterMax = (range.second as? Number)?.toFloat() ?: 0.1f

            module.updateState { it.copy(wblmaxminFilterMin = filterMin, wblmaxminFilterMax = filterMax) }
            module.settingPersistence.saveFloat("wblmaxminFilterMin", filterMin)
            module.settingPersistence.saveFloat("wblmaxminFilterMax", filterMax)
        },
        "forecastMinZoom" to { value ->
            val minZoom = (value as? Number)?.toFloat() ?: 6.0f
            module.updateState { it.copy(forecastMinZoom = minZoom) }
            module.settingPersistence.saveFloat("forecastMinZoom", minZoom)
        }
    )
}