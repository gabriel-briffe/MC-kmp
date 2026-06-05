package org.mountaincircles.app.io

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic file management interface
 *
 * This interface provides a unified API for file operations across all platforms.
 * It handles the differences between Android and iOS file systems automatically.
 */
expect class FileManager() {

    /**
     * Get the application's data directory path
     *
     * @return The absolute path to the app's data directory
     */
    fun getAppDataDirectory(): String

    /**
     * Get the cache directory path
     *
     * @return The absolute path to the app's cache directory
     */
    fun getCacheDirectory(): String

    /**
     * Create a directory if it doesn't exist
     *
     * @param path The directory path to create
     * @return true if successful, false otherwise
     */
    fun createDirectory(path: String): Boolean

    /**
     * Check if a file or directory exists
     *
     * @param path The file or directory path to check
     * @return true if the path exists, false otherwise
     */
    fun exists(path: String): Boolean

    /**
     * Delete a file or directory
     *
     * @param path The file or directory path to delete
     * @return true if successful, false otherwise
     */
    fun delete(path: String): Boolean

    /**
     * Read a file as bytes
     *
     * @param path The file path to read
     * @return The file contents as ByteArray, or null if failed
     */
    fun readBytes(path: String): ByteArray?

    /**
     * Write bytes to a file
     *
     * @param path The file path to write to
     * @param data The data to write
     * @return true if successful, false otherwise
     */
    fun writeBytes(path: String, data: ByteArray): Boolean

    /**
     * Read a file as text (UTF-8)
     *
     * @param path The file path to read
     * @return The file contents as String, or null if failed
     */
    fun readText(path: String): String?

    /**
     * Write text to a file (UTF-8)
     *
     * @param path The file path to write to
     * @param text The text to write
     * @return true if successful, false otherwise
     */
    fun writeText(path: String, text: String): Boolean

    /**
     * List files in a directory
     *
     * @param path The directory path to list
     * @return List of file names, empty list if directory doesn't exist or is empty
     */
    fun listFiles(path: String): List<String>

    /**
     * Get file size in bytes
     *
     * @param path The file path to check
     * @return File size in bytes, or -1 if file doesn't exist
     */
    fun getFileSize(path: String): Long

    /**
     * Get last modified timestamp
     *
     * @param path The file path to check
     * @return Last modified timestamp in milliseconds, or -1 if failed
     */
    fun getLastModified(path: String): Long

    /**
     * Copy a file from one location to another
     *
     * @param fromPath Source file path
     * @param toPath Destination file path
     * @return true if successful, false otherwise
     */
    fun copyFile(fromPath: String, toPath: String): Boolean

    /**
     * Move a file from one location to another
     *
     * @param fromPath Source file path
     * @param toPath Destination file path
     * @return true if successful, false otherwise
     */
    fun moveFile(fromPath: String, toPath: String): Boolean

    /**
     * Get available space in bytes
     *
     * @param path Path to check (can be any path on the same filesystem)
     * @return Available space in bytes, or -1 if unknown
     */
    fun getAvailableSpace(path: String): Long

    /**
     * Clean up cache files older than specified time
     *
     * @param maxAgeMs Maximum age in milliseconds
     * @return Number of files deleted
     */
    fun cleanupCache(maxAgeMs: Long): Int

    /**
     * Get directory size recursively
     *
     * @param path Directory path to calculate size for
     * @return Total size in bytes, or -1 if failed
     */
    fun getDirectorySize(path: String): Long
}

/**
 * File operation result wrapper
 */
sealed class FileResult<out T> {
    data class Success<T>(val data: T) : FileResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : FileResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> default
    }

    fun exceptionOrNull(): Throwable? = when (this) {
        is Success -> null
        is Error -> cause
    }
}