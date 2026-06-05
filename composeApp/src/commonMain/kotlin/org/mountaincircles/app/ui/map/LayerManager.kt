package org.mountaincircles.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Centralized manager for map layers with index-based ordering and click handling
 */
class LayerManager {

    // Internal state of all registered layers
    private val _layers = MutableStateFlow<List<LayerDescriptor>>(emptyList())
    val layers: StateFlow<List<LayerDescriptor>> = _layers.asStateFlow()

    // Layer composables registry
    private val layerComposables = mutableMapOf<String, @Composable () -> Unit>()

    // Stable layer registrations registry (for performance optimization)
    private val stableLayerRegistrations = mutableMapOf<String, LayerRegistrationHelper.StableLayerRegistration>()

    // Click handler registry
    private val clickHandlers = mutableMapOf<String, (MapClickEvent) -> Boolean>()

    // Modules that control their own re-rendering
    private val controlledReRenderModules = mutableSetOf<String>()

    // Pending layer registrations for controlled modules
    private val pendingLayers = mutableMapOf<String, Pair<LayerDescriptor, @Composable () -> Unit>>()

    // Pending layer removals for controlled modules
    private val pendingRemovals = mutableSetOf<String>()

    /**
     * Check if a layer is already registered
     */
    fun isLayerRegistered(layerId: String): Boolean {
        return layerComposables.containsKey(layerId)
    }

    /**
     * Register a module as controlling its own re-rendering
     */
    fun registerControlledReRenderModule(moduleId: String) {
        controlledReRenderModules.add(moduleId)
        Logger.log("LAYER_MANAGER", LogLevel.INFO, "Module $moduleId registered for controlled re-rendering")
    }

    /**
     * Trigger batch update for a controlled module
     */
    fun triggerBatchUpdate(moduleId: String) {
        Logger.log("LAYER_MANAGER", LogLevel.INFO, "=== TRIGGER BATCH UPDATE START: $moduleId ===")

        if (!controlledReRenderModules.contains(moduleId)) {
            Logger.log("LAYER_MANAGER", LogLevel.WARN, "Module $moduleId is not registered for controlled re-rendering")
            Logger.log("LAYER_MANAGER", LogLevel.INFO, "=== TRIGGER BATCH UPDATE END (not controlled): $moduleId ===")
            return
        }

        // Apply all pending layer registrations and removals for this module
        val pendingForModule = pendingLayers.filterKeys { it.startsWith("${moduleId}_") }
        val removalsForModule = pendingRemovals.filter { it.startsWith("${moduleId}_") }

        Logger.log("LAYER_MANAGER", LogLevel.DEBUG, "Total pending layers: ${pendingLayers.size}, total pending removals: ${pendingRemovals.size}")
        Logger.log("LAYER_MANAGER", LogLevel.DEBUG, "For module $moduleId: ${pendingForModule.size} pending registrations, ${removalsForModule.size} pending removals")

        if (pendingForModule.isNotEmpty() || removalsForModule.isNotEmpty()) {
            Logger.log("LAYER_MANAGER", LogLevel.INFO, "Applying ${pendingForModule.size} pending registrations and ${removalsForModule.size} pending removals for module $moduleId")

            if (moduleId == "skysight") {
                Logger.log("LAYER_MANAGER_DEBUG", LogLevel.INFO, "=== SKYSIGHT BATCH UPDATE DETAILS ===")
                Logger.log("LAYER_MANAGER_DEBUG", LogLevel.INFO, "Pending registrations: ${pendingForModule.keys.joinToString(", ")}")
                Logger.log("LAYER_MANAGER_DEBUG", LogLevel.INFO, "Pending removals: ${removalsForModule.joinToString(", ")}")
            }

            // Start with current layers
            var updatedLayers = _layers.value

            // Apply removals first
            if (removalsForModule.isNotEmpty()) {
                Logger.log("LAYER_MANAGER", LogLevel.DEBUG, "Removing ${removalsForModule.size} layers: ${removalsForModule.joinToString()}")
                updatedLayers = updatedLayers.filter { it.id !in removalsForModule }

                // Clean up composables and handlers for removed layers
                removalsForModule.forEach { layerId ->
                    layerComposables.remove(layerId)
                    stableLayerRegistrations.remove(layerId)
                    clickHandlers.remove(layerId)
                }

                // Clear pending removals for this module
                pendingRemovals.removeAll(removalsForModule)
            }

            // Apply additions
            if (pendingForModule.isNotEmpty()) {
                Logger.log("LAYER_MANAGER", LogLevel.DEBUG, "Adding ${pendingForModule.size} layers: ${pendingForModule.keys.joinToString()}")
                val newLayers = pendingForModule.values.map { it.first }
                updatedLayers = (updatedLayers + newLayers).sortedBy { it.renderPriority }

                // Register composables
                pendingForModule.forEach { (layerId, pair) ->
                    layerComposables[layerId] = pair.second
                }

                // Clear pending layers for this module
                pendingLayers.keys.removeAll(pendingForModule.keys)
            }

            // Update the StateFlow once with all changes
            _layers.value = updatedLayers

            Logger.log("LAYER_MANAGER", LogLevel.INFO, "Batch update completed for module $moduleId. Total layers: ${updatedLayers.size}")

            // Debug: Show all layers in RENDERING ORDER after batch update
            Logger.log("LAYER_RENDERING_ZZZ", LogLevel.DEBUG, "=== RENDERING ORDER AFTER BATCH UPDATE ===")
            updatedLayers.forEachIndexed { index, layer ->
                Logger.log("LAYER_RENDERING_ZZZ", LogLevel.DEBUG, "[$index] ${layer.id} (z-index: ${layer.renderPriority}, visible: ${layer.isVisible})")
            }
            Logger.log("LAYER_RENDERING_ZZZ", LogLevel.DEBUG, "=== END RENDERING ORDER ===\n")

            // Summary: Show layer count by module
            val moduleCounts = updatedLayers.groupBy { layer ->
                layer.moduleId
            }.mapValues { it.value.size }

            Logger.log("LAYER_SUMMARY", LogLevel.INFO, "📊 BATCH UPDATE SUMMARY:")
            Logger.log("LAYER_SUMMARY", LogLevel.INFO, "Total layers registered: ${updatedLayers.size}")
            moduleCounts.forEach { (moduleId, count) ->
                Logger.log("LAYER_SUMMARY", LogLevel.INFO, "  $moduleId: $count layers")
            }
        } else {
            Logger.log("LAYER_MANAGER", LogLevel.DEBUG, "No pending layers to apply for module $moduleId")
        }

        Logger.log("LAYER_MANAGER", LogLevel.INFO, "=== TRIGGER BATCH UPDATE END: $moduleId ===")
    }

    /**
     * Register a new layer with its composable
     */
    fun registerLayer(
        descriptor: LayerDescriptor,
        composable: @Composable () -> Unit
    ) {
        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "Registering layer: ${descriptor.id} (render: ${descriptor.renderPriority}, visible: ${descriptor.isVisible})")

        // Check if module controls its own re-rendering
        if (controlledReRenderModules.contains(descriptor.moduleId)) {
            // Store in pending layers for batch update
            pendingLayers[descriptor.id] = Pair(descriptor, composable)
            Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
                "Layer ${descriptor.id} stored in pending layers for controlled module ${descriptor.moduleId}")

            // Show pending layers summary
            Logger.log("LAYER_SUMMARY", LogLevel.INFO, "📋 PENDING LAYER REGISTRATION SUMMARY:")
            Logger.log("LAYER_SUMMARY", LogLevel.INFO, "Total pending layers: ${pendingLayers.size}")
            val pendingModuleCounts = pendingLayers.values.groupBy { it.first.moduleId }.mapValues { it.value.size }
            pendingModuleCounts.forEach { (moduleId, count) ->
                Logger.log("LAYER_SUMMARY", LogLevel.INFO, "  $moduleId: $count pending layers")
            }

            return
        }

        // Normal registration for modules that don't control re-rendering
        // Add to internal state
        val currentLayers = _layers.value
        val updatedLayers = (currentLayers + descriptor).sortedBy { it.renderPriority }
        _layers.value = updatedLayers

        // Store composable
        layerComposables[descriptor.id] = composable

        Logger.log("LAYER_MANAGER", LogLevel.INFO,
            "Layer registered: ${descriptor.id}. Total layers: ${updatedLayers.size}")

        // Debug: Show all layers in RENDERING ORDER (sorted by z-index)
        Logger.log("LAYER_RENDERING_ZZZ", LogLevel.DEBUG, "=== RENDERING ORDER AFTER REGISTRATION ===")
        updatedLayers.forEachIndexed { index, layer ->
            Logger.log("LAYER_RENDERING_ZZZ", LogLevel.DEBUG, "[$index] ${layer.id} (z-index: ${layer.renderPriority}, visible: ${layer.isVisible})")
        }
        Logger.log("LAYER_RENDERING_ZZZ", LogLevel.DEBUG, "=== END RENDERING ORDER ===\n")

        // Summary: Show layer count by module
        val moduleCounts = updatedLayers.groupBy { layer ->
            layer.moduleId
        }.mapValues { it.value.size }

        Logger.log("LAYER_SUMMARY", LogLevel.INFO, "📊 LAYER REGISTRATION SUMMARY:")
        Logger.log("LAYER_SUMMARY", LogLevel.INFO, "Total layers registered: ${updatedLayers.size}")
        moduleCounts.forEach { (moduleId, count) ->
            Logger.log("LAYER_SUMMARY", LogLevel.INFO, "  $moduleId: $count layers")
        }

        // Show the final rendering order (bottom to top)
        Logger.log("LAYER_SUMMARY", LogLevel.INFO, "\n🎯 FINAL RENDERING ORDER (bottom to top):")
        updatedLayers.forEachIndexed { index, layer ->
            val marker = when (layer.id) {
                "wave_wave_raster" -> " 🌊"  // Wave layer
                "airspace_airspace_boundaries" -> " ✈️"  // Airspace layer
                else -> ""
            }
            Logger.log("LAYER_SUMMARY", LogLevel.INFO, "  $index: ${layer.id}$marker")
        }
    }

    /**
     * Register a new stable layer with pre-computed values
     * This eliminates StateFlow collection at the composable level for better performance
     */
    fun registerStableLayer(registration: LayerRegistrationHelper.StableLayerRegistration) {
        val descriptor = registration.layerDescriptor

        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "Registering stable layer: ${descriptor.id} (render: ${descriptor.renderPriority}, visible: ${descriptor.isVisible})")

        // Add to internal state (same as regular layer registration)
        val currentLayers = _layers.value
        val updatedLayers = (currentLayers + descriptor).sortedBy { it.renderPriority }
        _layers.value = updatedLayers

        // Store stable registration (instead of regular composable)
        stableLayerRegistrations[descriptor.id] = registration

        Logger.log("LAYER_MANAGER", LogLevel.INFO,
            "Stable layer registered: ${descriptor.id}. Total layers: ${updatedLayers.size}")

        // Debug: Show stable layer registration
        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "Stable layer registration stored for: ${descriptor.id} (hasStableValues: ${registration.stableValues != null})")
    }

    /**
     * Get stable layer registration for a specific layer ID
     */
    fun getStableLayerRegistration(layerId: String): LayerRegistrationHelper.StableLayerRegistration? {
        return stableLayerRegistrations[layerId]
    }

    /**
     * Register a click handler for a layer
     */
    fun registerClickHandler(
        layerId: String,
        handler: (MapClickEvent) -> Boolean
    ) {
        clickHandlers[layerId] = handler
        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "Click handler registered for layer: $layerId")
    }

    /**
     * Unregister a layer
     */
    fun unregisterLayer(layerId: String) {
        Logger.log("LAYER_MANAGER", LogLevel.DEBUG, "Unregistering layer: $layerId")

        // Extract module ID from layer ID (format: moduleId_layerName)
        val moduleId = layerId.substringBefore("_", "")

        // Check if module controls its own re-rendering
        if (controlledReRenderModules.contains(moduleId)) {
            // Defer removal for controlled modules
            pendingRemovals.add(layerId)
            Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
                "Layer $layerId queued for deferred removal (controlled module: $moduleId)")

            // Show pending removals summary
            Logger.log("LAYER_SUMMARY", LogLevel.INFO, "📋 PENDING LAYER REMOVAL SUMMARY:")
            Logger.log("LAYER_SUMMARY", LogLevel.INFO, "Total pending removals: ${pendingRemovals.size}")
            val pendingRemovalModuleCounts = pendingRemovals.groupBy { it.substringBefore("_") }.mapValues { it.value.size }
            pendingRemovalModuleCounts.forEach { (moduleId, count) ->
                Logger.log("LAYER_SUMMARY", LogLevel.INFO, "  $moduleId: $count pending removals")
            }

            return
        }

        // Immediate removal for non-controlled modules
        val currentLayers = _layers.value
        val updatedLayers = currentLayers.filter { it.id != layerId }
        _layers.value = updatedLayers

        layerComposables.remove(layerId)
        stableLayerRegistrations.remove(layerId)
        clickHandlers.remove(layerId)

        Logger.log("LAYER_MANAGER", LogLevel.INFO,
            "Layer unregistered: $layerId. Remaining layers: ${updatedLayers.size}")
    }

    /**
     * Update layer visibility
     */
    fun setLayerVisibility(layerId: String, visible: Boolean) {
        val currentLayers = _layers.value
        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "setLayerVisibility: $layerId -> $visible (before: ${currentLayers.find { it.id == layerId }?.isVisible})")

        val updatedLayers = currentLayers.map { layer ->
            if (layer.id == layerId) {
                val updatedLayer = layer.withVisibility(visible)
                Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
                    "Updated layer $layerId: isVisible ${layer.isVisible} -> ${updatedLayer.isVisible}")
                updatedLayer
            } else layer
        }.sortedBy { it.renderPriority }

        _layers.value = updatedLayers

        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "Layer visibility updated: $layerId -> $visible")
        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "All layers after visibility change: ${updatedLayers.map { "${it.id}(visible=${it.isVisible})" }}")
    }


    /**
     * Get layers sorted by render priority (for rendering)
     */
    fun getRenderOrderedLayers(): List<LayerDescriptor> {
        val allLayers = _layers.value
        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "getRenderOrderedLayers called with ${allLayers.size} total layers")
        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "All layers in _layers: ${allLayers.map { "${it.id}(visible=${it.isVisible})" }}")

        val renderableLayers = allLayers.filter { layer ->
            val shouldRender = layer.shouldRender()
            Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
                "Layer ${layer.id}: shouldRender=${shouldRender}, isVisible=${layer.isVisible}")
            shouldRender
        }

        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "Filtered to ${renderableLayers.size} renderable layers: ${renderableLayers.map { it.id }}")

        val sortedLayers = renderableLayers.sortedBy { it.renderPriority }
        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "Sorted render layers: ${sortedLayers.map { "${it.id}(${it.renderPriority})" }}")

        return sortedLayers
    }


    /**
     * Get renderable layers data for use by composables
     * This replaces the old @Composable RenderLayers() method
     */
    fun getRenderLayersData(): List<LayerDescriptor> {
        val allLayers = _layers.value
        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "getRenderLayersData called with ${allLayers.size} total layers")

        val renderableLayers = allLayers.filter { layer ->
            val shouldRender = layer.shouldRender()
            Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
                "Layer ${layer.id}: shouldRender=${shouldRender}, isVisible=${layer.isVisible}")
            shouldRender
        }

        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "Filtered to ${renderableLayers.size} renderable layers: ${renderableLayers.map { it.id }}")

        val sortedLayers = renderableLayers.sortedBy { it.renderPriority }
        Logger.log("LAYER_MANAGER", LogLevel.DEBUG,
            "Sorted render layers: ${sortedLayers.map { "${it.id}(${it.renderPriority})" }}")

        return sortedLayers
    }

    /**
     * Get composable function for a specific layer ID
     * Used by standalone composable functions
     */
    fun getLayerComposable(layerId: String): (@Composable () -> Unit)? {
        return layerComposables[layerId]
    }

    /**
     * Get layer statistics for debugging
     */
    fun getLayerStats(): LayerStats {
        val allLayers = _layers.value
        return LayerStats(
            totalLayers = allLayers.size,
            visibleLayers = allLayers.count { it.isVisible },
            interactiveLayers = allLayers.count { it.isInteractive },
            renderPriorities = allLayers.map { it.renderPriority }.distinct().sorted()
        )
    }

    /**
     * Statistics about current layer state
     */
    data class LayerStats(
        val totalLayers: Int,
        val visibleLayers: Int,
        val interactiveLayers: Int,
        val renderPriorities: List<Int>
    )

    companion object {
        // Singleton instance for global access
        val instance = LayerManager().also {
            Logger.log("LAYER_MANAGER", LogLevel.INFO,
                "LayerManager singleton created")
        }
    }
}

/**
 * Map click event data
 */
data class MapClickEvent(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val screenX: Float,
    val screenY: Float,
    val modifiers: Set<ClickModifier> = emptySet()
) {
    enum class ClickModifier {
        SHIFT, CTRL, ALT, LONG_PRESS
    }
}
