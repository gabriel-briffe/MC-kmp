package org.mountaincircles.app.modules.circles.logic.business

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.modules.circles.logic.data.PackConfig

/**
 * Circles Business Service
 * Handles core business logic for circles operations
 */
class CirclesBusinessService(private val module: CirclesModule) {

    /**
     * Rescan installed packs
     */
    suspend fun rescanPacks() {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Rescanning installed packs")
        module.businessController.rescanPacks()
    }

    /**
     * Update circles visibility
     */
    suspend fun updateCirclesVisibility(isVisible: Boolean) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating circles visibility: $isVisible")
        module.businessController.updateCirclesVisibility(isVisible)
    }

    /**
     * Update sectors opacity
     */
    suspend fun updateSectorsOpacity(opacity: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating sectors opacity: $opacity")
        module.businessController.updateSectorsOpacity(opacity)
    }

    /**
     * Update airfields visibility
     */
    suspend fun updateAirfieldsVisibility(isVisible: Boolean) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating airfields visibility: $isVisible")
        module.businessController.updateAirfieldsVisibility(isVisible)
    }

    /**
     * Update airfield radius
     */
    suspend fun updateAirfieldRadius(radius: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating airfield radius: $radius")
        module.businessController.updateAirfieldRadius(radius)
    }

    /**
     * Update label offset
     */
    suspend fun updateLabelOffset(offset: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating label offset: $offset")
        module.businessController.updateLabelOffset(offset)
    }

    /**
     * Update circles label size
     */
    suspend fun updateCirclesLabelSize(size: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating circles label size: $size")
        module.businessController.updateCirclesLabelSize(size)
    }

    /**
     * Update circles label spacing
     */
    suspend fun updateCirclesLabelSpacing(spacing: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating circles label spacing: $spacing")
        module.businessController.updateCirclesLabelSpacing(spacing)
    }

    /**
     * Update airfield label size
     */
    suspend fun updateAirfieldLabelSize(size: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating airfield label size: $size")
        module.businessController.updateAirfieldLabelSize(size)
    }

    /**
     * Update airfield click size
     */
    suspend fun updateAirfieldClickSize(size: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating airfield click size: $size")
        module.businessController.updateAirfieldClickSize(size)
    }

    /**
     * Update circles line width
     */
    suspend fun updateCirclesLineWidth(width: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating circles line width: $width")
        module.businessController.updateCirclesLineWidth(width)
    }

    /**
     * Update airfield icon size
     */
    suspend fun updateAirfieldIconSize(size: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating airfield icon size: $size")
        module.businessController.updateAirfieldIconSize(size)
    }

    /**
     * Update circles min zoom
     */
    suspend fun updateCirclesMinZoom(minZoom: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating circles min zoom: $minZoom")
        module.businessController.updateCirclesMinZoom(minZoom)
    }

    /**
     * Update circle labels min zoom
     */
    suspend fun updateCircleLabelsMinZoom(minZoom: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating circle labels min zoom: $minZoom")
        module.businessController.updateCircleLabelsMinZoom(minZoom)
    }

    /**
     * Update airfield icons min zoom
     */
    suspend fun updateAirfieldIconsMinZoom(minZoom: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating airfield icons min zoom: $minZoom")
        module.businessController.updateAirfieldIconsMinZoom(minZoom)
    }

    /**
     * Update airfield labels min zoom
     */
    suspend fun updateAirfieldLabelsMinZoom(minZoom: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating airfield labels min zoom: $minZoom")
        module.businessController.updateAirfieldLabelsMinZoom(minZoom)
    }

    /**
     * Update sectors min zoom
     */
    suspend fun updateSectorsMinZoom(minZoom: Float) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Updating sectors min zoom: $minZoom")
        module.businessController.updateSectorsMinZoom(minZoom)
    }

    /**
     * Select a specific pack and configuration
     */
    suspend fun selectPackConfig(packId: String, configId: String) {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Selecting pack config: $packId/$configId")
        module.businessController.selectPackConfig(packId, configId)
    }

    /**
     * Show visual feedback for click areas
     */
    fun showClickAreaFeedback() {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Showing click area feedback")
        module.businessController.showClickAreaFeedback()
    }

    /**
     * Get available pack configurations
     */
    fun getAvailablePackConfigs(): List<PackConfig> {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Getting available pack configs")
        return module.businessController.getAvailablePackConfigs()
    }

    /**
     * Check if pack is installed
     */
    suspend fun isPackInstalled(packId: String, configId: String): Boolean {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Checking if pack is installed: $packId/$configId")
        return module.businessController.isPackInstalled(packId, configId)
    }

    /**
     * Delete a pack
     */
    suspend fun deletePack(packId: String, configId: String): Boolean {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Deleting pack: $packId/$configId")
        return module.businessController.deletePack(packId, configId)
    }

    /**
     * Toggle visibility
     */
    suspend fun toggleVisibility() {
        Logger.log("CIRCLES", LogLevel.DEBUG, "Toggling circles visibility")
        module.businessController.toggleVisibility()
    }

}
