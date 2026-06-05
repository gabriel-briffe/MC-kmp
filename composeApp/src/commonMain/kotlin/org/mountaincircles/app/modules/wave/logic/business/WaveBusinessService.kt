package org.mountaincircles.app.modules.wave.logic.business

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.modules.wave.logic.data.WaveSelection
import org.mountaincircles.app.modules.wave.logic.data.WaveEntry
import org.mountaincircles.app.modules.wave.logic.data.NavigationData

/**
 * Wave Business Service
 * Handles core business logic for wave operations
 */
class WaveBusinessService(private val module: WaveModule) {

    /**
     * Updates the wave opacity
     */
    suspend fun updateOpacity(opacity: Float) {
        module.updateState { it.copy(opacity = opacity) }
        // Save settings after opacity change
        Logger.log("WAVE", LogLevel.DEBUG, "Wave opacity updated: $opacity")
    }

    /**
     * Updates the wave selection with navigation capabilities calculation
     */
    suspend fun updateSelection(newSelection: WaveSelection) {
        val currentState = module.moduleState.value
        val updatedSelection = module.waveLogic.updateSelectionWithPath(newSelection, currentState.entries)
        val capabilities = module.waveLogic.calculateNavigationCapabilities(updatedSelection, currentState.entries)

        module.updateState { state ->
            state.copy(
                selection = updatedSelection,
                canPrevHour = capabilities.canPrevHour,
                canNextHour = capabilities.canNextHour,
                canPressureUp = capabilities.canPressureUp,
                canPressureDown = capabilities.canPressureDown
            )
        }

        // Save settings after selection change
        Logger.log("WAVE", LogLevel.DEBUG, "Wave selection updated: $updatedSelection")
    }

    /**
     * Updates the main label font size
     */
    suspend fun updateMainLabelFontSize(fontSize: Float) {
        module.updateState { it.copy(mainLabelFontSize = fontSize) }
        // Save settings after font size change
        Logger.log("WAVE", LogLevel.DEBUG, "Wave main label font size updated: ${fontSize}sp")
    }

    /**
     * Updates the sub label font size
     */
    suspend fun updateSubLabelFontSize(fontSize: Float) {
        module.updateState { it.copy(subLabelFontSize = fontSize) }
        // Save settings after font size change
        Logger.log("WAVE", LogLevel.DEBUG, "Wave sub label font size updated: ${fontSize}sp")
    }

    /**
     * Sets the wave entries
     */
    fun setEntries(entries: List<WaveEntry>) {
        module.updateState { it.copy(entries = entries) }
        Logger.log("WAVE", LogLevel.DEBUG, "Wave entries updated: ${entries.size} entries")
    }


    /**
     * Updates the wave progress
     */
    fun updateProgress(progress: org.mountaincircles.app.modules.wave.logic.data.WaveProgress?) {
        module.updateState { state ->
            state.copy(
                currentProgress = progress,
                isDownloading = progress != null
            )
        }
        Logger.log("WAVE", LogLevel.DEBUG, "Wave progress updated: $progress")
    }

    /**
     * Clears the wave progress
     */
    fun clearProgress() {
        module.updateState { state ->
            state.copy(
                currentProgress = null,
                isDownloading = false
            )
        }
        module.updateState { module.currentState.copy(importProgress = org.mountaincircles.app.modules.wave.logic.data.ImportProgressState.Idle) }
        Logger.log("WAVE", LogLevel.DEBUG, "Wave progress cleared")
    }
}
