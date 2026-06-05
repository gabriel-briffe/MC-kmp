package org.mountaincircles.app.ui.overlay

import androidx.compose.runtime.Composable
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Registry for map overlay providers from modules
 */
object MapOverlayRegistry {
    private val overlays = mutableMapOf<String, OverlayProvider>()

    fun register(moduleId: String, provider: OverlayProvider) {
        val previousProvider = overlays[moduleId]
        overlays[moduleId] = provider

        Logger.log("OVERLAY_REGISTRY", LogLevel.INFO, "📍 Registered overlay provider for module: $moduleId")
        Logger.log("OVERLAY_REGISTRY", LogLevel.DEBUG, "   Position: ${provider.position}")
        Logger.log("OVERLAY_REGISTRY", LogLevel.DEBUG, "   Priority: ${provider.priority}")
        Logger.log("OVERLAY_REGISTRY", LogLevel.DEBUG, "   Provider class: ${provider::class.simpleName}")
        Logger.log("OVERLAY_REGISTRY", LogLevel.DEBUG, "   Replaced existing: ${previousProvider != null}")

        Logger.log("OVERLAY_REGISTRY", LogLevel.DEBUG, "   Total registered overlays: ${overlays.size}")
    }

    fun unregister(moduleId: String) {
        val existed = overlays.containsKey(moduleId)
        overlays.remove(moduleId)
        Logger.log("OVERLAY_REGISTRY", LogLevel.INFO, "📍 Unregistered overlay provider for module: $moduleId (existed: $existed)")
        Logger.log("OVERLAY_REGISTRY", LogLevel.DEBUG, "   Remaining registered overlays: ${overlays.size}")
    }

    fun getOverlay(moduleId: String): OverlayProvider? {
        val provider = overlays[moduleId]
        Logger.log("OVERLAY_REGISTRY", LogLevel.DEBUG, "📍 Requested overlay for module: $moduleId -> ${provider?.let { "Found (${it::class.simpleName})" } ?: "Not found"}")
        return provider
    }

    fun getAllOverlays(): List<OverlayProvider> {
        Logger.log("OVERLAY_REGISTRY", LogLevel.DEBUG, "📍 Requested all overlays (${overlays.size} total)")
        return overlays.values.toList()
    }

    fun getOverlaysSortedByPriority(): List<OverlayProvider> {
        val sorted = overlays.values.sortedByDescending { it.priority }
        Logger.log("OVERLAY_REGISTRY", LogLevel.DEBUG, "📍 Requested overlays sorted by priority:")
        sorted.forEachIndexed { index, provider ->
            Logger.log("OVERLAY_REGISTRY", LogLevel.DEBUG, "   ${index + 1}. ${provider.moduleId} (priority: ${provider.priority}, position: ${provider.position})")
        }
        return sorted
    }
}
