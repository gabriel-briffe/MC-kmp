package org.mountaincircles.app.modules.airspace.ui

import org.mountaincircles.app.error.AppError

/**
 * Sealed class representing specific error types for the Airspace module.
 * Provides structured error information for UI display and logging.
 */
sealed class AirspaceError {
    abstract val userMessage: String

    data class NetworkError(
        val message: String,
        val cause: Throwable? = null
    ) : AirspaceError() {
        override val userMessage: String = "Network issue: $message"
    }

    data class FileSystemError(
        val message: String,
        val cause: Throwable? = null
    ) : AirspaceError() {
        override val userMessage: String = "File system error: $message"
    }

    data class ValidationError(
        val message: String,
        val cause: Throwable? = null
    ) : AirspaceError() {
        override val userMessage: String = "Data validation failed: $message"
    }

    data class ImportError(
        val message: String,
        val countries: List<String>,
        val cause: Throwable? = null
    ) : AirspaceError() {
        override val userMessage: String = "Airspace import failed for countries ${countries.joinToString()}: $message"
    }

    data class ClearError(
        val message: String,
        val cause: Throwable? = null
    ) : AirspaceError() {
        override val userMessage: String = "Failed to clear airspace data: $message"
    }

    data class CountrySelectionError(
        val message: String,
        val cause: Throwable? = null
    ) : AirspaceError() {
        override val userMessage: String = "Country selection error: $message"
    }

    data class ModuleError(
        val message: String,
        val operation: String,
        val cause: Throwable? = null
    ) : AirspaceError() {
        override val userMessage: String = "Airspace module error during $operation: $message"
    }

    data class UnknownError(
        val message: String,
        val cause: Throwable? = null
    ) : AirspaceError() {
        override val userMessage: String = "An unexpected error occurred: $message"
    }

    fun toAppError(): AppError {
        return when (this) {
            is NetworkError -> AppError.NetworkError(userMessage, cause)
            is FileSystemError -> AppError.FileSystemError(userMessage, null, cause)
            is ValidationError -> AppError.ValidationError(userMessage)
            is ImportError -> AppError.ModuleError("airspace", userMessage, cause)
            is ClearError -> AppError.PersistenceError(userMessage, "clear", cause)
            is CountrySelectionError -> AppError.UserInputError(userMessage)
            is ModuleError -> AppError.ModuleError("airspace", userMessage, cause)
            is UnknownError -> AppError.UnknownError(userMessage, cause)
        }
    }
}
