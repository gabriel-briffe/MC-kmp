package org.mountaincircles.app.modules.livetracking.settings

import org.mountaincircles.app.modules.livetracking.LiveTrackingModule
import org.mountaincircles.app.settings.*
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.logger.LogLevel

// Live Tracking Settings Definitions
val liveTrackingSettingsDefinitions = listOf(
    SettingDefinition(
        name = "aircraftIconSize",
        label = "Aircraft Icon Size",
        range = 0.5f..4.0f,
        defaultValue = 2.0f,
        unit = "x",
        group = "Display",
        step = 0.1f,
        decimals = 1,
        order = 1,
        description = "Size multiplier for aircraft icons"
    ),
    SettingDefinition(
        name = "aircraftIconMinZoom",
        label = "Aircraft Icon Min Zoom",
        range = 0.0f..15.0f,
        defaultValue = 0.0f,
        unit = "",
        group = "Display",
        step = 0.5f,
        decimals = 1,
        order = 2,
        description = "Minimum zoom level to show aircraft icons"
    ),
    SettingDefinition(
        name = "aircraftLabelSize",
        label = "Aircraft Label Size",
        range = 8.0f..30.0f,
        defaultValue = 12.0f,
        unit = "sp",
        group = "Display",
        step = 1.0f,
        decimals = 1,
        order = 3,
        description = "Font size for aircraft labels"
    ),
    SettingDefinition(
        name = "aircraftLabelMinZoom",
        label = "Aircraft Label Min Zoom",
        range = 0.0f..15.0f,
        defaultValue = 6.0f,
        unit = "",
        group = "Display",
        step = 0.5f,
        decimals = 1,
        order = 4,
        description = "Minimum zoom level to show aircraft labels"
    ),
    SettingDefinition(
        name = "aircraftLabelOffset",
        label = "Aircraft Label Offset",
        range = 0.5f..5.0f,
        defaultValue = 2.0f,
        unit = "em",
        group = "Display",
        step = 0.5f,
        decimals = 1,
        order = 5,
        description = "Vertical offset for labels below aircraft"
    ),
    SettingDefinition(
        name = "aircraftDataTimeout",
        label = "keep aircraft data for :",
        range = 1.0f..60.0f,
        defaultValue = 5.0f,
        unit = "mn",
        group = "Display",
        step = 1.0f,
        decimals = 1,
        order = 6,
        description = ""
    )
)

// Update functions for real-time UI updates
fun createLiveTrackingUpdateFunctions(module: LiveTrackingModule): Map<String, SettingUpdateFunction> = mapOf(
    "aircraftIconSize" to { value ->
        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Update function called for aircraftIconSize: $value")
        if (value is Float) {
            module.updateAircraftIconSize(value)
        } else {
            Logger.log("LIVETRACKING_SETTINGS", LogLevel.ERROR, "Invalid value type for aircraftIconSize: ${value::class.simpleName}")
        }
    },
    "aircraftIconMinZoom" to { value ->
        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Update function called for aircraftIconMinZoom: $value")
        if (value is Float) {
            module.updateAircraftIconMinZoom(value)
        } else {
            Logger.log("LIVETRACKING_SETTINGS", LogLevel.ERROR, "Invalid value type for aircraftIconMinZoom: ${value::class.simpleName}")
        }
    },
    "aircraftLabelSize" to { value ->
        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Update function called for aircraftLabelSize: $value")
        if (value is Float) {
            module.updateAircraftLabelSize(value)
        } else {
            Logger.log("LIVETRACKING_SETTINGS", LogLevel.ERROR, "Invalid value type for aircraftLabelSize: ${value::class.simpleName}")
        }
    },
    "aircraftLabelMinZoom" to { value ->
        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Update function called for aircraftLabelMinZoom: $value")
        if (value is Float) {
            module.updateAircraftLabelMinZoom(value)
        } else {
            Logger.log("LIVETRACKING_SETTINGS", LogLevel.ERROR, "Invalid value type for aircraftLabelMinZoom: ${value::class.simpleName}")
        }
    },
    "aircraftLabelOffset" to { value ->
        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Update function called for aircraftLabelOffset: $value")
        if (value is Float) {
            module.updateAircraftLabelOffset(value)
        } else {
            Logger.log("LIVETRACKING_SETTINGS", LogLevel.ERROR, "Invalid value type for aircraftLabelOffset: ${value::class.simpleName}")
        }
    },
    "aircraftDataTimeout" to { value ->
        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Update function called for aircraftDataTimeout: $value")
        if (value is Float) {
            module.updateAircraftDataTimeout(value)
        } else {
            Logger.log("LIVETRACKING_SETTINGS", LogLevel.ERROR, "Invalid value type for aircraftDataTimeout: ${value::class.simpleName}")
        }
    }
)

// Register settings during module initialization
fun registerLiveTrackingSettings(module: LiveTrackingModule) {
    ModuleSettingsRegistry.registerModuleSettings("livetracking", liveTrackingSettingsDefinitions)
    ModuleUpdateRegistry.registerModuleUpdateFunctions("livetracking", createLiveTrackingUpdateFunctions(module))
}
