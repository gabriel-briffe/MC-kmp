package org.mountaincircles.app.modules.wave.logic.controllers

import org.mountaincircles.app.modules.wave.logic.data.WaveEntry

/**
 * Wave file management and navigation logic
 *
 * Handles scanning MBTiles files and implementing time/altitude navigation
 */
expect class WaveManager() {
    /**
     * Scan wave directory for MBTiles files and parse filenames
     */
    suspend fun scan(): List<WaveEntry>

    /**
     * Get wave directory path
     */
    fun getWaveDirectory(): String

    /**
     * Check if a file exists
     */
    suspend fun fileExists(path: String): Boolean

    /**
     * Delete a specific wave file
     */
    suspend fun deleteFile(path: String): Boolean

    /**
     * Delete all wave files
     */
    suspend fun clearAllFiles(): Int

    /**
     * Get file size in bytes
     */
    fun getFileSize(path: String): Long
}