package org.mountaincircles.app.modules.skysight.settings.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.settings.*
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.ui.settings.SettingsMetadataProvider

/**
 * Settings metadata provider for the Skysight module
 * Uses the generic single-source-of-truth system
 */
class SkysightSettingsProvider(private val module: SkysightModule) : SettingsMetadataProvider {

    override val moduleId: String = "skysight"

    override fun canProvide(module: ModuleBase): Boolean = module is SkysightModule

    @Composable
    override fun getSettingsMetadata(module: ModuleBase): Pair<ClassMetadata?, Map<String, Any?>> {
        if (module !is SkysightModule) return null to emptyMap()

        val state = module.skysightState.collectAsState().value

        // Get settings definitions from the central registry
        val settings = ModuleSettingsRegistry.getModuleSettings("skysight")

        // Convert to field metadata, handling range sliders specially
        val fields = settings.map { setting ->
            if (setting.name == "waveFilterRange") {
                // Create range slider field manually
                SettingsMetadataExtractor.rangeSliderField(
                    name = setting.name,
                    label = setting.label,
                    description = setting.description,
                    min = setting.range.start.toDouble(),
                    max = setting.range.endInclusive.toDouble(),
                    step = setting.step.toDouble(),
                    unit = setting.unit,
                    decimals = setting.decimals,
                    group = setting.group,
                    order = setting.order
                )
            } else {
                setting.toFieldMetadata()
            }
        }

        val metadata = SettingsMetadataExtractor.createClassMetadata(
            name = "SkySight",
            description = "Configure SkySight layer display settings",
            fields = fields,
            category = "Modules"
        )

        // Map current state values to setting names
        val waveFilterMin = module.waveFilterMin.collectAsState().value
        val waveFilterMax = module.waveFilterMax.collectAsState().value
        val wblmaxminFilterMin = module.wblmaxminFilterMin.collectAsState().value
        val wblmaxminFilterMax = module.wblmaxminFilterMax.collectAsState().value
        val values = mapOf<String, Any?>(
            "layerOpacity" to module.layerOpacity.collectAsState().value,
            "labelSize" to module.labelSize.collectAsState().value,
            "waveFilterRange" to Pair(waveFilterMin, waveFilterMax),
            "wblmaxminFilterRange" to Pair(wblmaxminFilterMin, wblmaxminFilterMax),
            "forecastMinZoom" to module.forecastMinZoom.collectAsState().value
        )

        return metadata to values
    }

    override suspend fun handleSettingChange(module: ModuleBase, fieldName: String, value: Any) {
        if (module !is SkysightModule) return
        SettingUpdateUtils.updateSetting(module, fieldName, value)
    }

    override suspend fun resetSettingsToDefaults(module: ModuleBase) {
        if (module !is SkysightModule) return
        SettingUpdateUtils.resetModuleSettingsToDefaults(module)
    }
}