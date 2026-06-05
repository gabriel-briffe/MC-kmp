package org.mountaincircles.app.modules.circles.ui

import org.mountaincircles.app.modules.circles.CirclesModule

/**
 * Calculator for circles pack availability and file counts
 * Extracts common logic from ViewModel to reduce duplication and improve testability
 */
class CirclesPackAvailabilityCalculator(private val circlesModule: CirclesModule) {

    /**
     * Get list of available pack IDs
     */
    fun getAvailablePacks(): List<String> = circlesModule.circlesState.value.availableConfigs
        .map { "${it.packId}_${it.configId}" }

    /**
     * Get list of installed pack IDs
     */
    fun getInstalledPacks(): List<String> = circlesModule.circlesState.value.installedPacks

    /**
     * Check if a pack is available
     */
    fun isPackAvailable(packId: String, configId: String): Boolean {
        val fullPackId = "${packId}_${configId}"
        return circlesModule.circlesState.value.availableConfigs
            .any { it.packId == packId && it.configId == "${configId}-4210" }
    }

    /**
     * Check if a pack is installed
     */
    fun isPackInstalled(packId: String, configId: String): Boolean {
        val fullPackId = "${packId}_${configId}"
        return circlesModule.circlesState.value.installedPacks.contains(fullPackId)
    }

    /**
     * Get active pack ID
     */
    fun getActivePackId(): String? = circlesModule.circlesState.value.activeConfig
        ?.let { "${it.packId}_${it.configId}" }
}
