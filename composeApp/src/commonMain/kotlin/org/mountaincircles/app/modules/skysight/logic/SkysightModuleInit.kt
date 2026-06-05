package org.mountaincircles.app.modules.skysight.logic

import kotlinx.serialization.json.Json
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.data.LayerLegend
import org.mountaincircles.app.modules.skysight.logic.data.SkysightLayer

/**
 * Init and load helpers for Skysight (persistence, available layers).
 * Extracted from SkysightModule for clearer separation of concerns.
 */

suspend fun loadAvailableLayersFromPersistence(module: SkysightModule): List<SkysightLayer> {
    return try {
        val persistedData = module.settingPersistence.getString("availableLayers", "") ?: ""

        if (persistedData.isEmpty()) {
            return emptyList()
        }

        try {
            val layers = Json.decodeFromString<List<SkysightLayer>>(persistedData)
            Logger.log("SKYSIGHT", LogLevel.DEBUG, "Loaded ${layers.size} layers from JSON persistence")
            return layers
        } catch (e: Exception) {
            Logger.log("SKYSIGHT", LogLevel.DEBUG, "Failed to parse as JSON, trying legacy format: ${e.message}")
        }

        val layerNames = persistedData.split(",").filter { it.isNotBlank() }
        if (layerNames.isNotEmpty()) {
            Logger.log("SKYSIGHT", LogLevel.DEBUG, "Loaded ${layerNames.size} layer names from legacy persistence")
            return layerNames.map { name ->
                SkysightLayer(
                    name = name.trim(),
                    legend = LayerLegend(
                        color_mode = "",
                        units = "",
                        unit_type = "",
                        units_scale_factor = 1,
                        colors = emptyList()
                    ),
                    projection = "",
                    data_type = "",
                    id = "",
                    description = ""
                )
            }
        }

        emptyList()
    } catch (e: Exception) {
        Logger.log("SKYSIGHT", LogLevel.ERROR, "Failed to load available layers from persistence: ${e.message}", e)
        emptyList()
    }
}

suspend fun loadAvailableLayers(module: SkysightModule, region: String) {
    val result = module.getApiManager().loadAvailableLayers(module, region)
    when {
        result.isSuccess -> Logger.log("SKYSIGHT", LogLevel.DEBUG, "Layer loading completed successfully")
        result.isFailure -> Logger.log("SKYSIGHT", LogLevel.ERROR, "Layer loading failed: ${result.exceptionOrNull()?.message}")
    }
}
