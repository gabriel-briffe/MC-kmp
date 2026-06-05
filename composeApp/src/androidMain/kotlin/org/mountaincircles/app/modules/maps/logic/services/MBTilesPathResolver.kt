package org.mountaincircles.app.modules.maps.logic.services

import org.mountaincircles.app.modules.maps.getMapsDirectory
import java.io.File

/**
 * Android implementation for MBTiles file path resolution
 */
actual fun getPlatformMBTilesFilePath(mapId: String): String {
    val mapsDir = getMapsDirectory()
    return File(mapsDir, "$mapId.mbtiles").absolutePath
}
