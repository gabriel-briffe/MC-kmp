package org.mountaincircles.app.modules.wave.settings

import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.settings.ModuleSettingsRegistry
import org.mountaincircles.app.settings.ModuleUpdateRegistry
import org.mountaincircles.app.settings.SettingDefinition
import org.mountaincircles.app.settings.SettingUpdateFunction

/**
 * Wave module settings definitions - single source of truth
 */
val waveSettingsDefinitions = listOf(
    // Wave Display
    SettingDefinition(
        name = "waveOpacity",
        label = "Layer Opacity",
        range = 0.5f..1.0f,
        defaultValue = 0.75f,
        unit = "%",
        group = "Wave Display",
        step = 0.05f,
        decimals = 2,
        order = 1
    ),

    // Wave Labels
    SettingDefinition(
        name = "waveMainLabelFontSize",
        label = "Main Label Size",
        range = 8.0f..20.0f,
        defaultValue = 13.0f,
        unit = "sp",
        group = "Menu",
        step = 0.5f,
        order = 2
    ),
    SettingDefinition(
        name = "waveSubLabelFontSize",
        label = "Sub Label Size",
        range = 6.0f..16.0f,
        defaultValue = 10.0f,
        unit = "sp",
        group = "Menu",
        step = 0.5f,
        order = 3
    ),

    // Wind Display
    SettingDefinition(
        name = "windBarbSize",
        label = "Wind Barb Size",
        range = 0.1f..2.0f,
        defaultValue = 0.5f,
        unit = "x",
        group = "Wind Display",
        step = 0.05f,
        decimals = 1,
        order = 4
    ),
    SettingDefinition(
        name = "windSpeedScaleDistortion",
        label = "Wind Barb Size Distortion",
        range = 0f..0.5f,
        defaultValue = 0.3f,
        unit = "",
        group = "Wind Display",
        step = 0.1f,
        decimals = 1,
        order = 5
    ),
    SettingDefinition(
        name = "barbInterval",
        label = "Wind Barb Interval",
        range = 2f..20f,
        defaultValue = 10f,
        unit = "mm",
        group = "Wind Display",
        step = 1f,
        decimals = 0,
        order = 6
    ),
    SettingDefinition(
        name = "showZeroWindBarbs",
        label = "Show Zero Wind Barbs",
        range = 0f..1f, // Boolean represented as float
        defaultValue = 0f, // Default: don't show zero wind barbs
        unit = "",
        group = "Wind Display",
        step = 1f,
        decimals = 0,
        order = 7
    )
)

/**
 * Wave module update functions mapping
 */
fun createWaveUpdateFunctions(module: WaveModule): Map<String, SettingUpdateFunction> {
    return mapOf(
        "waveOpacity" to { value -> module.updateOpacity(value as Float) },
        "waveMainLabelFontSize" to { value -> module.updateMainLabelFontSize(value as Float) },
        "waveSubLabelFontSize" to { value -> module.updateSubLabelFontSize(value as Float) },
        "windBarbSize" to { value -> module.updateWindBarbSize(value as Float) },
        "windSpeedScaleDistortion" to { value -> module.updateWindSpeedScaleDistortion(value as Float) },
        "barbInterval" to { value -> module.updateBarbInterval(value.toString().toFloat()) },
        "showZeroWindBarbs" to { value ->
            // Handle both Float (from reset) and Boolean (from UI) values
            val boolValue = when (value) {
                is Boolean -> value
                is Float -> value > 0.5f
                is Int -> value > 0
                else -> false
            }
            module.updateShowZeroWindBarbs(boolValue)
        }
    )
}

// Register settings during module initialization
fun registerWaveSettings(module: WaveModule) {
    ModuleSettingsRegistry.registerModuleSettings("wave", waveSettingsDefinitions)
    ModuleUpdateRegistry.registerModuleUpdateFunctions("wave", createWaveUpdateFunctions(module))
}
