package org.mountaincircles.app.modules.maps.logic.initialization

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.maps.MapsModule
import org.mountaincircles.app.modules.maps.getInstalledMapFileIds

/**
 * Maps Module Initialization
 * Extracted initialization logic for better separation of concerns
 */

/**
 * Initialize the maps module - load settings and prepare for direct MBTiles access
 */
suspend fun MapsModule.initializeMaps() {
    // Settings will be loaded through the new generic persistence system

    // Get installed maps
    val installedMaps = getInstalledMapFileIds()

    // Direct MBTiles access - no server needed
    Logger.log("MAPS", LogLevel.INFO, "Maps module ready for direct MBTiles access")

    // Update module state to reflect successful initialization
    updateState { currentState.copy(
        isInitialized = true,
        installedMaps = installedMaps
    ) }

    Logger.log("MAPS", LogLevel.INFO, "Maps module initialized with ${installedMaps.size} installed maps: $installedMaps")
}
