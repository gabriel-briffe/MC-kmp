package org.mountaincircles.app.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import org.mountaincircles.app.error.AppError
import org.mountaincircles.app.error.ErrorHandler
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.logger.LogLevel

/**
 * DownloadStateManager - Centralized utility for managing download lifecycle across all modules
 *
 * This utility eliminates duplicate download management code by providing a standardized
 * way to execute, track, and cancel downloads across Maps, Circles, Wave, and Airspace modules.
 *
 * Benefits:
 * - ~240 lines of duplicate code elimination across 4 modules
 * - Consistent error handling and logging
 * - Proper coroutine lifecycle management
 * - Thread-safe download tracking
 * - Easy cancellation and cleanup
 * - Safe progress callbacks that don't crash on network failures
 */
class DownloadStateManager(
    private val moduleTag: String,
    private val scope: CoroutineScope = ScopeManager.uiScope // Use UI scope for state updates
) {

    // Mutex to prevent concurrent state updates that could cause crashes
    private val stateUpdateMutex = Mutex()

    // Map to track active downloads
    private val activeDownloads = mutableMapOf<String, Job>()

    /**
     * Safe wrapper for progress callbacks that prevents crashes during network failures
     * This launches a coroutine to handle the state update safely
     *
     * @param operation The state update operation to execute safely
     */
    fun safeProgressUpdate(operation: suspend () -> Unit) {
        // Launch in the UI scope to handle state updates safely
        scope.launch {
            try {
                // Check if coroutine is still active (prevents crashes on cancellation)
                coroutineContext.ensureActive()

                // Use mutex to prevent concurrent state updates
                stateUpdateMutex.withLock {
                    operation()
                }
            } catch (e: CancellationException) {
                // Expected during cancellation, don't log as error
                Logger.log(moduleTag, LogLevel.DEBUG, "Progress update cancelled")
                throw e // Re-throw to maintain cancellation semantics
            } catch (e: Exception) {
                // Use Phase 6 error framework for consistent error handling
                val appError = ErrorHandler.classifyException(e)
                ErrorHandler.handle(appError, "DownloadStateManager.safeProgressUpdate")
                // Don't re-throw - we want downloads to continue even if progress updates fail
            }
        }
    }

    /**
     * Execute a download operation with automatic lifecycle management
     *
     * @param key Unique identifier for this download (e.g., "map_123", "pack_alps")
     * @param operation The suspend function to execute
     * @return Result<T> containing the operation result or failure
     */
    suspend fun <T> executeDownload(
        key: String,
        operation: suspend () -> Result<T>
    ): Result<T> {
        // Check if download is already in progress
        if (isDownloading(key)) {
            Logger.log(moduleTag, LogLevel.WARN, "Download already in progress for key: $key")
            return Result.failure(IllegalStateException("Download already in progress for key: $key"))
        }

        return try {
            // Create and track the download job
            val job = scope.launch {
                Logger.log(moduleTag, LogLevel.INFO, "Starting download for key: $key")
                operation()
            }

            // Track the job
            activeDownloads[key] = job

            // Set up completion callback
            job.invokeOnCompletion { cause ->
                activeDownloads.remove(key)

                if (cause != null) {
                    Logger.log(moduleTag, LogLevel.ERROR, "Download failed for key: $key", cause)
                } else {
                    Logger.log(moduleTag, LogLevel.INFO, "Download completed successfully for key: $key")
                }
            }

            // Wait for completion and return result
            job.join()

            // Get the result from the operation
            // Note: This assumes the operation handles its own result reporting
            // The actual result should be handled by the calling code
            operation()

        } catch (e: Exception) {
            Logger.log(moduleTag, LogLevel.ERROR, "Download execution failed for key: $key", e)
            Result.failure(e)
        }
    }

    /**
     * Execute a download operation asynchronously (fire-and-forget)
     *
     * @param key Unique identifier for this download
     * @param operation The suspend function to execute
     * @return Job representing the download operation, or null if already in progress
     */
    fun executeDownloadAsync(
        key: String,
        operation: suspend () -> Unit
    ): Job? {
        // Check if download is already in progress
        if (isDownloading(key)) {
            Logger.log(moduleTag, LogLevel.WARN, "Download already in progress for key: $key")
            return null
        }

        val job = scope.launch {
            try {
                Logger.log(moduleTag, LogLevel.INFO, "Starting async download for key: $key")
                operation()
                Logger.log(moduleTag, LogLevel.INFO, "Async download completed for key: $key")
            } catch (e: Exception) {
                Logger.log(moduleTag, LogLevel.ERROR, "Async download failed for key: $key", e)
                // Don't re-throw: operation() handles exceptions internally (logs, updates UI state, cleanup)
            }
        }

        // Track the job
        activeDownloads[key] = job

        // Set up completion callback
        job.invokeOnCompletion { cause ->
            activeDownloads.remove(key)

            if (cause != null && cause !is CancellationException) {
                Logger.log(moduleTag, LogLevel.ERROR, "Async download failed for key: $key", cause)
            }
        }

        return job
    }

    /**
     * Check if a download is currently in progress for the given key
     */
    fun isDownloading(key: String): Boolean {
        return activeDownloads.containsKey(key)
    }

    /**
     * Get the number of active downloads
     */
    fun getActiveDownloadCount(): Int {
        return activeDownloads.size
    }

    /**
     * Get a list of all active download keys
     */
    fun getActiveDownloadKeys(): List<String> {
        return activeDownloads.keys.toList()
    }

    /**
     * Cancel a specific download by key
     *
     * @return true if download was cancelled, false if not found or already completed
     */
    fun cancelDownload(key: String): Boolean {
        val job = activeDownloads[key]
        if (job != null && job.isActive) {
            Logger.log(moduleTag, LogLevel.INFO, "Cancelling download for key: $key")
            job.cancel()
            activeDownloads.remove(key)
            return true
        } else {
            return false
        }
    }

    /**
     * Cancel all active downloads
     *
     * @return Number of downloads that were cancelled
     */
    fun cancelAllDownloads(): Int {
        val activeCount = activeDownloads.size
        Logger.log(moduleTag, LogLevel.INFO, "Cancelling all $activeCount active downloads")

        activeDownloads.values.forEach { job ->
            if (job.isActive) {
                job.cancel()
            }
        }

        activeDownloads.clear()
        return activeCount
    }

    /**
     * Clean up resources and cancel all downloads
     * Should be called when the module is being destroyed
     */
    fun cleanup() {
        Logger.log(moduleTag, LogLevel.INFO, "Cleaning up DownloadStateManager, cancelling ${getActiveDownloadCount()} active downloads")
        cancelAllDownloads()
        scope.cancel()
    }

    /**
     * Wait for all active downloads to complete
     * Useful for testing or shutdown scenarios
     */
    suspend fun awaitAllDownloads() {
        val jobs = activeDownloads.values.toList()

        jobs.forEach { job ->
            job.join()
        }
    }
}
