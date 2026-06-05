package org.mountaincircles.app.utils

import org.mountaincircles.app.error.AppError
import org.mountaincircles.app.error.ErrorHandler
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Common error handling utilities for ViewModels
 * Extracts duplicated error handling logic between modules
 */
object ErrorHandlerUtils {

    /**
     * Common error categorization and handling
     * Returns the categorized error for UI state updates
     */
    fun handleModuleError(
        message: String,
        exception: Exception,
        operation: String,
        moduleName: String
    ): org.mountaincircles.app.modules.wave.ui.WaveError? {
        // This is a generic implementation - modules can override with specific error types
        val waveError = when {
            operation.contains("download", ignoreCase = true) &&
            (message.contains("network", ignoreCase = true) || message.contains("connect", ignoreCase = true)) ->
                org.mountaincircles.app.modules.wave.ui.WaveError.NetworkError(message)

            operation.contains("clear", ignoreCase = true) || operation.contains("file", ignoreCase = true) ->
                org.mountaincircles.app.modules.wave.ui.WaveError.FileSystemError(message)

            message.contains("validation", ignoreCase = true) || message.contains("corrupt", ignoreCase = true) ->
                org.mountaincircles.app.modules.wave.ui.WaveError.ValidationError(message)

            else -> org.mountaincircles.app.modules.wave.ui.WaveError.ModuleError(message, operation)
        }

        // Log to the existing error system
        val appError = AppError.ModuleError(moduleName, message, exception)
        ErrorHandler.handle(appError, "$moduleName.$operation")

        return waveError
    }

    /**
     * Handle Circles module errors specifically
     */
    fun handleCirclesError(
        message: String,
        exception: Exception,
        operation: String
    ): org.mountaincircles.app.modules.circles.ui.CirclesError {
        val circlesError = when {
            operation.contains("download", ignoreCase = true) &&
            (message.contains("network", ignoreCase = true) || message.contains("connect", ignoreCase = true)) ->
                org.mountaincircles.app.modules.circles.ui.CirclesError.NetworkError(message)

            operation.contains("delete", ignoreCase = true) || operation.contains("file", ignoreCase = true) ->
                org.mountaincircles.app.modules.circles.ui.CirclesError.FileSystemError(message)

            message.contains("validation", ignoreCase = true) || message.contains("corrupt", ignoreCase = true) ->
                org.mountaincircles.app.modules.circles.ui.CirclesError.ValidationError(message)

            operation.contains("select", ignoreCase = true) ->
                org.mountaincircles.app.modules.circles.ui.CirclesError.PackError(message, operation)

            else -> org.mountaincircles.app.modules.circles.ui.CirclesError.ModuleError(message, operation)
        }

        // Log to the existing error system
        val appError = AppError.ModuleError("circles", message, exception)
        ErrorHandler.handle(appError, "CirclesImportViewModel.$operation")

        return circlesError
    }

    /**
     * Handle Wave module errors specifically
     */
    fun handleWaveError(
        message: String,
        exception: Exception,
        operation: String
    ): org.mountaincircles.app.modules.wave.ui.WaveError {
        val waveError = when {
            operation.contains("download", ignoreCase = true) &&
            (message.contains("network", ignoreCase = true) || message.contains("connect", ignoreCase = true)) ->
                org.mountaincircles.app.modules.wave.ui.WaveError.NetworkError(message)

            operation.contains("clear", ignoreCase = true) || operation.contains("file", ignoreCase = true) ->
                org.mountaincircles.app.modules.wave.ui.WaveError.FileSystemError(message)

            message.contains("validation", ignoreCase = true) || message.contains("corrupt", ignoreCase = true) ->
                org.mountaincircles.app.modules.wave.ui.WaveError.ValidationError(message)

            else -> org.mountaincircles.app.modules.wave.ui.WaveError.ModuleError(message, operation)
        }

        // Log to the existing error system
        val appError = AppError.ModuleError("wave", message, exception)
        ErrorHandler.handle(appError, "WaveImportViewModel.$operation")

        return waveError
    }
}
