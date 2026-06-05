package org.mountaincircles.app.modules.wave.ui

/**
 * Sealed class for wave-specific error types
 * Provides better error categorization and handling
 */
sealed class WaveError {
    abstract val message: String
    abstract val userMessage: String

    data class NetworkError(
        override val message: String,
        val url: String? = null
    ) : WaveError() {
        override val userMessage: String = "Network error: Unable to download forecast data. Please check your internet connection."
    }

    data class FileSystemError(
        override val message: String,
        val filePath: String? = null
    ) : WaveError() {
        override val userMessage: String = "File system error: Unable to save forecast data. Please check storage permissions."
    }

    data class ValidationError(
        override val message: String,
        val invalidData: String? = null
    ) : WaveError() {
        override val userMessage: String = "Data validation error: The downloaded forecast data appears to be corrupted."
    }

    data class ModuleError(
        override val message: String,
        val operation: String? = null
    ) : WaveError() {
        override val userMessage: String = "Module error: An internal error occurred while processing forecast data."
    }

    data class UnknownError(
        override val message: String
    ) : WaveError() {
        override val userMessage: String = "An unexpected error occurred. Please try again."
    }
}
