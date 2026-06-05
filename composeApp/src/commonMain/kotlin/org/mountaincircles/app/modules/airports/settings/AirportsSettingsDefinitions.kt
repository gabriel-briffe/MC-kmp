package org.mountaincircles.app.modules.airports.settings

import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.settings.ModuleSettingsRegistry
import org.mountaincircles.app.settings.ModuleUpdateRegistry
import org.mountaincircles.app.settings.SettingDefinition
import org.mountaincircles.app.settings.SettingUpdateFunction

/**
 * Airports module settings definitions - single source of truth
 */
val airportsSettingsDefinitions = listOf(
    // Airport Appearance
    SettingDefinition(
        name = "airportIconSize",
        label = "Airport Icon Size",
        range = 2.0f..20.0f,
        defaultValue = 6.0f,
        unit = "dp",
        group = "Airport Appearance",
        step = 0.5f,
        order = 1
    ),
    SettingDefinition(
        name = "airportLabelSize",
        label = "Airport Label Size",
        range = 8.0f..40.0f,
        defaultValue = 12.0f,
        unit = "sp",
        group = "Airport Appearance",
        step = 1.0f,
        order = 2
    ),

    // Zoom Levels
    SettingDefinition(
        name = "airportIconsMinZoom",
        label = "Airport Icons Min Zoom",
        range = 1.0f..18.0f,
        defaultValue = 6.0f,
        unit = "",
        group = "Zoom Levels",
        step = 1.0f,
        decimals = 1,
        order = 3
    ),
    SettingDefinition(
        name = "airportLabelsMinZoom",
        label = "Airport Labels Min Zoom",
        range = 1.0f..18.0f,
        defaultValue = 8.0f,
        unit = "",
        group = "Zoom Levels",
        step = 1.0f,
        decimals = 1,
        order = 4
    )
)

/**
 * Airports module update functions mapping
 */
fun createAirportsUpdateFunctions(module: AirportsModule): Map<String, SettingUpdateFunction> {
    return mapOf(
        "airportIconSize" to { value -> module.updateAirportIconSize(value as Float) },
        "airportLabelSize" to { value -> module.updateAirportLabelSize(value as Float) },
        "airportIconsMinZoom" to { value -> module.updateAirportIconsMinZoom(value as Float) },
        "airportLabelsMinZoom" to { value -> module.updateAirportLabelsMinZoom(value as Float) }
    )
}

// Register settings during module initialization
fun registerAirportsSettings(module: AirportsModule) {
    ModuleSettingsRegistry.registerModuleSettings("airports", airportsSettingsDefinitions)
    ModuleUpdateRegistry.registerModuleUpdateFunctions("airports", createAirportsUpdateFunctions(module))
}
