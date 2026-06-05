package org.mountaincircles.app.modules.wave.logic.controllers

import kotlinx.coroutines.flow.MutableStateFlow
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.wave.import.logic.WaveImportType
import org.mountaincircles.app.modules.wave.import.logic.WaveImporter
import org.mountaincircles.app.modules.wave.logic.data.ImportProgressState
import org.mountaincircles.app.modules.wave.logic.data.WaveProgress
import org.mountaincircles.app.modules.wave.logic.data.WaveSelection

/**
 * Wave Import Controller - Circles Pattern
 * Handles all import logic for the Wave module
 * Works with WaveModule's direct state management
 */
class WaveImportController(private val waveModule: org.mountaincircles.app.modules.wave.WaveModule) {

    private val waveLogic = WaveLogic()
    private val waveImporter = WaveImporter()
    private val waveManager = WaveManager()

    /**
     * Import wave data based on import type
     */
    suspend fun importWaves(importType: WaveImportType, includeWindFiles: Boolean = false, selectedWindRegions: Set<org.mountaincircles.app.modules.wave.ui.WindRegion> = emptySet()) {
        Logger.log("WAVE", LogLevel.INFO, "Starting wave import: $importType")

        try {
            // Update state to indicate download started
            waveModule.updateState { it.copy(isDownloading = true) }

            // Use the real WaveImporter with progress callback
            waveImporter.import(
                importType = importType,
                waveManager = waveManager,
                onProgress = { progress ->
                    waveModule.updateProgress(progress)
                },
                includeWindFiles = includeWindFiles,
                selectedWindRegions = selectedWindRegions
            )

            // After import, scan for the new entries
            val importedEntries = waveManager.scan()

            // Find initial selection for the imported data
            val initialSelection = if (importedEntries.isNotEmpty()) {
                waveLogic.findInitialSelection(importedEntries)
            } else {
                null
            }

            // Update state with imported data (entries first)
            waveModule.updateState { state ->
                state.copy(
                    entries = importedEntries,
                    isDownloading = false,
                    currentProgress = null,
                    isInitialized = true,
                    hasError = false,
                    errorMessage = null
                )
            }

            // Now update selection with proper navigation capabilities calculation
            if (initialSelection != null) {
                waveModule.updateSelection(initialSelection)
            }

            Logger.log("WAVE", LogLevel.INFO, "Wave import completed: ${importedEntries.size} entries")

        } catch (e: Exception) {
            Logger.log("WAVE", LogLevel.ERROR, "Wave import failed: ${e.message}", e)

            // Update state to indicate error
            waveModule.updateState { state ->
                state.copy(
                    isDownloading = false,
                    currentProgress = null,
                    hasError = true,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * Clear all wave files
     */
    suspend fun clearAllFiles() {
        Logger.log("WAVE", LogLevel.INFO, "Clearing all wave files")

        try {
            // Use the real WaveManager to clear files
            val deletedCount = waveManager.clearAllFiles()

            // Update state after clearing
            waveModule.updateState { state ->
                state.copy(
                    entries = emptyList(),
                    selection = WaveSelection("", "", 12, 500)
                )
            }

            Logger.log("WAVE", LogLevel.INFO, "Cleared $deletedCount wave files")

        } catch (e: Exception) {
            Logger.log("WAVE", LogLevel.ERROR, "Failed to clear wave files: ${e.message}", e)
        }
    }

    /**
     * Rescan for existing wave files
     */
    suspend fun rescanFiles() {
        Logger.log("WAVE", LogLevel.INFO, "Rescanning for wave files")

        try {
            // Use the real WaveManager to scan for files
            val existingEntries = waveManager.scan()

            waveModule.updateState { state ->
                state.copy(
                    entries = existingEntries,
                    isInitialized = true,
                    hasError = false,
                    errorMessage = null
                )
            }

            Logger.log("WAVE", LogLevel.INFO, "Found ${existingEntries.size} existing wave entries")

        } catch (e: Exception) {
            Logger.log("WAVE", LogLevel.ERROR, "Failed to rescan wave files: ${e.message}", e)
        }
    }
}