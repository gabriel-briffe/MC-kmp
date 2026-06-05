package org.mountaincircles.app.modules.airports.settings.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.settings.*
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.ui.settings.SettingsMetadataProvider

/**
 * Settings metadata provider for the Airports module
 * Now uses the generic single-source-of-truth system
 */
class AirportsSettingsProvider(private val module: AirportsModule) : SettingsMetadataProvider {

    override val moduleId: String = "airports"

    override fun canProvide(module: ModuleBase): Boolean = module is AirportsModule

    @Composable
    override fun getSettingsMetadata(module: ModuleBase): Pair<ClassMetadata?, Map<String, Any?>> {
        if (module !is AirportsModule) return null to emptyMap()

        val state = module.airportsState.collectAsState().value

        // Get settings definitions from the central registry
        val settings = ModuleSettingsRegistry.getModuleSettings("airports")
        val fields = settings.map { it.toFieldMetadata() }

        val metadata = SettingsMetadataExtractor.createClassMetadata(
            name = "Airports",
            description = "Configure airport display settings",
            fields = fields,
            category = "Modules"
        )

        // Map current state values to setting names
        val values = mapOf<String, Any?>(
            "airportIconSize" to state.airportIconSize,
            "airportLabelSize" to state.airportLabelSize,
            "airportIconsMinZoom" to state.airportIconsMinZoom,
            "airportLabelsMinZoom" to state.airportLabelsMinZoom
        )

        return metadata to values
    }

    override suspend fun handleSettingChange(module: ModuleBase, fieldName: String, value: Any) {
        if (module !is AirportsModule) return
        SettingUpdateUtils.updateSetting(module, fieldName, value)
    }

    override suspend fun resetSettingsToDefaults(module: ModuleBase) {
        if (module !is AirportsModule) return
        SettingUpdateUtils.resetModuleSettingsToDefaults(module)
    }
}
