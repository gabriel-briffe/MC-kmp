package org.mountaincircles.app.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.mountaincircles.app.error.AppError
import org.mountaincircles.app.error.ErrorHandler
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android implementation of unified download manager
 * Uses HttpURLConnection for optimal Android compatibility
 */
class UnifiedDownloadManagerAndroid : DownloadManager {

    override suspend fun download(
        request: DownloadRequest,
        onProgress: (ProgressData) -> Unit
    ): Result<DownloadResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            Logger.log("UNIFIED_DOWNLOAD", LogLevel.DEBUG, "Attempting download from: ${request.url}")

            val result = downloadFromUrl(request.url, request, onProgress)
            if (result.isSuccess) {
                val downloadTime = System.currentTimeMillis() - startTime
                val downloadResult = result.getOrThrow()
                return@withContext Result.success(
                    DownloadResult(
                        filePath = downloadResult.filePath,
                        totalBytes = downloadResult.totalBytes,
                        downloadTimeMs = downloadTime,
                        finalUrl = request.url
                    )
                )
            } else {
                Logger.log("UNIFIED_DOWNLOAD", LogLevel.ERROR, "Download failed: ${result.exceptionOrNull()?.message}")
                return@withContext result
            }
        } catch (e: CancellationException) {
            Logger.log("UNIFIED_DOWNLOAD", LogLevel.INFO, "Download cancelled: ${request.url}")
            throw e // Re-throw cancellation to maintain coroutine semantics
        } catch (e: Exception) {
            // Use Phase 6 error framework for consistent classification and logging
            val appError = when (e) {
                is java.net.UnknownHostException ->
                    AppError.NetworkError("DNS resolution failed for ${request.url}", e)
                is java.net.SocketTimeoutException ->
                    AppError.TimeoutError("download", HttpConfig.READ_TIMEOUT_MS.toInt().toLong())
                is java.io.IOException ->
                    AppError.NetworkError("Network I/O error for ${request.url}", e)
                is javax.net.ssl.SSLHandshakeException ->
                    AppError.NetworkError("SSL handshake failed for ${request.url}", e)
                else ->
                    ErrorHandler.classifyException(e)
            }

            ErrorHandler.handle(appError, "DownloadManager.download(${request.url})")

            // Clean up any partial file on error
            try {
                val partialFile = File(request.filePath)
                if (partialFile.exists()) partialFile.delete()
            } catch (cleanupException: Exception) {
                ErrorHandler.handleException(cleanupException, "Cleanup after download failure")
            }

            return@withContext Result.failure(e)
        }
    }

    override suspend fun downloadText(
        url: String,
        headers: Map<String, String>,
        onProgress: (ProgressData) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Logger.log("UNIFIED_DOWNLOAD", LogLevel.DEBUG, "Downloading text from: $url")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = HttpConfig.CONNECT_TIMEOUT_MS.toInt()
                readTimeout = HttpConfig.READ_TIMEOUT_MS.toInt()
                requestMethod = "GET"
                setRequestProperty("User-Agent", HttpConfig.USER_AGENT)
                // Add custom headers
                headers.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
                instanceFollowRedirects = true
            }

            val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: -1L

            connection.inputStream.use { input ->
                val text = input.bufferedReader().use { reader ->
                    // Check for cancellation before reading
                    ensureActive()
                    reader.readText()
                }

                // Report completion progress
                onProgress(ProgressData.fromBytes(text.length.toLong(), contentLength, "Text download completed"))

                Logger.log("UNIFIED_DOWNLOAD", LogLevel.DEBUG, "Downloaded text content: ${text.length} characters")
                Result.success(text)
            }

        } catch (e: Exception) {
            Logger.log("UNIFIED_DOWNLOAD", LogLevel.ERROR, "Text download failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun downloadFromUrl(
        url: String,
        request: DownloadRequest,
        onProgress: (ProgressData) -> Unit
    ): Result<DownloadResult> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = HttpConfig.CONNECT_TIMEOUT_MS.toInt()
                readTimeout = HttpConfig.READ_TIMEOUT_MS.toInt()
                requestMethod = "GET"
                setRequestProperty("User-Agent", request.userAgent)
                // Add custom headers
                request.headers.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
                instanceFollowRedirects = true
            }

            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            var downloaded = 0L

            // Ensure parent directory exists
            val outFile = File(request.filePath)
            outFile.parentFile?.mkdirs()

            connection.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(HttpConfig.BUFFER_SIZE_BYTES)
                    var lastReport = System.currentTimeMillis()

                    while (true) {
                        // Check for coroutine cancellation before each read
                        ensureActive()

                        val bytesRead = input.read(buffer)
                        if (bytesRead <= 0) break

                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReport > HttpConfig.PROGRESS_REPORT_INTERVAL_MS) {
                            onProgress(ProgressData.fromBytes(downloaded, total, "Downloading..."))
                            lastReport = now
                        }
                    }
                    // Final progress report
                    onProgress(ProgressData.complete(total, "Download completed"))
                }
            }

            Logger.log("UNIFIED_DOWNLOAD", LogLevel.DEBUG, "Downloaded ${outFile.length()} bytes to ${outFile.absolutePath}")
            Result.success(DownloadResult(
                filePath = request.filePath,
                totalBytes = outFile.length(),
                downloadTimeMs = 0L, // Will be set by caller
                finalUrl = url
            ))

        } catch (e: CancellationException) {
            Logger.log("UNIFIED_DOWNLOAD", LogLevel.INFO, "Download cancelled: ${request.filePath}")
            // Clean up partially downloaded file
            try {
                val partialFile = File(request.filePath)
                if (partialFile.exists()) {
                    partialFile.delete()
                    Logger.log("UNIFIED_DOWNLOAD", LogLevel.INFO, "Cleaned up partial download file: ${request.filePath}")
                }
            } catch (cleanupException: Exception) {
                Logger.log("UNIFIED_DOWNLOAD", LogLevel.WARN, "Failed to clean up partial file: ${request.filePath}", cleanupException)
            }
            throw e // Re-throw cancellation
        } catch (e: Exception) {
            // Use Phase 6 error framework for consistent classification and logging
            val appError = when (e) {
                is java.net.UnknownHostException ->
                    AppError.NetworkError("DNS resolution failed for $url", e)
                is java.net.SocketTimeoutException ->
                    AppError.TimeoutError("download", HttpConfig.READ_TIMEOUT_MS.toInt().toLong())
                is java.io.IOException ->
                    AppError.NetworkError("Network I/O error for $url", e)
                is javax.net.ssl.SSLHandshakeException ->
                    AppError.NetworkError("SSL handshake failed for $url", e)
                else ->
                    ErrorHandler.classifyException(e)
            }

            ErrorHandler.handle(appError, "DownloadManager.downloadFromUrl($url)")

            // Clean up any partial file on error
            try {
                val partialFile = File(request.filePath)
                if (partialFile.exists()) partialFile.delete()
            } catch (cleanupException: Exception) {
                ErrorHandler.handleException(cleanupException, "Cleanup after downloadFromUrl failure")
            }

            return@withContext Result.failure<DownloadResult>(e)
        }
    }
}
