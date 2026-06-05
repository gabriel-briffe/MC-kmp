package org.mountaincircles.app.modules.circles.import.logic

import org.mountaincircles.app.modules.circles.logic.data.PackMetadata

/**
 * Expected class for circles file management
 * Platform-specific implementations handle file I/O for circles packs
 */
expect class CirclesManager() {
    /**
     * Get the circles data directory path
     */
    fun getCirclesDirectory(): String
    
    /**
     * Check if a file exists
     */
    fun fileExists(filePath: String): Boolean
    
    /**
     * Get list of installed circles packs
     */
    suspend fun getInstalledPacks(): List<String>
    
    /**
     * Get list of available configurations for a pack
     */
    suspend fun getPackConfigurations(packId: String): List<String>
    
    /**
     * Delete a circles pack and all its files
     */
    suspend fun deletePack(packId: String, configId: String): Boolean
    
    /**
     * Get the total number of files for a pack configuration
     */
    suspend fun getPackConfigFileCount(packId: String, configId: String): Int
    
    /**
     * Check if a specific pack configuration is completely installed
     */
    suspend fun isPackConfigInstalled(packId: String, configId: String): Boolean
    
    /**
     * Clear all circles files
     */
    suspend fun clearAllFiles(): Int
    
    /**
     * Read GeoJSON file content
     */
    suspend fun readGeoJsonFile(filePath: String): String?
    
    /**
     * Read metadata for a pack configuration
     */
    suspend fun readPackMetadata(packId: String, configId: String): PackMetadata?
    
    /**
     * Get list of available configurations for a pack
     */
    suspend fun getAvailableConfigurations(packId: String): List<String>

    /**
     * Unzip a circles pack to the circles directory (cross-platform ByteArray version)
     */
    suspend fun unzipToCirclesDir(fileBytes: ByteArray): Boolean
}
