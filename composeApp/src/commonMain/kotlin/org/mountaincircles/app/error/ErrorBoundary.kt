package org.mountaincircles.app.error

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger



/**
 * Module-level error boundary for module initialization and operations
 */
class ModuleErrorBoundary(private val moduleId: String) {

    /**
     * Execute module operation with error handling
     */
    suspend fun <T> execute(
        operation: suspend () -> T,
        operationName: String
    ): Result<T> {
        return try {
            Result.success(operation())
        } catch (e: Throwable) {
            val error = AppError.ModuleError(moduleId, "$operationName failed: ${e.message}", e)
            ErrorHandler.handle(error, "Module $moduleId.$operationName")
            Result.failure(e)
        }
    }

    /**
     * Execute module initialization with specific error handling
     */
    suspend fun initialize(
        initBlock: suspend () -> Unit
    ): Result<Unit> {
        return try {
            initBlock()
            Result.success(Unit)
        } catch (e: Throwable) {
            val error = AppError.ModuleInitError(moduleId, "Initialization failed: ${e.message}", e)
            ErrorHandler.handle(error, "Module $moduleId initialization")
            Result.failure(e)
        }
    }

    /**
     * Wrap a suspend operation with error handling and optional fallback
     */
    suspend fun <T> safeExecute(
        operation: suspend () -> T,
        operationName: String,
        fallback: (() -> T)? = null
    ): T {
        return try {
            operation()
        } catch (e: Throwable) {
            val error = AppError.ModuleError(moduleId, "$operationName failed: ${e.message}", e)
            ErrorHandler.handle(error, "Module $moduleId.$operationName")

            fallback?.invoke() ?: throw e
        }
    }

    /**
     * Execute operation with error recovery - attempts operation, logs errors, but continues
     */
    suspend fun executeWithRecovery(
        operation: suspend () -> Unit,
        operationName: String
    ) {
        try {
            operation()
        } catch (e: Throwable) {
            val error = AppError.ModuleError(moduleId, "$operationName failed but continuing: ${e.message}", e)
            ErrorHandler.handle(error, "Module $moduleId.$operationName (recovery mode)")
        }
    }
}

/**
 * Utility function for safe async operations with error handling
 */
suspend inline fun <T> safeAsync(
    context: String,
    crossinline operation: suspend () -> T
): Result<T> {
    return try {
        Result.success(operation())
    } catch (e: Throwable) {
        ErrorHandler.handleException(e, context)
        Result.failure(e)
    }
}

/**
 * Extension function for Result<T> to handle errors consistently
 */
inline fun <T> Result<T>.onError(
    context: String,
    crossinline handler: (Throwable) -> Unit = {}
): Result<T> {
    return onFailure { error ->
        ErrorHandler.handleException(error, context)
        handler(error)
    }
}

/**
 * Extension function for Flow<T> to handle errors consistently
 */
fun <T> kotlinx.coroutines.flow.Flow<T>.catchAppError(
    context: String
): kotlinx.coroutines.flow.Flow<T> {
    return catch { error ->
        ErrorHandler.handleException(error, context)
    }
}
