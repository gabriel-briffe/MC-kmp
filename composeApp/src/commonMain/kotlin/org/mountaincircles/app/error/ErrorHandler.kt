package org.mountaincircles.app.error

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Centralized error handling for the Mountain Circles application.
 * Provides consistent error processing, logging, and user feedback patterns.
 */
object ErrorHandler {

    /**
     * Handle an application error with appropriate logging and user feedback
     */
    fun handle(error: AppError, context: String = "Unknown") {
        // Log the error with appropriate level
        val logLevel = when (error.getSeverity()) {
            ErrorSeverity.INFO -> LogLevel.INFO
            ErrorSeverity.WARNING -> LogLevel.WARN
            ErrorSeverity.ERROR -> LogLevel.ERROR
            ErrorSeverity.CRITICAL -> LogLevel.ERROR
        }

        Logger.log("ErrorHandler", logLevel, "[${error::class.simpleName}] $context: ${error.getUserMessage()}")

        // Log additional details for debugging - extract cause from error types that have it
        val cause = when (error) {
            is AppError.NetworkError -> error.cause
            is AppError.DataParseError -> error.cause
            is AppError.FileSystemError -> error.cause
            is AppError.PersistenceError -> error.cause
            is AppError.ModuleError -> error.cause
            is AppError.ModuleInitError -> error.cause
            is AppError.SystemError -> error.cause
            is AppError.UnknownError -> error.cause
            else -> null
        }
        cause?.let {
            Logger.log("ErrorHandler", LogLevel.DEBUG, "[${error::class.simpleName}] Cause: ${it.message}", it)
        }

        // Add context-specific fields for better debugging
        when (error) {
            is AppError.HttpError -> Logger.log("ErrorHandler", LogLevel.DEBUG, "[HttpError] URL: ${error.url}, Status: ${error.statusCode}")
            is AppError.FileSystemError -> Logger.log("ErrorHandler", LogLevel.DEBUG, "[FileSystemError] Path: ${error.path}")
            is AppError.ModuleError, is AppError.ModuleInitError -> Logger.log("ErrorHandler", LogLevel.DEBUG, "[ModuleError] Module: ${(error as? AppError.ModuleError)?.moduleId ?: (error as? AppError.ModuleInitError)?.moduleId}")
            else -> {} // No additional context for other error types
        }
    }

    /**
     * Handle an exception by converting it to an AppError and processing it
     */
    fun handleException(exception: Throwable, context: String = "Unknown") {
        val appError = classifyException(exception)
        handle(appError, context)
    }

    /**
     * Classify a generic exception into an appropriate AppError type
     */
    fun classifyException(exception: Throwable): AppError {
        return when (exception) {
            // Network-related exceptions
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.SocketException -> AppError.NetworkError("Connection failed", exception)

            is java.net.SocketTimeoutException,
            is kotlinx.coroutines.TimeoutCancellationException -> AppError.TimeoutError("Operation timed out", 30000L) // Default 30s timeout

            // File system exceptions
            is java.io.FileNotFoundException -> AppError.FileSystemError("File not found", exception.message, exception)
            is java.io.IOException -> AppError.FileSystemError("File operation failed", exception.message, exception)

            // Serialization exceptions
            is kotlinx.serialization.SerializationException,
            is kotlinx.serialization.MissingFieldException -> AppError.DataParseError("Data format error", exception.message, exception)

            // Permission exceptions
            is java.security.AccessControlException -> AppError.PermissionError("permission", "Access denied")

            // Memory exceptions
            is OutOfMemoryError -> AppError.MemoryError("Out of memory", null)

            // Module-specific exceptions (check for module context in message)
            else -> {
                val message = exception.message ?: "Unknown error"
                if (message.contains("module", ignoreCase = true)) {
                    AppError.ModuleError("unknown", message, exception)
                } else {
                    AppError.UnknownError(message, exception)
                }
            }
        }
    }

    /**
     * Create a user-friendly error message for display
     */
    fun getDisplayMessage(error: AppError): String {
        return error.getUserMessage()
    }

    /**
     * Get recovery suggestion for user action
     */
    fun getRecoverySuggestion(error: AppError): String {
        return error.getRecoverySuggestion()
    }

    /**
     * Check if an error should trigger user feedback (toast, dialog, etc.)
     */
    fun shouldShowUserFeedback(error: AppError): Boolean {
        return error.getSeverity() >= ErrorSeverity.WARNING
    }

    /**
     * Check if an error should be reported to analytics/crash reporting
     */
    fun shouldReportToAnalytics(error: AppError): Boolean {
        return error.getSeverity() >= ErrorSeverity.ERROR
    }

    /**
     * Handle async operation errors with Result<T> pattern
     */
    suspend fun <T> handleResult(
        operation: suspend () -> Result<T>,
        context: String = "Async operation"
    ): Result<T> {
        return try {
            operation()
        } catch (e: Throwable) {
            handleException(e, context)
            Result.failure(e)
        }
    }
}
