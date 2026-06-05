package org.mountaincircles.app.modules.wave.settings.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.settings.*
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.ui.settings.SettingsMetadataProvider

/**
 * Settings metadata provider for the Wave module
 * Now uses the generic single-source-of-truth system
 */
class WaveSettingsProvider(private val module: WaveModule) : SettingsMetadataProvider {

    override val moduleId: String = "wave"

    override fun canProvide(module: ModuleBase): Boolean = module is WaveModule

    @Composable
    override fun getSettingsMetadata(module: ModuleBase): Pair<ClassMetadata?, Map<String, Any?>> {
        if (module !is WaveModule) return null to emptyMap()

        val state = module.combinedStateFlow.collectAsState().value

        // Get settings definitions from the central registry
        val settings = ModuleSettingsRegistry.getModuleSettings("wave")
        val fields = settings.map { it.toFieldMetadata() }

        val metadata = SettingsMetadataExtractor.createClassMetadata(
            name = "Wave - Wind",
            description = "Configure wave and wind display settings",
            fields = fields,
            category = "Modules"
        )

        // Map current state values to setting names
        val values = mapOf<String, Any?>(
            "waveOpacity" to state.opacity,
            "waveMainLabelFontSize" to state.mainLabelFontSize,
            "waveSubLabelFontSize" to state.subLabelFontSize,
            "windBarbSize" to state.windBarbSize,
            "windSpeedScaleDistortion" to state.windSpeedScaleDistortion,
            "barbInterval" to state.barbInterval,
            "showZeroWindBarbs" to state.showZeroWindBarbs
        )

        return metadata to values
    }

    override suspend fun handleSettingChange(module: ModuleBase, fieldName: String, value: Any) {
        if (module !is WaveModule) return
        SettingUpdateUtils.updateSetting(module, fieldName, value)
    }

    override suspend fun resetSettingsToDefaults(module: ModuleBase) {
        if (module !is WaveModule) return
        SettingUpdateUtils.resetModuleSettingsToDefaults(module)
    }
}
