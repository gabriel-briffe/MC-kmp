package org.mountaincircles.app.modules.maps.logic.services

/**
 * Utility for resolving MBTiles file paths across platforms
 */
class MBTilesPathResolver {

    companion object {
        /**
         * Get the full file path for an MBTiles file
         */
        fun getMBTilesFilePath(mapId: String): String {
            return getPlatformMBTilesFilePath(mapId)
        }
    }
}

/**
 * Platform-specific MBTiles file path resolution
 */
expect fun getPlatformMBTilesFilePath(mapId: String): String
