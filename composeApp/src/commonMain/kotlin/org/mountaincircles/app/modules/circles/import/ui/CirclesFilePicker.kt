package org.mountaincircles.app.modules.circles.import.ui

/**
 * Expected interface for file picker functionality
 * Platform-specific implementations handle file selection for circles zip import
 */
expect class CirclesFilePicker() {
    /**
     * Launch file picker to select a zip file for circles import
     * Returns true if a file was selected and import was attempted, false otherwise
     */
    suspend fun launchZipImport(onResult: suspend (Boolean) -> Unit)
}
