package org.mountaincircles.app.error

/**
 * Standardized error classification for the Mountain Circles application.
 * Provides type-safe error handling and consistent user feedback patterns.
 */
sealed class AppError {

    // ============================================================================
    // NETWORK ERRORS
    // ============================================================================

    /** Network connectivity issues */
    data class NetworkError(val message: String, val cause: Throwable? = null) : AppError()

    /** HTTP errors with status codes */
    data class HttpError(val statusCode: Int, val message: String, val url: String? = null) : AppError()

    /** Timeout errors */
    data class TimeoutError(val operation: String, val timeoutMs: Long) : AppError()

    // ============================================================================
    // DATA ERRORS
    // ============================================================================

    /** Data parsing/serialization errors */
    data class DataParseError(val message: String, val data: String? = null, val cause: Throwable? = null) : AppError()

    /** File system errors */
    data class FileSystemError(val message: String, val path: String? = null, val cause: Throwable? = null) : AppError()

    /** Database/persistence errors */
    data class PersistenceError(val message: String, val operation: String, val cause: Throwable? = null) : AppError()

    /** Data validation errors */
    data class ValidationError(val message: String, val field: String? = null) : AppError()

    // ============================================================================
    // UI ERRORS
    // ============================================================================

    /** UI state errors */
    data class UiStateError(val message: String, val component: String) : AppError()

    /** User input errors */
    data class UserInputError(val message: String, val field: String? = null) : AppError()

    // ============================================================================
    // MODULE ERRORS
    // ============================================================================

    /** Module-specific errors */
    data class ModuleError(val moduleId: String, val message: String, val cause: Throwable? = null) : AppError()

    /** Module initialization errors */
    data class ModuleInitError(val moduleId: String, val message: String, val cause: Throwable? = null) : AppError()

    // ============================================================================
    // SYSTEM ERRORS
    // ============================================================================

    /** Permission denied errors */
    data class PermissionError(val permission: String, val message: String) : AppError()

    /** Out of memory errors */
    data class MemoryError(val message: String, val requestedBytes: Long? = null) : AppError()

    /** Unexpected system errors */
    data class SystemError(val message: String, val cause: Throwable? = null) : AppError()

    /** Unknown/unclassified errors */
    data class UnknownError(val message: String, val cause: Throwable? = null) : AppError()

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    /**
     * Get user-friendly error message for display
     */
    fun getUserMessage(): String = when (this) {
        is NetworkError -> "Network connection error: $message"
        is HttpError -> "Server error (${statusCode}): $message"
        is TimeoutError -> "Operation timed out: $operation"
        is DataParseError -> "Data format error: $message"
        is FileSystemError -> "File access error: $message"
        is PersistenceError -> "Data storage error: $message"
        is ValidationError -> "Invalid data: $message"
        is UiStateError -> "Display error in $component: $message"
        is UserInputError -> "Input error: $message"
        is ModuleError -> "Module error ($moduleId): $message"
        is ModuleInitError -> "Module initialization failed ($moduleId): $message"
        is PermissionError -> "Permission required: $message"
        is MemoryError -> "Insufficient memory: $message"
        is SystemError -> "System error: $message"
        is UnknownError -> "An unexpected error occurred: $message"
    }

    /**
     * Get error severity level for logging and handling
     */
    fun getSeverity(): ErrorSeverity = when (this) {
        is NetworkError, is HttpError, is TimeoutError -> ErrorSeverity.WARNING
        is DataParseError, is FileSystemError, is ValidationError -> ErrorSeverity.WARNING
        is UiStateError, is UserInputError -> ErrorSeverity.INFO
        is ModuleError, is ModuleInitError -> ErrorSeverity.ERROR
        is PermissionError, is PersistenceError -> ErrorSeverity.ERROR
        is MemoryError, is SystemError, is UnknownError -> ErrorSeverity.CRITICAL
    }

    /**
     * Check if error is recoverable by user action
     */
    fun isRecoverable(): Boolean = when (this) {
        is NetworkError, is TimeoutError -> true  // Retry connection
        is PermissionError -> true  // Grant permission
        is UserInputError, is ValidationError -> true  // Fix input
        is HttpError -> statusCode in 400..499  // Client errors are recoverable
        else -> false  // System/server errors typically not recoverable
    }

    /**
     * Get recovery suggestion for user
     */
    fun getRecoverySuggestion(): String = when (this) {
        is NetworkError -> "Check your internet connection and try again"
        is HttpError -> if (statusCode in 500..599) "Server is temporarily unavailable. Please try again later" else "Please check your request and try again"
        is TimeoutError -> "The operation took too long. Please try again"
        is PermissionError -> "Please grant the required permission and try again"
        is UserInputError, is ValidationError -> "Please check your input and try again"
        is FileSystemError -> "Please check storage space and permissions"
        is DataParseError -> "Please try refreshing the data"
        else -> "Please restart the application or contact support if the problem persists"
    }
}

/**
 * Error severity levels for consistent handling
 */
enum class ErrorSeverity {
    INFO,      // User notification only
    WARNING,   // User notification + logging
    ERROR,     // User feedback + error logging + potential recovery
    CRITICAL   // User feedback + error logging + app stability impact
}
