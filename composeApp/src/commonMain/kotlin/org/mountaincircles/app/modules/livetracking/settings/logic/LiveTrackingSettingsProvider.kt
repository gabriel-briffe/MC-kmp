package org.mountaincircles.app.modules.livetracking.settings.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.mountaincircles.app.modules.livetracking.LiveTrackingModule
import org.mountaincircles.app.settings.*
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.ui.settings.SettingsMetadataProvider
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.logger.LogLevel

class LiveTrackingSettingsProvider(private val module: LiveTrackingModule) : SettingsMetadataProvider {

    override val moduleId: String = "livetracking"

    override fun canProvide(module: ModuleBase): Boolean = module is LiveTrackingModule

    @Composable
    override fun getSettingsMetadata(module: ModuleBase): Pair<ClassMetadata?, Map<String, Any?>> {
        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "getSettingsMetadata called for ${module.moduleId}")

        if (module !is LiveTrackingModule) {
            Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Module is not LiveTrackingModule")
            return null to emptyMap()
        }

        // Collect reactive settings state - this makes the composable reactive to state changes
        val settings by module.liveTrackingSettings
        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Settings values: icon=${settings.iconSize}, label=${settings.labelSize}, offset=${settings.labelOffset}")

        // Get settings definitions from the central registry
        val settingDefs = ModuleSettingsRegistry.getModuleSettings("livetracking")
        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Found ${settingDefs.size} setting definitions")

        val fields = settingDefs.map { it.toFieldMetadata() }
        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Created ${fields.size} field metadata")

        val metadata = SettingsMetadataExtractor.createClassMetadata(
            name = "Live Tracking",
            description = "Configure live aircraft tracking display settings",
            fields = fields,
            category = "Modules"
        )

        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Created metadata with ${metadata?.fields?.size ?: 0} fields")

        // Return current values from reactive settings state
        val currentValues = mapOf(
            "aircraftIconSize" to settings.iconSize,
            "aircraftIconMinZoom" to settings.iconMinZoom,
            "aircraftLabelSize" to settings.labelSize,
            "aircraftLabelMinZoom" to settings.labelMinZoom,
            "aircraftLabelOffset" to settings.labelOffset,
            "aircraftDataTimeout" to settings.timeOut
        )

        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "Returning metadata and ${currentValues.size} current values")

        return metadata to currentValues
    }

    override suspend fun handleSettingChange(module: ModuleBase, fieldName: String, value: Any) {
        if (module !is LiveTrackingModule) return

        Logger.log("LIVETRACKING_SETTINGS", LogLevel.DEBUG, "handleSettingChange called: $fieldName = $value (${value::class.simpleName})")

        when (fieldName) {
            "aircraftIconSize" -> if (value is Float) module.updateAircraftIconSize(value)
            "aircraftIconMinZoom" -> if (value is Float) module.updateAircraftIconMinZoom(value)
            "aircraftLabelSize" -> if (value is Float) module.updateAircraftLabelSize(value)
            "aircraftLabelMinZoom" -> if (value is Float) module.updateAircraftLabelMinZoom(value)
            "aircraftLabelOffset" -> if (value is Float) module.updateAircraftLabelOffset(value)
            "aircraftDataTimeout" -> if (value is Float) module.updateAircraftDataTimeout(value)
            else -> Logger.log("LIVETRACKING_SETTINGS", LogLevel.WARN, "Unknown setting: $fieldName")
        }
    }

    override suspend fun resetSettingsToDefaults(module: ModuleBase) {
        if (module !is LiveTrackingModule) return

        val settings = ModuleSettingsRegistry.getModuleSettings("livetracking")
        settings.forEach { setting ->
            handleSettingChange(module, setting.name, setting.defaultValue)
        }
    }
}
