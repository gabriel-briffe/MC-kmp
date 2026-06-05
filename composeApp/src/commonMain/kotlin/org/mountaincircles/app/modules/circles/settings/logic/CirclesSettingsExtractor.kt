package org.mountaincircles.app.modules.circles.settings.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.settings.*
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.ui.settings.SettingsMetadataProvider

/**
 * Settings metadata provider for the Circles module
 * Now uses the generic single-source-of-truth system
 */
class CirclesSettingsProvider(private val module: CirclesModule) : SettingsMetadataProvider {

    override val moduleId: String = "circles"

    override fun canProvide(module: ModuleBase): Boolean = module is CirclesModule

    @Composable
    override fun getSettingsMetadata(module: ModuleBase): Pair<ClassMetadata?, Map<String, Any?>> {
        if (module !is CirclesModule) return null to emptyMap()

        val state = module.circlesState.collectAsState().value

        // Get settings definitions from the central registry
        val settings = ModuleSettingsRegistry.getModuleSettings("circles")
        val fields = settings.map { it.toFieldMetadata() }

        val metadata = SettingsMetadataExtractor.createClassMetadata(
            name = "Circles",
            description = "Configure circle and airfield display settings",
            fields = fields,
            category = "Modules"
        )

        // Map current state values to setting names
        val values = mapOf<String, Any?>(
            "circlesLineWidth" to state.circlesLineWidth,
            "circlesLabelSize" to state.circlesLabelSize,
            "circlesLabelSpacing" to state.circlesLabelSpacing,
            "airfieldIconSize" to state.airfieldIconSize,
            "airfieldClickSize" to state.airfieldClickSize,
            "airfieldLabelSize" to state.airfieldLabelSize,
            "circlesMinZoom" to state.circlesMinZoom,
            "circleLabelsMinZoom" to state.circleLabelsMinZoom,
            "airfieldIconsMinZoom" to state.airfieldIconsMinZoom,
            "airfieldLabelsMinZoom" to state.airfieldLabelsMinZoom,
            "sectorsMinZoom" to state.sectorsMinZoom
        )

        return metadata to values
    }

    override suspend fun handleSettingChange(module: ModuleBase, fieldName: String, value: Any) {
        if (module !is CirclesModule) return
        SettingUpdateUtils.updateSetting(module, fieldName, value)
    }

    override suspend fun resetSettingsToDefaults(module: ModuleBase) {
        if (module !is CirclesModule) return
        SettingUpdateUtils.resetModuleSettingsToDefaults(module)
    }
}
