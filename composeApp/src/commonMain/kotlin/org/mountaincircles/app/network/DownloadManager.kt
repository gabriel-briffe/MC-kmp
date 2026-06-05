package org.mountaincircles.app.network

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Unified download manager interface for all HTTP download operations
 * Provides consistent API across all modules while allowing platform-specific implementations
 */
interface DownloadManager {

    /**
     * Download a file from URL to local file path
     * @param request Download request with URL, file path, headers, and fallback URLs
     * @param onProgress Progress callback with unified ProgressData
     * @return Result with DownloadResult on success, or exception on failure
     */
    suspend fun download(
        request: DownloadRequest,
        onProgress: (ProgressData) -> Unit = {}
    ): Result<DownloadResult>

    /**
     * Download text content from URL directly to memory
     * @param url URL to download from
     * @param headers Optional HTTP headers
     * @param onProgress Progress callback with unified ProgressData
     * @return Result with text content on success, or exception on failure
     */
    suspend fun downloadText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        onProgress: (ProgressData) -> Unit = {}
    ): Result<String>


}

/**
 * Request data for download operations
 */
data class DownloadRequest(
    val url: String,
    val filePath: String,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String = HttpConfig.USER_AGENT
)

/**
 * Result data from successful download operations
 */
data class DownloadResult(
    val filePath: String,
    val totalBytes: Long,
    val downloadTimeMs: Long,
    val finalUrl: String // URL that was actually used (may be different due to redirects/fallbacks)
)

/**
 * Create a cross-platform download manager instance
 * Uses platform-specific implementations for optimal performance and compatibility
 */
expect fun createDownloadManager(): DownloadManager
