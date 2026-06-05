package org.mountaincircles.app.modules.airports.import.ui

import org.mountaincircles.app.modules.airports.AirportsModule

/**
 * Expected interface for CUP file picker functionality
 * Platform-specific implementations handle file selection for CUP outlanding file import
 */
expect class CupFilePicker() {
    /**
     * Launch file picker to just save a .cup file to filesystem (no processing)
     * Returns true if a file was selected and saved, false otherwise
     */
    suspend fun launchCupSave(module: AirportsModule, onResult: suspend (Boolean) -> Unit)
}
