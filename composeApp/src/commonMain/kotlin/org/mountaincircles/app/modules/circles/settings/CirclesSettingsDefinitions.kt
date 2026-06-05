package org.mountaincircles.app.modules.circles.settings

import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.settings.ModuleSettingsRegistry
import org.mountaincircles.app.settings.ModuleUpdateRegistry
import org.mountaincircles.app.settings.SettingDefinition
import org.mountaincircles.app.settings.SettingUpdateFunction

/**
 * Circles module settings definitions - single source of truth
 */
val circlesSettingsDefinitions = listOf(
    // Circle Appearance
    SettingDefinition(
        name = "circlesLineWidth",
        label = "Circle Line Width",
        range = 0.5f..5.0f,
        defaultValue = 2.0f,
        unit = "dp",
        group = "Circle Appearance",
        step = 0.1f,
        order = 1
    ),
    SettingDefinition(
        name = "circlesLabelSize",
        label = "Circle Label Size",
        range = 8.0f..30.0f,
        defaultValue = 14.0f,
        unit = "sp",
        group = "Circle Appearance",
        step = 1.0f,
        order = 2
    ),
    SettingDefinition(
        name = "circlesLabelSpacing",
        label = "Circle Label Spacing",
        range = 20.0f..300.0f,
        defaultValue = 240.0f,
        unit = "dp",
        group = "Circle Appearance",
        step = 5.0f,
        order = 3
    ),

    // Airfield Appearance
    SettingDefinition(
        name = "airfieldIconSize",
        label = "Airfield Icon Size",
        range = 4.0f..20.0f,
        defaultValue = 6.0f,
        unit = "dp",
        group = "Airfield Appearance",
        step = 0.5f,
        order = 4
    ),
    SettingDefinition(
        name = "airfieldLabelSize",
        label = "Airfield Label Size",
        range = 8.0f..40.0f,
        defaultValue = 12.0f,
        unit = "sp",
        group = "Airfield Appearance",
        step = 1.0f,
        order = 5
    ),

    // Airfield Interaction
    SettingDefinition(
        name = "airfieldClickSize",
        label = "Airfield Click Size",
        range = 10.0f..100.0f,
        defaultValue = 30.0f,
        unit = "px",
        group = "Airfield Interaction",
        step = 5.0f,
        order = 6
    ),

    // Zoom Levels
    SettingDefinition(
        name = "circlesMinZoom",
        label = "Circles Min Zoom",
        range = 1.0f..18.0f,
        defaultValue = 7.0f,
        unit = "",
        group = "Zoom Levels",
        step = 1.0f,
        decimals = 1,
        order = 7
    ),
    SettingDefinition(
        name = "circleLabelsMinZoom",
        label = "Circle Labels Min Zoom",
        range = 1.0f..18.0f,
        defaultValue = 9.0f,
        unit = "",
        group = "Zoom Levels",
        step = 1.0f,
        decimals = 1,
        order = 8
    ),
    SettingDefinition(
        name = "airfieldIconsMinZoom",
        label = "Airfield Icons Min Zoom",
        range = 1.0f..18.0f,
        defaultValue = 18.0f,
        unit = "",
        group = "Zoom Levels",
        step = 1.0f,
        decimals = 1,
        order = 9
    ),
    SettingDefinition(
        name = "airfieldLabelsMinZoom",
        label = "Airfield Labels Min Zoom",
        range = 1.0f..18.0f,
        defaultValue = 18.0f,
        unit = "",
        group = "Zoom Levels",
        step = 1.0f,
        decimals = 1,
        order = 10
    ),
    SettingDefinition(
        name = "sectorsMinZoom",
        label = "Sectors Min Zoom",
        range = 1.0f..18.0f,
        defaultValue = 1.0f,
        unit = "",
        group = "Zoom Levels",
        step = 1.0f,
        decimals = 1,
        order = 11
    )
)

/**
 * Circles module update functions mapping
 */
fun createCirclesUpdateFunctions(module: CirclesModule): Map<String, SettingUpdateFunction> {
    return mapOf(
        "circlesLineWidth" to { value -> module.updateCirclesLineWidth(value as Float) },
        "circlesLabelSize" to { value -> module.updateCirclesLabelSize(value as Float) },
        "circlesLabelSpacing" to { value -> module.updateCirclesLabelSpacing(value as Float) },
        "airfieldIconSize" to { value -> module.updateAirfieldIconSize(value as Float) },
        "airfieldClickSize" to { value ->
            module.updateAirfieldClickSize(value as Float)
            module.showClickAreaFeedback()
        },
        "airfieldLabelSize" to { value -> module.updateAirfieldLabelSize(value as Float) },
        "circlesMinZoom" to { value -> module.updateCirclesMinZoom(value as Float) },
        "circleLabelsMinZoom" to { value -> module.updateCircleLabelsMinZoom(value as Float) },
        "airfieldIconsMinZoom" to { value -> module.updateAirfieldIconsMinZoom(value as Float) },
        "airfieldLabelsMinZoom" to { value -> module.updateAirfieldLabelsMinZoom(value as Float) },
        "sectorsMinZoom" to { value -> module.updateSectorsMinZoom(value as Float) }
    )
}

// Register settings during module initialization
fun registerCirclesSettings(module: CirclesModule) {
    ModuleSettingsRegistry.registerModuleSettings("circles", circlesSettingsDefinitions)
    ModuleUpdateRegistry.registerModuleUpdateFunctions("circles", createCirclesUpdateFunctions(module))
}
