package org.mountaincircles.app.modules.circles.logic.controllers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.logic.data.PackConfig
import org.mountaincircles.app.modules.circles.logic.data.CirclesState
import org.mountaincircles.app.modules.circles.import.logic.CirclesManager
import org.mountaincircles.app.modules.circles.layer.ui.CirclesLayerManager

/**
 * Circles Business Controller
 * Handles all business logic operations for the circles module
 * Requires access to state, managers, and settings persistence
 */
class CirclesBusinessController(
    private val circlesState: StateFlow<CirclesState>,
    private val stateUpdater: ((CirclesState) -> CirclesState) -> Unit,
    private val circlesManager: CirclesManager,
    private val layerManager: CirclesLayerManager?,
    private var preferredPackId: String?,
    private var preferredConfigId: String?
) {

    /**
     * Rescan installed packs
     */
    suspend fun rescanPacks() {
        try {
            Logger.log("CIRCLES", LogLevel.DEBUG, "Rescanning installed circles packs")
            val packs = circlesManager.getInstalledPacks()

            // Build list of all available configurations
            val availableConfigs = mutableListOf<PackConfig>()
            for (packId in packs) {
                val configs = circlesManager.getAvailableConfigurations(packId)
                for (configId in configs) {
                    val metadata = circlesManager.readPackMetadata(packId, configId)
                    // Build folder name from metadata (policy_prefix) or fallback
                    val folderName = if (metadata != null) {
                        "${metadata.policy}_${metadata.prefix}"
                    } else {
                        // Fallback: use packId_configPrefix
                        val prefix = configId.split("-").dropLast(1).joinToString("-")
                        "${packId}_${prefix}"
                    }
                    availableConfigs.add(PackConfig(packId, configId, folderName, metadata))
                }
            }

            // Auto-select active config with preference order:
            // 1. Preferred pack from settings (for settings restoration)
            // 2. Current active config (for existing state)
            // 3. First available config (for new installs)
            val currentState = circlesState.value
            val newActiveConfig = when {
                // First priority: Restore preferred config from settings if available
                preferredPackId != null && preferredConfigId != null -> {
                    val preferred = availableConfigs.find {
                        it.packId == preferredPackId && it.configId == preferredConfigId
                    }
                    if (preferred != null) {
                        Logger.log("CIRCLES", LogLevel.INFO, "Restored preferred config from settings: ${preferred.packId}/${preferred.configId}")
                        // Clear preferences after restoration
                        this.preferredPackId = null
                        this.preferredConfigId = null
                        preferred
                    } else {
                        // Fallback to current or first available
                        when {
                            currentState.activeConfig != null && availableConfigs.any {
                                it.packId == currentState.activeConfig.packId && it.configId == currentState.activeConfig.configId
                            } -> currentState.activeConfig
                            availableConfigs.isNotEmpty() -> availableConfigs.first()
                            else -> null
                        }
                    }
                }
                // Second priority: Keep current if still available
                currentState.activeConfig != null && availableConfigs.any {
                    it.packId == currentState.activeConfig.packId && it.configId == currentState.activeConfig.configId
                } -> currentState.activeConfig
                // Third priority: Select first available
                availableConfigs.isNotEmpty() -> availableConfigs.first()
                // No configs available
                else -> null
            }

            // Update state with new configs and active config
            stateUpdater { it.copy(
                installedPacks = packs,
                availableConfigs = availableConfigs,
                activeConfig = newActiveConfig
            ) }

            // Update layer manager with new selection if changed
            if (newActiveConfig != null && newActiveConfig != currentState.activeConfig) {
                layerManager?.resetToMainLayer()
                layerManager?.updateSelection(newActiveConfig)
            }

            Logger.log("CIRCLES", LogLevel.INFO, "Rescanned ${packs.size} packs, found ${availableConfigs.size} configs, active: ${newActiveConfig?.packId ?: "none"}")

        } catch (e: Exception) {
            Logger.log("CIRCLES", LogLevel.ERROR, "Failed to rescan packs: ${e.message}", e)
        }
    }

    /**
     * Update visibility
     */
    suspend fun updateCirclesVisibility(isVisible: Boolean) {
        stateUpdater { it.copy(circlesVisibility = isVisible) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updated circles visibility: $isVisible")
    }

    /**
     * Update sectors opacity
     */
    suspend fun updateSectorsOpacity(opacity: Float) {
        stateUpdater { it.copy(sectorsOpacity = opacity) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Sectors opacity updated: $opacity")
    }

    /**
     * Update airfields visibility
     */
    suspend fun updateAirfieldsVisibility(isVisible: Boolean) {
        stateUpdater { it.copy(airfieldsVisibility = isVisible) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updated airfields visibility: $isVisible")
    }

    /**
     * Update airfield radius
     */
    suspend fun updateAirfieldRadius(radius: Float) {
        stateUpdater { it.copy(airfieldRadius = radius) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Airfield radius updated: $radius")
    }

    /**
     * Update label offset
     */
    suspend fun updateLabelOffset(offset: Float) {
        stateUpdater { it.copy(labelOffset = offset) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Label offset updated: $offset")
    }

    /**
     * Update circles label size
     */
    suspend fun updateCirclesLabelSize(size: Float) {
        stateUpdater { it.copy(circlesLabelSize = size) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Circles label size updated: $size")
    }

    /**
     * Update circles label spacing
     */
    suspend fun updateCirclesLabelSpacing(spacing: Float) {
        stateUpdater { it.copy(circlesLabelSpacing = spacing) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Circles label spacing updated: $spacing")
    }

    /**
     * Update airfield label size
     */
    suspend fun updateAirfieldLabelSize(size: Float) {
        stateUpdater { it.copy(airfieldLabelSize = size) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Airfield label size updated: $size")
    }

    /**
     * Update airfield click size
     */
    suspend fun updateAirfieldClickSize(size: Float) {
        stateUpdater { it.copy(airfieldClickSize = size) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Airfield click size updated: $size")
    }

    /**
     * Update circles line width
     */
    suspend fun updateCirclesLineWidth(width: Float) {
        stateUpdater { it.copy(circlesLineWidth = width) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Circles line width updated: $width")
    }

    /**
     * Update airfield icon size
     */
    suspend fun updateAirfieldIconSize(size: Float) {
        stateUpdater { it.copy(airfieldIconSize = size) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Airfield icon size updated: $size")
    }

    /**
     * Update circles min zoom
     */
    suspend fun updateCirclesMinZoom(minZoom: Float) {
        stateUpdater { it.copy(circlesMinZoom = minZoom) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Circles min zoom updated: $minZoom")
    }

    /**
     * Update circle labels min zoom
     */
    suspend fun updateCircleLabelsMinZoom(minZoom: Float) {
        stateUpdater { it.copy(circleLabelsMinZoom = minZoom) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Circle labels min zoom updated: $minZoom")
    }

    /**
     * Update airfield icons min zoom
     */
    suspend fun updateAirfieldIconsMinZoom(minZoom: Float) {
        stateUpdater { it.copy(airfieldIconsMinZoom = minZoom) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Airfield icons min zoom updated: $minZoom")
    }

    /**
     * Update airfield labels min zoom
     */
    suspend fun updateAirfieldLabelsMinZoom(minZoom: Float) {
        stateUpdater { it.copy(airfieldLabelsMinZoom = minZoom) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Airfield labels min zoom updated: $minZoom")
    }

    /**
     * Update sectors min zoom
     */
    suspend fun updateSectorsMinZoom(minZoom: Float) {
        stateUpdater { it.copy(sectorsMinZoom = minZoom) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Sectors min zoom updated: $minZoom")
    }

    /**
     * Select a specific pack and configuration
     */
    suspend fun selectPackConfig(packId: String, configId: String) {
        val currentState = circlesState.value
        val targetConfig = currentState.availableConfigs.find {
            it.packId == packId && it.configId == configId
        }

        if (targetConfig != null) {
            stateUpdater { it.copy(activeConfig = targetConfig) }

            // Reset to main linestring layer when selecting a new pack
            layerManager?.resetToMainLayer()

            // Update layer manager with new config
            layerManager?.updateSelection(targetConfig)

            Logger.log("CIRCLES", LogLevel.INFO, "Selected pack config: $packId/$configId - reset to main layer")
        } else {
            Logger.log("CIRCLES", LogLevel.WARN, "Pack config not found: $packId/$configId")
        }
    }

    /**
     * Show visual feedback for click areas by temporarily making them visible at 50% opacity
     * for 2 seconds when the click size setting is changed
     */
    fun showClickAreaFeedback() {
        layerManager?.showClickAreaFeedback()
        Logger.log("CIRCLES", LogLevel.DEBUG, "Showing click area feedback")
    }

    /**
     * Get available pack configurations
     */
    fun getAvailablePackConfigs(): List<PackConfig> {
        return circlesState.value.availableConfigs
    }

    /**
     * Toggle visibility
     */
    suspend fun toggleVisibility() {
        val newVisibility = !circlesState.value.circlesVisibility
        stateUpdater { it.copy(circlesVisibility = newVisibility) }
        Logger.log("CIRCLES", LogLevel.DEBUG, "Circles visibility toggled: $newVisibility")
    }

    /**
     * Check if pack is installed
     */
    suspend fun isPackInstalled(packId: String, configId: String): Boolean {
        return circlesManager.isPackConfigInstalled(packId, configId)
    }

    /**
     * Delete a pack
     */
    suspend fun deletePack(packId: String, configId: String): Boolean {
        try {
            val currentState = circlesState.value

            // Check if this is the currently active pack
            val isActivePack = currentState.activeConfig?.packId == packId

            // Delete the pack files
            val success = circlesManager.deletePack(packId, configId)

            if (success) {
                Logger.log("CIRCLES", LogLevel.INFO, "Deleted pack: $packId/$configId")

                // If this was the active pack, clear the active config
                if (isActivePack) {
                    stateUpdater { it.copy(activeConfig = null) }
                    layerManager?.resetToMainLayer()
                    Logger.log("CIRCLES", LogLevel.INFO, "Cleared active config after deleting active pack")
                }

                // Rescan to update available configs
                rescanPacks()

                return true
            } else {
                Logger.log("CIRCLES", LogLevel.WARN, "Failed to delete pack: $packId/$configId")
                return false
            }
        } catch (e: Exception) {
            Logger.log("CIRCLES", LogLevel.ERROR, "Error deleting pack $packId/$configId: ${e.message}", e)
            return false
        }
    }
}
