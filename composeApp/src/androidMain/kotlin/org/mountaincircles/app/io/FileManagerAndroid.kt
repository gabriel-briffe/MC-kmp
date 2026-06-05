package org.mountaincircles.app.io

import android.content.Context
import java.io.File
import java.io.IOException
import org.mountaincircles.app.logger.Logger

/**
 * Android implementation of FileManager
 *
 * Uses Android's Context and File APIs to provide platform-specific
 * file operations with proper permissions and security.
 */
actual class FileManager actual constructor() {

    // Context will be set during initialization
    private var applicationContext: Context? = null

    /**
     * Initialize the FileManager with application context
     * Called from Application.onCreate() so FileManager is available for widgets.
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    private val context: Context
        get() = applicationContext ?: throw IllegalStateException(
            "FileManager not initialized. Call initialize(context) in Application.onCreate()"
        )

    actual fun getAppDataDirectory(): String {
        return context.filesDir.absolutePath
    }

    actual fun getCacheDirectory(): String {
        return context.cacheDir.absolutePath
    }

    actual fun createDirectory(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists()) {
                file.isDirectory
            } else {
                file.mkdirs()
            }
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception creating directory: $path")
            false
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception creating directory: $path, ${e.message}")
            false
        }
    }

    actual fun exists(path: String): Boolean {
        return try {
            File(path).exists()
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception checking existence: $path")
            false
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception checking existence: $path, ${e.message}")
            false
        }
    }

    actual fun delete(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception deleting: $path")
            false
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception deleting: $path, ${e.message}")
            false
        }
    }

    actual fun readBytes(path: String): ByteArray? {
        return try {
            File(path).readBytes()
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception reading bytes: $path")
            null
        } catch (e: IOException) {
            Logger.warn("FILE_MANAGER", "FileManager: IOException reading bytes: $path, ${e.message}")
            null
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception reading bytes: $path, ${e.message}")
            null
        }
    }

    actual fun writeBytes(path: String, data: ByteArray): Boolean {
        return try {
            val file = File(path)
            // Ensure parent directory exists
            file.parentFile?.mkdirs()
            file.writeBytes(data)
            true
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception writing bytes: $path")
            false
        } catch (e: IOException) {
            Logger.warn("FILE_MANAGER", "FileManager: IOException writing bytes: $path, ${e.message}")
            false
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception writing bytes: $path, ${e.message}")
            false
        }
    }

    actual fun readText(path: String): String? {
        return try {
            File(path).readText(Charsets.UTF_8)
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception reading text: $path")
            null
        } catch (e: IOException) {
            Logger.warn("FILE_MANAGER", "FileManager: IOException reading text: $path, ${e.message}")
            null
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception reading text: $path, ${e.message}")
            null
        }
    }

    actual fun writeText(path: String, text: String): Boolean {
        return try {
            val file = File(path)
            // Ensure parent directory exists
            file.parentFile?.mkdirs()
            file.writeText(text, Charsets.UTF_8)
            true
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception writing text: $path")
            false
        } catch (e: IOException) {
            Logger.warn("FILE_MANAGER", "FileManager: IOException writing text: $path, ${e.message}")
            false
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception writing text: $path, ${e.message}")
            false
        }
    }

    actual fun listFiles(path: String): List<String> {
        return try {
            val file = File(path)
            if (file.exists() && file.isDirectory) {
                file.listFiles()?.map { it.name } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception listing files: $path")
            emptyList()
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception listing files: $path, ${e.message}")
            emptyList()
        }
    }

    actual fun getFileSize(path: String): Long {
        return try {
            val file = File(path)
            if (file.exists() && file.isFile) {
                file.length()
            } else {
                -1L
            }
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception getting file size: $path")
            -1L
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception getting file size: $path, ${e.message}")
            -1L
        }
    }

    actual fun getLastModified(path: String): Long {
        return try {
            val file = File(path)
            if (file.exists()) {
                file.lastModified()
            } else {
                -1L
            }
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception getting last modified: $path")
            -1L
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception getting last modified: $path, ${e.message}")
            -1L
        }
    }

    actual fun copyFile(fromPath: String, toPath: String): Boolean {
        return try {
            val fromFile = File(fromPath)
            val toFile = File(toPath)

            if (!fromFile.exists() || !fromFile.isFile) {
                Logger.warn("FILE_MANAGER", "FileManager: Source file doesn't exist or is not a file: $fromPath")
                return false
            }

            // Ensure parent directory exists
            toFile.parentFile?.mkdirs()

            fromFile.copyTo(toFile, overwrite = true)
            true
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception copying file: $fromPath -> $toPath")
            false
        } catch (e: IOException) {
            Logger.warn("FILE_MANAGER", "FileManager: IOException copying file: $fromPath -> $toPath, ${e.message}")
            false
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception copying file: $fromPath -> $toPath, ${e.message}")
            false
        }
    }

    actual fun moveFile(fromPath: String, toPath: String): Boolean {
        return try {
            val fromFile = File(fromPath)
            val toFile = File(toPath)

            if (!fromFile.exists() || !fromFile.isFile) {
                Logger.warn("FILE_MANAGER", "FileManager: Source file doesn't exist or is not a file: $fromPath")
                return false
            }

            // Ensure parent directory exists
            toFile.parentFile?.mkdirs()

            fromFile.copyTo(toFile, overwrite = true)
            fromFile.delete()
            true
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception moving file: $fromPath -> $toPath")
            false
        } catch (e: IOException) {
            Logger.warn("FILE_MANAGER", "FileManager: IOException moving file: $fromPath -> $toPath, ${e.message}")
            false
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception moving file: $fromPath -> $toPath, ${e.message}")
            false
        }
    }

    actual fun getAvailableSpace(path: String): Long {
        return try {
            val file = File(path)
            val stat = android.os.StatFs(file.parent ?: path)
            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.availableBlocksLong
            blockSize * availableBlocks
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception getting available space: $path")
            -1L
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception getting available space: $path, ${e.message}")
            -1L
        }
    }

    actual fun cleanupCache(maxAgeMs: Long): Int {
        return try {
            val cacheDir = File(getCacheDirectory())
            if (!cacheDir.exists() || !cacheDir.isDirectory) {
                return 0
            }

            val cutoffTime = System.currentTimeMillis() - maxAgeMs
            var deletedCount = 0

            cacheDir.walkTopDown().forEach { file ->
                if (file.isFile && file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }

            Logger.info("FILE_MANAGER", "FileManager: Cleaned up $deletedCount cache files older than ${maxAgeMs / (1000 * 60 * 60)} hours")
            deletedCount
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception cleaning cache")
            0
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception cleaning cache: ${e.message}")
            0
        }
    }

    actual fun getDirectorySize(path: String): Long {
        return try {
            val file = File(path)
            if (!file.exists() || !file.isDirectory) {
                return -1L
            }

            var totalSize = 0L
            file.walkTopDown().forEach { child ->
                if (child.isFile) {
                    totalSize += child.length()
                }
            }
            totalSize
        } catch (e: SecurityException) {
            Logger.warn("FILE_MANAGER", "FileManager: Security exception getting directory size: $path")
            -1L
        } catch (e: Exception) {
            Logger.warn("FILE_MANAGER", "FileManager: Exception getting directory size: $path, ${e.message}")
            -1L
        }
    }
}
