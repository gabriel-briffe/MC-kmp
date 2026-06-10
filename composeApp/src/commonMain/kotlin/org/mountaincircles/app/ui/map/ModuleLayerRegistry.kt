package org.mountaincircles.app.ui.map

import androidx.compose.runtime.Composable
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.modules.circles.CirclesModule

/**
 * Registry for module-specific map layer rendering
 *
 * All modules now use the unified LayerManager system for consistent layer management,
 * priority handling, and lifecycle management.
 */
object ModuleLayerRegistry {

    /**
     * Render map layers for a given module using the unified LayerManager system
     *
     * All modules are now migrated to use LayerManager, providing:
     * - Consistent layer priority management
     * - Proper lifecycle handling
     * - Unified click interaction system
     * - Better performance and debugging
     */
    @Composable
    fun RenderModuleLayers(module: ModuleBase) {
        when (module) {
            is WaveModule -> {
                Logger.log("LAYER_REGISTRY", LogLevel.DEBUG,
                    "Rendering WaveModule with LayerManager system")
                // LayerManager handles rendering through LayerManagerComposables
            }
            is CirclesModule -> {
                Logger.log("LAYER_REGISTRY", LogLevel.DEBUG,
                    "Rendering CirclesModule with LayerManager system")
                // LayerManager handles rendering through LayerManagerComposables
            }
            // Add new module layer renderers here:
            /*
            is AirspaceModule -> {
                // Future modules will also use LayerManager system
                Logger.log("LAYER_REGISTRY", LogLevel.DEBUG,
                    "Rendering AirspaceModule with LayerManager system")
            }
            is ContourModule -> {
                // Future modules will also use LayerManager system
                Logger.log("LAYER_REGISTRY", LogLevel.DEBUG,
                    "Rendering ContourModule with LayerManager system")
            }
            */
            else -> {
                // Unknown module type - this is fine, just skip it
                Logger.log("LAYER_REGISTRY", LogLevel.DEBUG,
                    "No layer renderer available for module: ${module.moduleId}")
            }
        }
    }
    
    /**
     * Check if a module has map layers available
     *
     * All migrated modules use the LayerManager system and have layers registered
     * through their respective LayerManager implementations.
     */
    fun hasMapLayers(module: ModuleBase): Boolean {
        return when (module) {
            is WaveModule -> {
                Logger.log("LAYER_REGISTRY", LogLevel.DEBUG,
                    "WaveModule has LayerManager layers")
                true
            }
            is CirclesModule -> {
                Logger.log("LAYER_REGISTRY", LogLevel.DEBUG,
                    "CirclesModule has LayerManager layers")
                true
            }
            // Add checks for future modules here
            else -> {
                Logger.log("LAYER_REGISTRY", LogLevel.DEBUG,
                    "Module ${module.moduleId} has no registered layers")
                false
            }
        }
    }
}
