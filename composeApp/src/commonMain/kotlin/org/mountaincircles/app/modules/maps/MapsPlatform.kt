package org.mountaincircles.app.modules.maps

/**
 * Platform-specific file operations for map downloads
 */
expect suspend fun getInstalledMapFileIds(): List<String>
expect suspend fun checkMapFileExists(mapId: String): Boolean
expect suspend fun deleteAllMapFiles(): Int
expect suspend fun deleteMapFile(mapId: String): Boolean
expect suspend fun getMapFilePath(mapId: String): String
