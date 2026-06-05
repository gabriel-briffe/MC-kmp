package org.mountaincircles.app.network

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.network.HttpConfig
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.math.min

/**
 * iOS implementation of unified download manager
 * Uses Ktor with Darwin engine for HTTP operations on iOS
 */
@OptIn(ExperimentalForeignApi::class)
class UnifiedDownloadManagerIOS : DownloadManager {

    private val client = HttpClient(Darwin) {
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
            }
        }
    }

    override suspend fun download(
        request: DownloadRequest,
        onProgress: (ProgressData) -> Unit
    ): Result<DownloadResult> = withContext(Dispatchers.Default) {
        val startTime = (NSDate().timeIntervalSince1970 * 1000).toLong()

        try {
            Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.DEBUG, "Attempting download from: ${request.url}")

            val result = downloadFromUrl(request.url, request, onProgress)
            if (result.isSuccess) {
                val downloadTime = (NSDate().timeIntervalSince1970 * 1000).toLong() - startTime
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
                Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.ERROR, "Download failed: ${result.exceptionOrNull()?.message}")
                return@withContext result
            }
        } catch (e: Exception) {
            Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.ERROR, "Download exception: ${e.message}")
            return@withContext Result.failure(e)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun downloadFromUrl(
        url: String,
        request: DownloadRequest,
        onProgress: (ProgressData) -> Unit
    ): Result<DownloadResult> {
        val filePath = request.filePath // Declare at function scope for exception handling

        return try {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            val response = client.get(url) {
                request.headers.forEach { (key, value) ->
                    header(key, value)
                }
                header(HttpHeaders.UserAgent, request.userAgent)
            }

            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            if (!response.status.isSuccess()) {
                Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.ERROR, "HTTP error: ${response.status}")
                return Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}"))
            }

            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L

            Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.DEBUG, "Downloading to: $filePath, size: $contentLength")

            // Create parent directory if it doesn't exist
            val parentPath = filePath.substringBeforeLast("/", "")
            if (parentPath.isNotEmpty()) {
                val fileManager = NSFileManager.defaultManager
                val parentUrl = NSURL.fileURLWithPath(parentPath)
                try {
                    fileManager.createDirectoryAtURL(
                        parentUrl,
                        withIntermediateDirectories = true,
                        attributes = null,
                        error = null
                    )
                } catch (e: Exception) {
                    Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.WARN, "Could not create parent directory: $parentPath")
                }
            }

            // Report initial progress
            Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.DEBUG, "Reporting initial progress: 0% (0/$contentLength)")
            onProgress(ProgressData(
                downloaded = 0,
                total = contentLength,
                status = "Starting download...",
                percentage = 0f
            ))

            // Create file handle for incremental writing
            val fileHandle = NSFileHandle.fileHandleForWritingAtPath(filePath)
                ?: NSFileManager.defaultManager.createFileAtPath(filePath, null, null).let {
                    NSFileHandle.fileHandleForWritingAtPath(filePath)
                }

            if (fileHandle == null) {
                Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.ERROR, "Failed to create file handle for: $filePath")
                return Result.failure(Exception("Failed to create file handle for: $filePath"))
            }

            var fileHandleInitialized = false
            try {
                fileHandleInitialized = true
                // Use chunked download with 5MB chunks
                val chunkSize = 5 * 1024 * 1024L // 5MB chunks
                var downloaded = 0L

                Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.DEBUG, "Starting chunked download with contentLength: $contentLength, chunkSize: $chunkSize")

                while (downloaded < contentLength) {
                    // Check for cancellation
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()

                    val startByte = downloaded
                    val endByte = minOf(startByte + chunkSize - 1, contentLength - 1)

                    Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.DEBUG, "Downloading chunk: bytes=$startByte-$endByte")

                    // Download this chunk
                    val chunkResponse = client.get(url) {
                        request.headers.forEach { (key, value) ->
                            header(key, value)
                        }
                        header(HttpHeaders.UserAgent, request.userAgent)
                        header(HttpHeaders.Range, "bytes=$startByte-$endByte")
                    }

                    // Check if server supports range requests
                    if (chunkResponse.status != HttpStatusCode.PartialContent) {
                        Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.WARN, "Server does not support Range requests (${chunkResponse.status}), falling back to full download")

                        // Fallback to full download if range not supported
                        val fullResponse = client.get(url) {
                            request.headers.forEach { (key, value) ->
                                header(key, value)
                            }
                            header(HttpHeaders.UserAgent, request.userAgent)
                        }

                        val bytes = fullResponse.readBytes()
                        bytes.usePinned { pinned ->
                            val nsData = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                            fileHandle.writeData(nsData)
                        }

                        downloaded = bytes.size.toLong()

                        // Report completion
                        onProgress(ProgressData(
                            downloaded = downloaded,
                            total = contentLength,
                            status = "Download completed",
                            percentage = if (contentLength > 0) (downloaded * 100.0 / contentLength).toFloat() else 100f
                        ))
                        break
                    }

                    // Process the chunk
                    val chunkBytes = chunkResponse.readBytes()
                    Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.DEBUG, "Received chunk of ${chunkBytes.size} bytes")

                    chunkBytes.usePinned { pinned ->
                        val nsData = NSData.create(bytes = pinned.addressOf(0), length = chunkBytes.size.toULong())
                        fileHandle.writeData(nsData)
                    }

                    downloaded += chunkBytes.size

                    // Report progress after each chunk
                    val progressPercent = if (contentLength > 0) (downloaded * 100.0 / contentLength).toFloat() else 0f
                    Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.DEBUG, "Chunk completed - progress: ${progressPercent.toInt()}% ($downloaded/$contentLength)")

                    onProgress(ProgressData(
                        downloaded = downloaded,
                        total = contentLength,
                        status = "Downloading...",
                        percentage = progressPercent
                    ))
                }

                // Final progress report
                val finalPercent = if (contentLength > 0) (downloaded * 100.0 / contentLength).toFloat() else 100f
                Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.DEBUG, "Reporting final progress: ${finalPercent.toInt()}% ($downloaded/$contentLength)")
                onProgress(ProgressData(
                    downloaded = downloaded,
                    total = contentLength,
                    status = "Download completed",
                    percentage = finalPercent
                ))

                if (fileHandleInitialized) {
                    fileHandle.closeFile()
                }

                Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.INFO, "Download completed: $filePath (${downloaded} bytes)")
                Result.success(DownloadResult(
                    filePath = filePath,
                    totalBytes = downloaded,
                    downloadTimeMs = 0,
                    finalUrl = url
                ))
            } catch (e: Exception) {
                if (fileHandleInitialized) {
                    fileHandle.closeFile()
                }
                Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.ERROR, "Failed during chunked download: ${e.message}")
                Result.failure(e)
            }
        } catch (e: CancellationException) {
            Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.INFO, "Download cancelled: $url")
            // Clean up partial file
            try {
                NSFileManager.defaultManager.removeItemAtPath(filePath, null)
                Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.INFO, "Cleaned up partial download file: $filePath")
            } catch (cleanupException: Exception) {
                Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.WARN, "Failed to clean up partial file: $filePath")
            }
            throw e
        } catch (e: Exception) {
            Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.ERROR, "Download failed: ${e.message}")
            // Clean up partial file on error
            try {
                NSFileManager.defaultManager.removeItemAtPath(filePath, null)
                Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.INFO, "Cleaned up partial download file after error: $filePath")
            } catch (cleanupException: Exception) {
                Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.WARN, "Failed to clean up partial file: $filePath")
            }
            Result.failure(e)
        }
    }

    override suspend fun downloadText(
        url: String,
        headers: Map<String, String>,
        onProgress: (ProgressData) -> Unit
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.DEBUG, "Downloading text from: $url")

            val response = client.get(url) {
                headers.forEach { (key, value) ->
                    header(key, value)
                }
                header(HttpHeaders.UserAgent, HttpConfig.USER_AGENT)
            }

            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            if (!response.status.isSuccess()) {
                Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.ERROR, "HTTP error: ${response.status}")
                return@withContext Result.failure(Exception("HTTP ${response.status.value}: ${response.status.description}"))
            }

            val text = response.bodyAsText()
            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.DEBUG, "Text download completed: ${text.length} characters")
            Result.success(text)
        } catch (e: CancellationException) {
            Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.INFO, "Text download cancelled: $url")
            throw e
        } catch (e: Exception) {
            Logger.log("UNIFIED_DOWNLOAD_IOS", LogLevel.ERROR, "Text download failed: ${e.message}")
            Result.failure(e)
        }
    }
}
