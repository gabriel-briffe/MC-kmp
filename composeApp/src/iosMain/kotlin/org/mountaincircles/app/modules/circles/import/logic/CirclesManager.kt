package org.mountaincircles.app.modules.circles.import.logic

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.logic.data.PackMetadata
import platform.Foundation.*
import platform.zlib.*
import kotlinx.cinterop.*
import kotlinx.cinterop.sizeOf

/**
 * iOS implementation of CirclesManager
 * Handles circles pack file management on iOS
 */
@OptIn(ExperimentalForeignApi::class)
actual class CirclesManager {

    /**
     * Get the circles data directory path
     */
    actual fun getCirclesDirectory(): String {
        val documentsPath = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String ?: ""

        val circlesPath = "$documentsPath/circles"
        Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Circles directory: $circlesPath")
        return circlesPath
    }

    /**
     * Check if a file exists
     */
    actual fun fileExists(filePath: String): Boolean {
        return NSFileManager.defaultManager.fileExistsAtPath(filePath)
    }

    /**
     * Get list of installed circles packs
     */
    actual suspend fun getInstalledPacks(): List<String> = withContext(Dispatchers.Default) {
        try {
            val circlesDir = getCirclesDirectory()
            val fileManager = NSFileManager.defaultManager

            if (!fileManager.fileExistsAtPath(circlesDir)) {
                Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Circles directory does not exist")
                return@withContext emptyList<String>()
            }

            val contents = fileManager.contentsOfDirectoryAtPath(circlesDir, null) as? List<String> ?: emptyList()
            val packs = contents.filter { packName ->
                val packPath = "$circlesDir/$packName"
                // Check if it's a directory and has config subdirectories
                // Check if it's a directory by trying to list its contents
                try {
                    fileManager.contentsOfDirectoryAtPath(packPath, null) != null
                } catch (e: Exception) {
                    false
                }
            }

            Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Found ${packs.size} installed packs: ${packs.joinToString(", ")}")
            packs.sorted()
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Error getting installed packs: ${e.message}", e)
        emptyList()
        }
    }

    /**
     * Get list of available configurations for a pack
     */
    actual suspend fun getPackConfigurations(packId: String): List<String> = withContext(Dispatchers.Default) {
        try {
            val packDir = "${getCirclesDirectory()}/$packId"
            val fileManager = NSFileManager.defaultManager

            if (!fileManager.fileExistsAtPath(packDir)) {
                return@withContext emptyList<String>()
            }

            val contents = fileManager.contentsOfDirectoryAtPath(packDir, null) as? List<String> ?: emptyList()
            val configs = contents.filter { configName ->
                val configPath = "$packDir/$configName"
                // Check if it's a directory by trying to list its contents
                try {
                    fileManager.contentsOfDirectoryAtPath(configPath, null) != null
                } catch (e: Exception) {
                    false
                }
            }

            Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Found ${configs.size} configurations for pack $packId: ${configs.joinToString(", ")}")
            configs.sorted()
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Error getting pack configurations for $packId: ${e.message}", e)
        emptyList()
        }
    }

    /**
     * Delete a circles pack configuration and all its files
     */
    actual suspend fun deletePack(packId: String, configId: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val circlesDir = getCirclesDirectory()
            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Attempting to delete pack config: $packId/$configId from circles dir: $circlesDir")

            val configDir = "$circlesDir/$packId/$configId"
            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Config directory path: $configDir")
            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Config directory exists: ${NSFileManager.defaultManager.fileExistsAtPath(configDir)}")

            if (!NSFileManager.defaultManager.fileExistsAtPath(configDir)) {
                Logger.log("CIRCLES_MANAGER", LogLevel.WARN, "Config directory does not exist: $packId/$configId")
                return@withContext true // Consider as success if already doesn't exist
            }

            try {
                val nsUrl = NSURL.fileURLWithPath(configDir)
                val deleted = NSFileManager.defaultManager.removeItemAtURL(nsUrl, null)
                Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Delete operation result: $deleted")

                if (deleted) {
                    Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Successfully deleted pack config: $packId/$configId")
                } else {
                    Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to delete pack config: $packId/$configId")
                }
                return@withContext deleted
            } catch (e: Exception) {
                Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Exception deleting pack config $packId/$configId: ${e.message}")
                false
            }
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Error deleting pack config $packId/$configId: ${e.message}", e)
        false
        }
    }

    /**
     * Get the total number of files for a pack configuration
     */
    actual suspend fun getPackConfigFileCount(packId: String, configId: String): Int = withContext(Dispatchers.Default) {
        try {
            val configDir = "${getCirclesDirectory()}/$packId/$configId"
            val fileManager = NSFileManager.defaultManager

            if (!fileManager.fileExistsAtPath(configDir)) {
                return@withContext 0
            }

            val contents = fileManager.contentsOfDirectoryAtPath(configDir, null) as? List<String> ?: emptyList()
            val geoJsonFiles = contents.filter { it.endsWith(".geojson") }

            Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Pack $packId config $configId has ${geoJsonFiles.size} files")
            geoJsonFiles.size
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Error counting files for pack $packId config $configId: ${e.message}", e)
            0
        }
    }

    /**
     * Check if a specific pack configuration is completely installed
     */
    actual suspend fun isPackConfigInstalled(packId: String, configId: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val configDir = "${getCirclesDirectory()}/$packId/$configId"

            if (!NSFileManager.defaultManager.fileExistsAtPath(configDir)) {
                return@withContext false
            }

            // Check for main files that should exist
            val mainFile = "$configDir/aa_${packId}_${configId}.geojson"
            val sectorsFile = "$configDir/aa_${packId}_${configId}_sectors1.geojson"

            val isInstalled = NSFileManager.defaultManager.fileExistsAtPath(mainFile) &&
                             NSFileManager.defaultManager.fileExistsAtPath(sectorsFile)
            Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Pack $packId config $configId installed: $isInstalled")
            isInstalled
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Error checking installation for pack $packId config $configId: ${e.message}", e)
        false
        }
    }

    /**
     * Clear all circles files
     */
    actual suspend fun clearAllFiles(): Int = withContext(Dispatchers.Default) {
        try {
            val circlesDir = getCirclesDirectory()
            val fileManager = NSFileManager.defaultManager
            var deletedCount = 0

            if (!fileManager.fileExistsAtPath(circlesDir)) {
                return@withContext 0
            }

            // Get all items in circles directory
            val contents = fileManager.contentsOfDirectoryAtPath(circlesDir, null) as? List<String> ?: emptyList()

            contents.forEach { itemName ->
                val itemPath = "$circlesDir/$itemName"
                try {
                    val nsUrl = NSURL.fileURLWithPath(itemPath)
                    if (fileManager.removeItemAtURL(nsUrl, null)) {
                        deletedCount++
                    } else {
                        Logger.log("CIRCLES_MANAGER", LogLevel.WARN, "Failed to delete $itemPath")
                    }
                } catch (e: Exception) {
                    Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Error deleting $itemPath: ${e.message}")
                }
            }

            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Cleared $deletedCount circles files")
            deletedCount
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Error clearing circles files: ${e.message}")
            0
        }
    }

    /**
     * Read GeoJSON file content
     */
    actual suspend fun readGeoJsonFile(filePath: String): String? = withContext(Dispatchers.Default) {
        try {
            if (!NSFileManager.defaultManager.fileExistsAtPath(filePath)) {
                Logger.log("CIRCLES_MANAGER", LogLevel.WARN, "GeoJSON file not found: $filePath")
                return@withContext null
            }

            try {
                val content = NSString.stringWithContentsOfFile(filePath, NSUTF8StringEncoding, null)
                if (content != null) {
                    Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Reading GeoJSON file: $filePath")
                    content as String
                } else {
                    Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to read GeoJSON file: $filePath")
                    null
                }
            } catch (e: Exception) {
                Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Exception reading GeoJSON file: $filePath - ${e.message}")
                null
            }
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to read GeoJSON file: $filePath - ${e.message}")
            null
        }
    }

    /**
     * Read metadata for a pack configuration
     */
    actual suspend fun readPackMetadata(packId: String, configId: String): PackMetadata? = withContext(Dispatchers.Default) {
        try {
            val metadataFile = "${getCirclesDirectory()}/$packId/$configId/manifest.json"

            if (!NSFileManager.defaultManager.fileExistsAtPath(metadataFile)) {
                Logger.log("CIRCLES_MANAGER", LogLevel.WARN, "Manifest file not found: $metadataFile")
                return@withContext null
            }

            val content = try {
                NSString.stringWithContentsOfFile(metadataFile, NSUTF8StringEncoding, null) as? String
            } catch (e: Exception) {
                Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Exception reading manifest: ${e.message}")
                null
            }
            if (content == null) {
                return@withContext null
            }

            val json = Json.parseToJsonElement(content).jsonObject
            PackMetadata(
                policy = json["policy"]?.jsonPrimitive?.content ?: packId,
                config = json["config"]?.jsonPrimitive?.content ?: configId,
                prefix = json["prefix"]?.jsonPrimitive?.content ?: configId.split("-").dropLast(1).joinToString("-"),
                expected = json["expected"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                ld_ratio = json["ld_ratio"]?.jsonPrimitive?.content,
                elevation_interval = json["elevation_interval"]?.jsonPrimitive?.content?.toIntOrNull(),
                description = json["description"]?.jsonPrimitive?.content
            )
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to read metadata for $packId/$configId: ${e.message}")
            null
        }
    }

    /**
     * Get list of available configurations for a pack
     */
    actual suspend fun getAvailableConfigurations(packId: String): List<String> = withContext(Dispatchers.Default) {
        try {
            val packDir = "${getCirclesDirectory()}/$packId"
            val fileManager = NSFileManager.defaultManager

            if (!fileManager.fileExistsAtPath(packDir)) {
                return@withContext emptyList<String>()
            }

            val contents = fileManager.contentsOfDirectoryAtPath(packDir, null) as? List<String> ?: emptyList()
            val configs = contents.filter { configName ->
                val configPath = "$packDir/$configName"
                // Check if it's a directory by trying to list its contents
                try {
                    fileManager.contentsOfDirectoryAtPath(configPath, null) != null
                } catch (e: Exception) {
                    false
                }
            }

            configs.sorted()
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to get configurations for pack $packId: ${e.message}")
        emptyList()
        }
    }

    /**
            * Unzip a circles pack to the circles directory
            * Uses iOS built-in ZIP handling via NSFileManager
     */
           @OptIn(ExperimentalForeignApi::class)
    actual suspend fun unzipToCirclesDir(fileBytes: ByteArray): Boolean = withContext(Dispatchers.Default) {
               try {
                   Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "unzipToCirclesDir called with ByteArray size: ${fileBytes.size}")
                   if (fileBytes.isNotEmpty()) {
                       val firstByte = fileBytes[0].toUByte().toString(16).padStart(2, '0')
                       val first16 = fileBytes.take(16).joinToString(" ") { "0x${it.toUByte().toString(16).padStart(2, '0')}" }
                       Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "ByteArray first byte: 0x$firstByte, first 16 bytes: $first16")
                   }

                   val baseDir = getCirclesDirectory()
                   val fileManager = NSFileManager.defaultManager

                   // Create base directory if it doesn't exist
                   if (!fileManager.fileExistsAtPath(baseDir)) {
                       fileManager.createDirectoryAtPath(baseDir, true, null, null)
                   }
                   Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Starting unzip to $baseDir")

                   // Create temporary ZIP file
                   val timestamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
                   val tempZipPath = "$baseDir/temp_$timestamp.zip"

                   // Write ZIP data to temporary file
                   val zipData = fileBytes.usePinned { pinned ->
                       NSData.create(bytes = pinned.addressOf(0), length = fileBytes.size.toULong())
                   }

                   if (zipData == null || !fileManager.createFileAtPath(tempZipPath, zipData, null)) {
                       Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to create temporary ZIP file")
                       return@withContext false
                   }

                   Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Created temporary ZIP file: $tempZipPath")

                   // Create temporary extraction directory
                   val tempExtractDir = "$baseDir/temp_extract_$timestamp"
                   if (!fileManager.createDirectoryAtPath(tempExtractDir, true, null, null)) {
                       Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to create temp extract directory")
                       fileManager.removeItemAtPath(tempZipPath, null)
                       return@withContext false
                   }

                   Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Created temp extract dir: $tempExtractDir")

                   // Extract ZIP file directly from ByteArray
                   val success = extractZipFile(fileBytes, tempExtractDir, fileManager)

                   // Clean up temp ZIP file
                   fileManager.removeItemAtPath(tempZipPath, null)

                   if (!success) {
                       Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to extract ZIP file")
                       fileManager.removeItemAtPath(tempExtractDir, null)
                       return@withContext false
                   }

                   // Find manifest.json and determine content root
                   var manifestPath = "$tempExtractDir/manifest.json"
                   var contentRoot = tempExtractDir

                   if (!fileManager.fileExistsAtPath(manifestPath)) {
                       // Look in subdirectories
                       val contents = fileManager.contentsOfDirectoryAtPath(tempExtractDir, null)
                           ?.filterIsInstance<NSString>()
                           ?.map { it.toString() } ?: emptyList()

                       val subdirs = contents.filter { dirname ->
                           fileManager.contentsOfDirectoryAtPath("$tempExtractDir/$dirname", null) != null
                       }

                       if (subdirs.size == 1) {
                           val subManifestPath = "$tempExtractDir/${subdirs[0]}/manifest.json"
                           if (fileManager.fileExistsAtPath(subManifestPath)) {
                               manifestPath = subManifestPath
                               contentRoot = "$tempExtractDir/${subdirs[0]}"
                           }
                       }
                   }

                   if (!fileManager.fileExistsAtPath(manifestPath)) {
                       Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "manifest.json not found in extracted files")
                       fileManager.removeItemAtPath(tempExtractDir, null)
                       return@withContext false
                   }

                   // Read and parse manifest
                   val manifestData = fileManager.contentsAtPath(manifestPath) as? NSData
                   val manifestText = manifestData?.let { NSString.create(it, NSUTF8StringEncoding)?.toString() }

                   if (manifestText.isNullOrBlank()) {
                       Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "manifest.json empty/unreadable")
                       fileManager.removeItemAtPath(tempExtractDir, null)
                       return@withContext false
                   }

                   val json = Json.parseToJsonElement(manifestText).jsonObject
                   val policy = json["policy"]?.jsonPrimitive?.content
                   val config = json["config"]?.jsonPrimitive?.content

                   if (policy.isNullOrBlank() || config.isNullOrBlank()) {
                       Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "manifest missing policy/config")
                       fileManager.removeItemAtPath(tempExtractDir, null)
                       return@withContext false
                   }

                   // Create target directory and move files
                   val targetDir = "$baseDir/$policy/$config"
                   if (!fileManager.createDirectoryAtPath(targetDir, true, null, null)) {
                       Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to create target directory")
                       fileManager.removeItemAtPath(tempExtractDir, null)
                       return@withContext false
                   }

                   Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Moving extracted files to $targetDir")

                   val contents = fileManager.contentsOfDirectoryAtPath(contentRoot, null)
                       ?.filterIsInstance<NSString>()
                       ?.map { it.toString() } ?: emptyList()

                   var filesMoved = 0
                   for (item in contents) {
                       val src = "$contentRoot/$item"
                       val dest = "$targetDir/$item"
                       if (fileManager.moveItemAtPath(src, dest, null)) {
                           filesMoved++
                       } else {
                           Logger.log("CIRCLES_MANAGER", LogLevel.WARN, "Failed to move item $item")
                       }
                   }

                   // Clean up temp directory
                   fileManager.removeItemAtPath(tempExtractDir, null)
                   Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Unzip complete, $filesMoved files moved to $targetDir")

                   return@withContext true

               } catch (e: Exception) {
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Unzip failed: ${e.message}", e)
        false
               }
           }


           /**
            * Extract ZIP file from ByteArray directly
            * Handles uncompressed files only (compression method 0)
            */
           @OptIn(ExperimentalForeignApi::class)
           private fun extractZipFile(zipBytes: ByteArray, extractPath: String, fileManager: NSFileManager): Boolean {
               var position = 0
               var filesExtracted = 0

               Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Starting ZIP extraction, file size: ${zipBytes.size} bytes")

               // Log first 16 bytes for debugging
               val firstBytes = zipBytes.take(16).joinToString(" ") { "0x${it.toUByte().toString(16).padStart(2, '0')}" }
               Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "First 16 bytes: $firstBytes")

               // Check if this looks like a valid ZIP file
               if (zipBytes.size < 4) {
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "ZIP file too small: ${zipBytes.size} bytes (minimum 4 bytes required)")
                   return false
               }

               // Check for common ZIP signatures
               val firstSignature = zipBytes.readUIntLE(0)
               Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "First 4-byte signature: 0x${firstSignature.toString(16)}")

               if (firstSignature == 0x04034b50u) {
                   Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "✓ Valid ZIP file starts with Local File Header")
               } else if (firstSignature == 0x504b0304u) {
                   Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "✓ Valid ZIP file starts with Local File Header (big-endian)")
               } else if (firstSignature == 0x02014b50u) {
                   Logger.log("CIRCLES_MANAGER", LogLevel.WARN, "ZIP file starts with Central Directory Header (unusual but valid)")
               } else if (firstSignature == 0x06054b50u) {
                   Logger.log("CIRCLES_MANAGER", LogLevel.WARN, "ZIP file starts with End of Central Directory (damaged?)")
               } else if (zipBytes.size >= 2 && zipBytes[0].toInt() == 0x1f && zipBytes[1].toInt() == 0x8b) {
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "This appears to be a GZIP file (0x1f 0x8b), not a ZIP file")
                   return false
               } else if (zipBytes.size >= 4 && zipBytes.take(4).all { it == 0.toByte() }) {
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "First 4 bytes are all zeros - this is not a valid ZIP file")
                   return false
               } else {
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Unrecognized file format. Expected ZIP signatures:")
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "  - Local File Header: 0x04034b50 or 0x504b0304")
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "  - Central Directory: 0x02014b50")
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "  - End of Central Dir: 0x06054b50")
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "  Found: 0x${firstSignature.toString(16)}")
                   return false
               }

               while (position < zipBytes.size) {
                   if (zipBytes.size - position < 4) {
                       Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Unexpected end of ZIP file at position $position")
                       return false
                   }

                   val signature = zipBytes.readUIntLE(position)
                   val sigBytes = zipBytes.slice(position until position + 4).joinToString(" ") { "0x${it.toUByte().toString(16).padStart(2, '0')}" }
                   Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Found signature at position $position: $sigBytes (0x${signature.toString(16)})")

                   when (signature) {
                       0x04034b50u -> {
                           // Local File Header - process the file
                           Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Processing Local File Header at position $position")
                           position += 4

                           val version = zipBytes.readUShortLE(position)
                           position += 2
                           val flags = zipBytes.readUShortLE(position)
                           position += 2

                           if (flags.toInt() and 8 != 0) {
                               Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Data descriptors not supported (flag bit 3 set)")
                               return false
                           }

                           if (flags.toInt() and 1 != 0) {
                               Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Encrypted files not supported (flag bit 0 set)")
                               return false
                           }

                           val method = zipBytes.readUShortLE(position)
                           position += 2
                           position += 4 // time + date
                           val crcExpected = zipBytes.readUIntLE(position)
                           position += 4
                           val compressedSize = zipBytes.readUIntLE(position)
                           position += 4
                           val uncompressedSize = zipBytes.readUIntLE(position)
                           position += 4
                           val nameLen = zipBytes.readUShortLE(position)
                           position += 2
                           val extraLen = zipBytes.readUShortLE(position)
                           position += 2

                           // Validate header values
                           if (nameLen.toInt() == 0) {
                               Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "File entry has no name (nameLen = 0)")
                               return false
                           }

                           if (method.toInt() != 0 && method.toInt() != 8) {
                               Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Unsupported compression method: ${method.toInt()} (only 0=Store and 8=Deflate supported)")
                               return false
                           }

                           // Check if we have enough data for the file content
                           val dataStart = position + nameLen.toInt() + extraLen.toInt()
                           val dataEnd = dataStart + compressedSize.toInt()
                           if (dataEnd > zipBytes.size) {
                               Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "File data extends beyond ZIP file (dataEnd=$dataEnd, fileSize=${zipBytes.size})")
                               return false
                           }

                           val nameBytes = zipBytes.copyOfRange(position, position + nameLen.toInt())
                           val name = nameBytes.toKStringFromUtf8()
                           position += nameLen.toInt()
                           position += extraLen.toInt() // skip extra

                           val fullPath = "$extractPath/$name"
                           Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Processing file: '$name' (method: $method, compressed: $compressedSize bytes, uncompressed: $uncompressedSize bytes)")

                           if (name.endsWith("/")) {
                               // Directory
                               if (compressedSize != 0u || uncompressedSize != 0u) {
                                   Logger.log("CIRCLES_MANAGER", LogLevel.WARN, "Invalid directory entry: $name")
                               }
                               if (!fileManager.createDirectoryAtPath(fullPath, true, null, null)) {
                                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to create directory: $fullPath")
                                   return false
                               }
                               Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Created directory: $fullPath")
                           } else {
                               // File
                               val parentDir = fullPath.substringBeforeLast("/")
                               if (!fileManager.fileExistsAtPath(parentDir)) {
                                   if (!fileManager.createDirectoryAtPath(parentDir, true, null, null)) {
                                       Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to create parent directory: $parentDir")
                                       return false
                                   }
                               }

                               if (!fileManager.createFileAtPath(fullPath, null, null)) {
                                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to create file: $fullPath")
                                   return false
                               }

                               val handle = NSFileHandle.fileHandleForWritingAtPath(fullPath)
                               if (handle == null) {
                                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to open file handle: $fullPath")
                                   return false
                               }

                               try {
                                   val success = when (method.toInt()) {
                                       0 -> storeData(zipBytes, position, compressedSize.toInt(), crcExpected, handle)
                                       8 -> deflateData(zipBytes, position, compressedSize.toInt(), uncompressedSize.toInt(), crcExpected, handle)
                                       else -> {
                                           Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Unsupported compression method: ${method.toInt()} for file: $name")
                                           false
                                       }
                                   }
                                   if (!success) {
                                       return false
                                   }
                               } finally {
                                   handle.closeFile()
                               }

                               filesExtracted++
                               if (filesExtracted <= 5) {
                                   Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Extracted $fullPath (${uncompressedSize} bytes)")
                               }
                           }

                           position += compressedSize.toInt()
                       }
                       0x02014b50u -> {
                           // Central Directory Header - skip it
                           Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Skipping Central Directory Header at position $position")
                           if (zipBytes.size - position < 46) {
                               Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Invalid Central Directory Header (need 46 bytes, have ${zipBytes.size - position})")
                               return false
                           }
                           val nameLen = zipBytes.readUShortLE(position + 28)
                           val extraLen = zipBytes.readUShortLE(position + 30)
                           val commentLen = zipBytes.readUShortLE(position + 32)
                           val skipBytes = 46 + nameLen.toInt() + extraLen.toInt() + commentLen.toInt()
                           Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Skipping $skipBytes bytes (name: ${nameLen}, extra: ${extraLen}, comment: ${commentLen})")
                           position += skipBytes
                           continue
                       }
                       0x06054b50u -> {
                           // End of Central Directory - we're done with file headers
                           Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Found End of Central Directory at position $position - extraction complete")
                           break
                       }
                       else -> {
                           Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Unknown signature 0x${signature.toString(16)} at position $position")
                           Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Expected one of: 0x04034b50 (Local File), 0x02014b50 (Central Dir), 0x06054b50 (End of Central Dir)")

                           // Show next few bytes for debugging
                           val nextBytes = minOf(16, zipBytes.size - position)
                           val nextHex = zipBytes.slice(position until position + nextBytes).joinToString(" ") { "0x${it.toUByte().toString(16).padStart(2, '0')}" }
                           Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Next $nextBytes bytes: $nextHex")

                           // Try to find next valid signature
                           Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Searching for next valid signature...")
                           var found = false
                           var searchPos = position + 1
                           while (searchPos <= zipBytes.size - 4) {
                               val testSig = zipBytes.readUIntLE(searchPos)
                               if (testSig == 0x04034b50u || testSig == 0x02014b50u || testSig == 0x06054b50u) {
                                   Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Found valid signature 0x${testSig.toString(16)} at position $searchPos (skipped ${searchPos - position} bytes)")
                                   position = searchPos
                                   found = true
                                   break
                               }
                               searchPos++
                           }
                           if (!found) {
                               Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "No valid ZIP signatures found in remaining ${zipBytes.size - position} bytes")
                               Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "This file may not be a valid ZIP archive or may be corrupted")
                               return false
                           }
                           continue
                       }
                   }
               }

               if (filesExtracted == 0) {
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "ZIP extraction completed but no files were extracted")
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "This may indicate:")
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "  - The ZIP file contains no files")
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "  - All files were filtered out (directories only)")
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "  - The ZIP file structure is corrupted")
                   return false
               } else {
                   Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "ZIP extraction completed successfully: $filesExtracted files extracted")
                   return true
               }
           }

           /**
            * Handle uncompressed (Store) data
            */
           private fun storeData(zipBytes: ByteArray, offset: Int, size: Int, crcExpected: UInt, handle: NSFileHandle): Boolean {
               try {
                   // Extract the data
                   val dataBytes = zipBytes.copyOfRange(offset, offset + size)

                   // Calculate CRC32 for uncompressed data
                   var calculatedCrc = 0xFFFFFFFFu  // Initialize CRC32 to standard starting value
                   val crc32Table = generateCrc32Table()
                   for (byte in dataBytes) {
                       val tableIndex = (calculatedCrc.toInt() xor byte.toInt()) and 0xFF
                       calculatedCrc = (calculatedCrc shr 8) xor crc32Table[tableIndex]
                   }
                   calculatedCrc = calculatedCrc xor 0xFFFFFFFFu  // Finalize CRC32

                   if (calculatedCrc != crcExpected) {
                       Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "CRC mismatch: expected $crcExpected (0x${crcExpected.toString(16)}), got $calculatedCrc (0x${calculatedCrc.toString(16)})")
                       return false
                   }

                   // Write data to file
                   val nsData = dataBytes.usePinned { pinned ->
                       NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
                   }

                   if (nsData != null) {
                       handle.writeData(nsData)
                       return true
                   } else {
                       Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to create NSData for writing")
                       return false
                   }
               } catch (e: Exception) {
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Exception in storeData: ${e.message}")
                   return false
               }
           }

           /**
            * Generate CRC32 lookup table
            */
           private fun generateCrc32Table(): UIntArray {
               val table = UIntArray(256)
               val polynomial = 0xEDB88320u

               for (i in 0..255) {
                   var crc = i.toUInt()
                   for (j in 0..7) {
                       crc = if ((crc and 1u) != 0u) {
                           (crc shr 1) xor polynomial
                       } else {
                           crc shr 1
                       }
                   }
                   table[i] = crc
               }
               return table
           }

           /**
            * Handle Deflate compressed data using KmpIO-style zlib implementation
            */
           @OptIn(ExperimentalForeignApi::class)
           private fun deflateData(zipBytes: ByteArray, offset: Int, compressedSize: Int, uncompressedSize: Int, crcExpected: UInt, handle: NSFileHandle): Boolean {
               try {
                   // Extract compressed data
                   val compressedData = zipBytes.copyOfRange(offset, offset + compressedSize)

                   // Set up zlib stream for decompression (inflate)
                   val windowBits = -15 // Raw deflate, no header
                   val chunk = 16384

                   memScoped {
                       alloc<z_stream>().apply {
                           zalloc = null
                           zfree = null
                           opaque = null
                           avail_in = 0u
                           next_in = null

                           val err = inflateInit2_(ptr, windowBits, ZLIB_VERSION, sizeOf<z_stream>().toInt())
                           if (err != Z_OK) {
                               Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "inflateInit2 failed: $err")
                               return false
                           }

                           val outArray = UByteArray(chunk)
                           val outArraySize = outArray.size.toUInt()
                           var totalOut = 0uL
                           var calculatedCrc = 0xFFFFFFFFu  // Initialize CRC32 to standard starting value

                           val crc32Table = generateCrc32Table()

                           try {
                               // Set up input data
                               compressedData.usePinned { pinned ->
                                   next_in = pinned.addressOf(0).reinterpret<UByteVar>()
                                   avail_in = compressedData.size.toUInt()
                               }

                               outArray.usePinned { outPinned ->
                                   next_out = outPinned.addressOf(0)
                                   avail_out = outArraySize

                                   var ret: Int
                                   do {
                                       ret = inflate(ptr, Z_NO_FLUSH)
                                       if (ret != Z_OK && ret != Z_STREAM_END) {
                                           Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Inflate error: $ret")
                                           return false
                                       }

                                       val have = (outArraySize - avail_out).toInt()
                                       if (have > 0) {
                                           // Update CRC32
                                           for (i in 0 until have) {
                                               val byte = outArray[i].toByte()
                                               val tableIndex = (calculatedCrc.toInt() xor byte.toInt()) and 0xFF
                                               calculatedCrc = (calculatedCrc shr 8) xor crc32Table[tableIndex]
                                           }

                                           // Write decompressed data to file
                                           val outputBytes = outArray.copyOfRange(0, have).toByteArray()
                                           outputBytes.usePinned { outputPinned ->
                                               val nsData = NSData.create(bytes = outputPinned.addressOf(0), length = have.toULong())
                                               if (nsData != null) {
                                                   handle.writeData(nsData)
                                               } else {
                                                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to create NSData for writing")
                                                   return false
                                               }
                                           }

                                           totalOut += have.toULong()
                                           next_out = outPinned.addressOf(0)
                                           avail_out = outArraySize
                                       }
                                   } while (ret != Z_STREAM_END && totalOut < uncompressedSize.toULong())
                               }

                               inflateEnd(ptr)

                               // Finalize CRC32 (XOR with 0xFFFFFFFF)
                               calculatedCrc = calculatedCrc xor 0xFFFFFFFFu

                               // Verify output size and CRC
                               if (totalOut != uncompressedSize.toULong()) {
                                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Size mismatch: expected $uncompressedSize, got $totalOut")
                                   return false
                               }

                               if (calculatedCrc != crcExpected) {
                                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "CRC mismatch: expected $crcExpected (0x${crcExpected.toString(16)}), got $calculatedCrc (0x${calculatedCrc.toString(16)})")
                                   return false
                               }

                               Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Deflate decompression successful: $totalOut bytes")
                               return true

                           } catch (e: Exception) {
                               inflateEnd(ptr)
                               Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Exception during deflate decompression: ${e.message}")
                               return false
                           }
                       }
                   }
               } catch (e: Exception) {
                   Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Deflate decompression failed: ${e.message}")
                   return false
               }
           }

           /**
            * Helper extensions for reading data
            */
           private fun ByteArray.readUShortLE(offset: Int): UShort =
               ((this[offset + 1].toUByte().toUInt() shl 8) or this[offset].toUByte().toUInt()).toUShort()

           private fun ByteArray.readUIntLE(offset: Int): UInt =
               (readUShortLE(offset + 2).toUInt() shl 16) or readUShortLE(offset).toUInt()

           /**
            * Convert UTF-8 bytes to String (Kotlin/Native compatible)
            */
           private fun ByteArray.toKStringFromUtf8(): String {
               // Simple UTF-8 to String conversion for ZIP filenames
               val nullIndex = indexOf(0.toByte())
               val actualBytes = if (nullIndex >= 0) copyOfRange(0, nullIndex) else this
               return actualBytes.decodeToString()
    }
}
