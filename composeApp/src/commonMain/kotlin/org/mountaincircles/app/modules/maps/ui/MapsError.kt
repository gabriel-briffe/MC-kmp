package org.mountaincircles.app.modules.maps.ui

import org.mountaincircles.app.error.AppError

/**
 * Sealed class representing specific error types for the Maps module.
 * Provides structured error information for UI display and logging.
 */
sealed class MapsError {
    abstract val userMessage: String

    data class NetworkError(
        val message: String,
        val cause: Throwable? = null
    ) : MapsError() {
        override val userMessage: String = "Network issue: $message"
    }

    data class FileSystemError(
        val message: String,
        val cause: Throwable? = null
    ) : MapsError() {
        override val userMessage: String = "File system error: $message"
    }

    data class ValidationError(
        val message: String,
        val cause: Throwable? = null
    ) : MapsError() {
        override val userMessage: String = "Data validation failed: $message"
    }

    data class DownloadError(
        val message: String,
        val mapId: String,
        val cause: Throwable? = null
    ) : MapsError() {
        override val userMessage: String = "Download failed for $mapId: $message"
    }

    data class DeleteError(
        val message: String,
        val mapId: String,
        val cause: Throwable? = null
    ) : MapsError() {
        override val userMessage: String = "Delete failed for $mapId: $message"
    }

    data class ModuleError(
        val message: String,
        val operation: String,
        val cause: Throwable? = null
    ) : MapsError() {
        override val userMessage: String = "Maps module error during $operation: $message"
    }

    data class UnknownError(
        val message: String,
        val cause: Throwable? = null
    ) : MapsError() {
        override val userMessage: String = "An unexpected error occurred: $message"
    }

    fun toAppError(): AppError {
        return when (this) {
            is NetworkError -> AppError.NetworkError(userMessage, cause)
            is FileSystemError -> AppError.FileSystemError(userMessage, null, cause)
            is ValidationError -> AppError.ValidationError(userMessage)
            is DownloadError -> AppError.ModuleError("maps", userMessage, cause)
            is DeleteError -> AppError.ModuleError("maps", userMessage, cause)
            is ModuleError -> AppError.ModuleError("maps", userMessage, cause)
            is UnknownError -> AppError.UnknownError(userMessage, cause)
        }
    }
}
