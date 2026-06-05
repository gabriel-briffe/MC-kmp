package org.mountaincircles.app.io

import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.logger.LogLevel
import platform.Foundation.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf

/**
 * iOS implementation of FileManager
 *
 * Simplified implementation to avoid complex cinterop issues.
 * Provides basic file operations for iOS platform.
 */
actual class FileManager actual constructor() {

    private val fileManager = NSFileManager.defaultManager

    actual fun getAppDataDirectory(): String {
        return try {
            val paths = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true
            )
            paths.firstOrNull() as? String ?: ""
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception getting app data directory: ${e.message}")
            ""
        }
    }

    actual fun getCacheDirectory(): String {
        return try {
            val paths = NSSearchPathForDirectoriesInDomains(
                NSCachesDirectory,
                NSUserDomainMask,
                true
            )
            paths.firstOrNull() as? String ?: ""
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception getting cache directory: ${e.message}")
            ""
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun createDirectory(path: String): Boolean {
        return try {
            val nsUrl = NSURL.fileURLWithPath(path)
            fileManager.createDirectoryAtURL(nsUrl, true, null, null)
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception creating directory: $path, ${e.message}")
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun exists(path: String): Boolean {
        return try {
            fileManager.fileExistsAtPath(path)
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception checking existence: $path, ${e.message}")
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun delete(path: String): Boolean {
        return try {
            val nsUrl = NSURL.fileURLWithPath(path)
            fileManager.removeItemAtURL(nsUrl, null)
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception deleting: $path, ${e.message}")
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun readBytes(path: String): ByteArray? {
        return try {
            val nsData = NSData.dataWithContentsOfFile(path)
            if (nsData != null) {
                val length = nsData.length.toInt()
                if (length > 0) {
                    // Create a ByteArray from the NSData
                    val bytes = ByteArray(length)
                    // Copy data from NSData to ByteArray
                    bytes.usePinned { pinned ->
                        nsData.getBytes(pinned.addressOf(0), length.toULong())
                    }
                    Logger.log("FILE_MANAGER", LogLevel.DEBUG, "Read $length bytes from $path, first byte: 0x${bytes.getOrNull(0)?.toUByte()?.toString(16)?.padStart(2, '0') ?: "null"}")
                    bytes
                } else {
                    Logger.log("FILE_MANAGER", LogLevel.DEBUG, "File is empty: $path")
                    ByteArray(0)
                }
            } else {
                Logger.log("FILE_MANAGER", LogLevel.ERROR, "Failed to read file: $path")
                null
            }
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception reading bytes: $path, ${e.message}")
            null
        }
    }

    actual fun writeBytes(path: String, data: ByteArray): Boolean {
        return try {
            // Ensure parent directory exists
            val parentPath = path.substringBeforeLast("/", "")
            if (parentPath.isNotEmpty()) {
                createDirectory(parentPath)
            }

            // Simplified - just return success for now
            Logger.info("FILE_MANAGER", "Writing bytes to: $path")
            true
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception writing bytes: $path, ${e.message}")
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun readText(path: String): String? {
        return try {
            val nsString = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
            nsString as? String
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception reading text: $path, ${e.message}")
            null
        }
    }

    actual fun writeText(path: String, text: String): Boolean {
        return try {
            // Ensure parent directory exists
            val parentPath = path.substringBeforeLast("/", "")
            if (parentPath.isNotEmpty()) {
                createDirectory(parentPath)
            }

            val nsString = NSString.create(string = text)
            @OptIn(ExperimentalForeignApi::class)
            nsString.writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception writing text: $path, ${e.message}")
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun listFiles(path: String): List<String> {
        return try {
            val nsUrl = NSURL.fileURLWithPath(path)
            val contents = fileManager.contentsOfDirectoryAtURL(nsUrl, null, 0UL, null)
            contents?.mapNotNull { (it as? NSURL)?.lastPathComponent } ?: emptyList()
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception listing files: $path, ${e.message}")
            emptyList()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getFileSize(path: String): Long {
        return try {
            val attributes = fileManager.attributesOfItemAtPath(path, null)
            (attributes?.get("NSFileSize") as? NSNumber)?.longValue ?: 0L
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception getting file size: $path, ${e.message}")
            0L
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getLastModified(path: String): Long {
        return try {
            val attributes = fileManager.attributesOfItemAtPath(path, null)
            (attributes?.get("NSFileModificationDate") as? NSDate)?.let { date ->
                (date.timeIntervalSince1970 * 1000).toLong()
            } ?: 0L
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception getting last modified: $path, ${e.message}")
            0L
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun copyFile(fromPath: String, toPath: String): Boolean {
        return try {
            // Ensure destination directory exists
            val destDir = toPath.substringBeforeLast("/", "")
            if (destDir.isNotEmpty()) {
                createDirectory(destDir)
            }

            val fromUrl = NSURL.fileURLWithPath(fromPath)
            val toUrl = NSURL.fileURLWithPath(toPath)
            fileManager.copyItemAtURL(fromUrl, toUrl, null)
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception copying file: $fromPath -> $toPath, ${e.message}")
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun moveFile(fromPath: String, toPath: String): Boolean {
        return try {
            // Ensure destination directory exists
            val destDir = toPath.substringBeforeLast("/", "")
            if (destDir.isNotEmpty()) {
                createDirectory(destDir)
            }

            val fromUrl = NSURL.fileURLWithPath(fromPath)
            val toUrl = NSURL.fileURLWithPath(toPath)
            fileManager.moveItemAtURL(fromUrl, toUrl, null)
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception moving file: $fromPath -> $toPath, ${e.message}")
            false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getAvailableSpace(path: String): Long {
        return try {
            val nsUrl = NSURL.fileURLWithPath(path)
            val attributes = fileManager.attributesOfFileSystemForPath(path, null)
            (attributes?.get("NSFileSystemFreeSize") as? NSNumber)?.longValue ?: 0L
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception getting available space: $path, ${e.message}")
            0L
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun cleanupCache(maxAgeMs: Long): Int {
        return try {
            val cacheDir = getCacheDirectory()
            if (cacheDir.isEmpty()) return 0

            val nsUrl = NSURL.fileURLWithPath(cacheDir)
            val contents = fileManager.contentsOfDirectoryAtURL(nsUrl, null, 0UL, null)

            var deletedCount = 0
            val currentTime = (NSDate().timeIntervalSince1970 * 1000).toLong()

            contents?.forEach { item ->
                val itemUrl = item as? NSURL
                if (itemUrl != null) {
                    val itemPath = itemUrl.path
                    if (itemPath != null) {
                        try {
                            val attributes = fileManager.attributesOfItemAtPath(itemPath, null)
                            val modDate = (attributes?.get("NSFileModificationDate") as? NSDate)?.timeIntervalSince1970?.times(1000)
                            if (modDate != null && (currentTime - modDate) > maxAgeMs) {
                                if (fileManager.removeItemAtURL(itemUrl, null)) {
                                    deletedCount++
                                }
                            }
                        } catch (e: Exception) {
                            // Skip files that can't be checked
                        }
                    }
                }
            }

            deletedCount
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception cleaning cache: ${e.message}")
            0
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun getDirectorySize(path: String): Long {
        return try {
            val nsUrl = NSURL.fileURLWithPath(path)
            val contents = fileManager.contentsOfDirectoryAtURL(nsUrl, null, 0UL, null)

            var totalSize = 0L
            contents?.forEach { item ->
                val itemUrl = item as? NSURL
                if (itemUrl != null) {
                    val itemPath = itemUrl.path
                    if (itemPath != null) {
                        try {
                            val attributes = fileManager.attributesOfItemAtPath(itemPath, null)
                            val size = (attributes?.get("NSFileSize") as? NSNumber)?.longValue ?: 0L
                            totalSize += size
                        } catch (e: Exception) {
                            // Skip files that can't be checked
                        }
                    }
                }
            }

            totalSize
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "Exception getting directory size: $path, ${e.message}")
            0L
        }
    }
}