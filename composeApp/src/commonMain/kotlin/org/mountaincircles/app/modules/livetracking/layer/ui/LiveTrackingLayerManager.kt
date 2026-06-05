package org.mountaincircles.app.modules.livetracking.layer.ui

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.ui.map.LayerRegistrationHelper
import org.mountaincircles.app.ui.map.LayerZIndex
import org.mountaincircles.app.modules.livetracking.LiveTrackingModule
import org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingVisibilityMode

/**
 * LiveTracking module LayerManager implementation
 */
class LiveTrackingLayerManager(private val module: LiveTrackingModule) {

    private val layerIds = mutableListOf<String>()
    val layerIdsPublic: List<String> get() = layerIds

    /**
     * Initialize and register all live tracking layers with the LayerManager
     */
    fun initializeLayers() {
        Logger.log("LIVETRACKING_LAYERS", LogLevel.INFO,
            "Initializing live tracking layers with LayerManager")

        // Register the aircraft icons layer
        val aircraftLayerId = LayerRegistrationHelper.registerLayer(
            moduleId = "livetracking",
            layerName = "aircraft_positions",
            zIndex = 10 * LayerZIndex.getZIndex("livetracking_aircraft"),
            layerType = org.mountaincircles.app.ui.map.LayerDescriptor.LayerType.OVERLAY,
            isInteractive = true,
            description = "Live aircraft tracking icons",
            tags = setOf("livetracking", "aircraft", "overlay", "tracking", "icons"),
            composable = { LiveTrackingAircraftIconsLayer(module) }
        )

        // Register the aircraft labels layer
        val labelsLayerId = LayerRegistrationHelper.registerLayer(
            moduleId = "livetracking",
            layerName = "aircraft_labels",
            zIndex = 10 * LayerZIndex.getZIndex("livetracking_aircraft_labels"),
            layerType = org.mountaincircles.app.ui.map.LayerDescriptor.LayerType.OVERLAY,
            isInteractive = false,
            description = "Live aircraft tracking labels",
            tags = setOf("livetracking", "aircraft", "overlay", "tracking", "labels"),
            composable = { LiveTrackingAircraftLabelsLayer(module) }
        )

        layerIds.add(aircraftLayerId)
        layerIds.add(labelsLayerId)

        Logger.log("LIVETRACKING_LAYERS", LogLevel.INFO,
            "Registered ${layerIds.size} live tracking layers with LayerManager")

        // Set initial visibility
        updateLayerVisibility()
    }

    /**
     * Update layer visibility based on module state
     */
    fun updateLayerVisibility() {
        val visible = when (module.currentState.visibilityMode) {
            LiveTrackingVisibilityMode.ALL_VISIBLE, LiveTrackingVisibilityMode.FRIENDS_ONLY -> true
            LiveTrackingVisibilityMode.ALL_HIDDEN -> false
        }

        layerIds.forEach { layerId ->
            LayerRegistrationHelper.layerManager.setLayerVisibility(layerId, visible)
        }

        Logger.log("LIVETRACKING_LAYERS", LogLevel.DEBUG,
            "Updated layer visibility: $visible for ${layerIds.size} layers")
    }

    /**
     * Clean up layers when module is destroyed
     */
    fun cleanup() {
        Logger.log("LIVETRACKING_LAYERS", LogLevel.INFO,
            "Cleaning up ${layerIds.size} live tracking layers")

        layerIds.forEach { layerId ->
            LayerRegistrationHelper.layerManager.unregisterLayer(layerId)
        }

        layerIds.clear()
    }
}
