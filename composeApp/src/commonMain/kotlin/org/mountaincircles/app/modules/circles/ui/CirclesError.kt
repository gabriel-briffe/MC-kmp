package org.mountaincircles.app.modules.circles.ui

/**
 * Sealed class for circles-specific error types
 */
sealed class CirclesError {
    abstract val message: String
    abstract val userMessage: String

    data class NetworkError(
        override val message: String,
        val url: String? = null
    ) : CirclesError() {
        override val userMessage: String = "Network error: Unable to download circle pack data. Please check your internet connection."
    }

    data class FileSystemError(
        override val message: String,
        val filePath: String? = null
    ) : CirclesError() {
        override val userMessage: String = "File system error: Unable to save circle pack data. Please check storage permissions."
    }

    data class ValidationError(
        override val message: String,
        val invalidData: String? = null
    ) : CirclesError() {
        override val userMessage: String = "Data validation error: The downloaded circle pack data appears to be corrupted."
    }

    data class PackError(
        override val message: String,
        val packId: String? = null
    ) : CirclesError() {
        override val userMessage: String = "Pack error: Unable to process the selected circle pack."
    }

    data class ModuleError(
        override val message: String,
        val operation: String? = null
    ) : CirclesError() {
        override val userMessage: String = "Module error: An internal error occurred while processing circle data."
    }

    data class UnknownError(
        override val message: String
    ) : CirclesError() {
        override val userMessage: String = "An unexpected error occurred. Please try again."
    }
}
