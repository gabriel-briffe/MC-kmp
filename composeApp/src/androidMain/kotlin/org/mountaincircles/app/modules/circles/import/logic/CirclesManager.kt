package org.mountaincircles.app.modules.circles.import.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.logic.data.PackMetadata
import org.mountaincircles.app.modules.maps.getMapsDirectory
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Android implementation of CirclesManager
 * Handles circles pack file management on Android
 */
actual class CirclesManager {
    
    /**
     * Get the circles data directory path
     */
    actual fun getCirclesDirectory(): String {
        val mapsDir = getMapsDirectory() // Reuse maps directory helper
        val circlesDir = File(mapsDir.parentFile, "circles")
        if (!circlesDir.exists()) {
            circlesDir.mkdirs()
            Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Created circles directory: ${circlesDir.absolutePath}")
        }
        return circlesDir.absolutePath
    }
    
    /**
     * Check if a file exists
     */
    actual fun fileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }
    
    /**
     * Get list of installed circles packs
     */
    actual suspend fun getInstalledPacks(): List<String> = withContext(Dispatchers.IO) {
        try {
            val circlesDir = File(getCirclesDirectory())
            if (!circlesDir.exists()) return@withContext emptyList()
            
            // Get unique pack names (policy directories that have config subdirectories)
            val packs = circlesDir.listFiles()?.filter { it.isDirectory }
                ?.filter { packDir -> 
                    // Only include if it has config subdirectories
                    packDir.listFiles()?.any { it.isDirectory } == true
                }
                ?.map { it.name }
                ?: emptyList()
            
            Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Found ${packs.size} installed packs: ${packs.joinToString(", ")}")
            packs
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Error getting installed packs: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get list of available configurations for a pack
     */
    actual suspend fun getPackConfigurations(packId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val packDir = File(getCirclesDirectory(), packId)
            if (!packDir.exists()) return@withContext emptyList()
            
            val configs = packDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
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
    actual suspend fun deletePack(packId: String, configId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val circlesDir = getCirclesDirectory()
            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Attempting to delete pack config: $packId/$configId from circles dir: $circlesDir")

            val configDir = File(circlesDir, "$packId/$configId")
            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Config directory path: ${configDir.absolutePath}")
            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Config directory exists: ${configDir.exists()}")

            if (!configDir.exists()) {
                Logger.log("CIRCLES_MANAGER", LogLevel.WARN, "Config directory does not exist: $packId/$configId")
                return@withContext true // Consider as success if already doesn't exist
            }

            // List all files/directories in circles directory before deletion
            val circlesDirFile = File(circlesDir)
            val beforeDeletion = circlesDirFile.listFiles()?.map { it.name } ?: emptyList()
            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Files in circles dir BEFORE deletion: ${beforeDeletion.joinToString(", ")}")

            // List contents of the config directory
            val configContents = configDir.listFiles()?.map { it.name } ?: emptyList()
            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Contents of config $packId/$configId: ${configContents.joinToString(", ")}")

            val deleted = configDir.deleteRecursively()
            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Delete operation result: $deleted")

            if (deleted) {
                Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Successfully deleted pack config: $packId/$configId")

                // List all files/directories in circles directory after deletion
                val afterDeletion = circlesDirFile.listFiles()?.map { it.name } ?: emptyList()
                Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Files in circles dir AFTER deletion: ${afterDeletion.joinToString(", ")}")
            } else {
                Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to delete pack config: $packId/$configId")
            }
            deleted
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Error deleting pack config $packId/$configId: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get the total number of files for a pack configuration
     */
    actual suspend fun getPackConfigFileCount(packId: String, configId: String): Int = withContext(Dispatchers.IO) {
        try {
            val configDir = File(getCirclesDirectory(), "$packId/$configId")
            if (!configDir.exists()) return@withContext 0
            
            val count = configDir.listFiles()?.filter { it.isFile && it.name.endsWith(".geojson") }?.size ?: 0
            Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Pack $packId config $configId has $count files")
            count
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Error counting files for pack $packId config $configId: ${e.message}", e)
            0
        }
    }
    
    /**
     * Check if a specific pack configuration is completely installed
     */
    actual suspend fun isPackConfigInstalled(packId: String, configId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val configDir = File(getCirclesDirectory(), "$packId/$configId")
            if (!configDir.exists()) return@withContext false
            
            // Check for main files that should exist
            val mainFile = File(configDir, "aa_${packId}_${configId}.geojson")
            val sectorsFile = File(configDir, "aa_${packId}_${configId}_sectors1.geojson")
            
            val isInstalled = mainFile.exists() && sectorsFile.exists()
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
    actual suspend fun clearAllFiles(): Int = withContext(Dispatchers.IO) {
        try {
            val circlesDir = File(getCirclesDirectory())
            var deletedCount = 0
            
            if (circlesDir.exists()) {
                circlesDir.walkTopDown().forEach { file ->
                    if (file.isFile && file != circlesDir) {
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                }
                // Clean up empty directories
                circlesDir.walkBottomUp().forEach { dir ->
                    if (dir.isDirectory && dir != circlesDir && dir.listFiles()?.isEmpty() == true) {
                        dir.delete()
                    }
                }
            }
            
            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Cleared $deletedCount circles files")
            return@withContext deletedCount
            
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Error clearing circles files: ${e.message}")
            return@withContext 0
        }
    }
    
    /**
     * Read GeoJSON file content
     */
    actual suspend fun readGeoJsonFile(filePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                Logger.log("CIRCLES_MANAGER", LogLevel.DEBUG, "Reading GeoJSON file: $filePath")
                file.readText()
            } else {
                Logger.log("CIRCLES_MANAGER", LogLevel.WARN, "GeoJSON file not found: $filePath")
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
    actual suspend fun readPackMetadata(packId: String, configId: String): PackMetadata? = withContext(Dispatchers.IO) {
        try {
            val metadataFile = File(getCirclesDirectory(), "$packId/$configId/manifest.json")
            if (metadataFile.exists()) {
                val content = metadataFile.readText()
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
            } else {
                Logger.log("CIRCLES_MANAGER", LogLevel.WARN, "Manifest file not found: ${metadataFile.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to read metadata for $packId/$configId: ${e.message}")
            null
        }
    }
    
    /**
     * Get list of available configurations for a pack
     */
    actual suspend fun getAvailableConfigurations(packId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val packDir = File(getCirclesDirectory(), packId)
            if (packDir.exists() && packDir.isDirectory) {
                packDir.listFiles { file -> file.isDirectory }
                    ?.map { it.name }
                    ?.sorted()
                    ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Failed to get configurations for pack $packId: ${e.message}")
            emptyList()
        }
    }
    

    /**
     * Unzip a circles pack to the circles directory (cross-platform ByteArray version)
     */
    actual suspend fun unzipToCirclesDir(fileBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val inputStream = java.io.ByteArrayInputStream(fileBytes)

            val baseDir = File(getCirclesDirectory())
            if (!baseDir.exists()) baseDir.mkdirs()

            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Starting unzip to ${baseDir.absolutePath}")
            
            // 1) Extract to temp folder first
            val tmpDir = File(baseDir, "tmp_unpack_${System.currentTimeMillis()}")
            if (!tmpDir.exists()) tmpDir.mkdirs()
            
            var files = 0
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(tmpDir, entry.name)
                    if (entry.isDirectory) {
                        if (!outFile.exists()) outFile.mkdirs()
                    } else {
                        if (!outFile.parentFile.exists()) outFile.parentFile.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val r = zis.read(buf)
                                if (r <= 0) break
                                fos.write(buf, 0, r)
                            }
                        }
                        files++
                        if (files <= 5) {
                            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, 
                                "Extracted ${outFile.absolutePath} (${outFile.length()} bytes)")
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            
            // 2) Locate manifest.json inside temp (root or single subdir)
            var manifestFile = File(tmpDir, "manifest.json")
            var contentRoot = tmpDir
            if (!manifestFile.exists()) {
                // search depth-1
                val subdirs = tmpDir.listFiles { f -> f.isDirectory }?.toList() ?: emptyList()
                if (subdirs.size == 1) {
                    val m2 = File(subdirs[0], "manifest.json")
                    if (m2.exists()) {
                        manifestFile = m2
                        contentRoot = subdirs[0]
                    }
                }
            }
            if (!manifestFile.exists()) {
                Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "manifest.json not found in zip; cannot determine target folder")
                try { tmpDir.deleteRecursively() } catch (_: Throwable) {}
                return@withContext false
            }
            
            // 3) Parse policy/config from manifest
            val manifestText = try { manifestFile.readText() } catch (_: Throwable) { null }
            if (manifestText.isNullOrBlank()) {
                Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "manifest.json empty/unreadable")
                try { tmpDir.deleteRecursively() } catch (_: Throwable) {}
                return@withContext false
            }
            
            val json = Json.parseToJsonElement(manifestText).jsonObject
            val policy = json["policy"]?.jsonPrimitive?.content
            val config = json["config"]?.jsonPrimitive?.content
            
            if (policy.isNullOrBlank() || config.isNullOrBlank()) {
                Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "manifest missing policy/config")
                try { tmpDir.deleteRecursively() } catch (_: Throwable) {}
                return@withContext false
            }
            
            // 4) Move contents into circles/<policy>/<config>
            val targetDir = File(baseDir, "$policy/$config")
            if (!targetDir.exists()) targetDir.mkdirs()
            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Moving unpacked files to ${targetDir.absolutePath}")
            moveChildren(contentRoot, targetDir)
            
            // 5) Cleanup temp
            try { tmpDir.deleteRecursively() } catch (_: Throwable) {}
            Logger.log("CIRCLES_MANAGER", LogLevel.INFO, "Unzip complete, files extracted=$files to ${targetDir.absolutePath}")
            
            return@withContext true
            
        } catch (e: Exception) {
            Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, "Unzip failed: ${e.message}", e)
            return@withContext false
        }
    }
    
    /**
     * Move all children from source directory to target directory
     */
    private fun moveChildren(fromDir: File, toDir: File) {
        fromDir.listFiles()?.forEach { src ->
            val dest = File(toDir, src.name)
            if (src.isDirectory) {
                if (!dest.exists()) dest.mkdirs()
                moveChildren(src, dest)
            } else {
                // Try rename; fallback to copy
                val renamed = try { src.renameTo(dest) } catch (_: Throwable) { false }
                if (!renamed) {
                    try {
                        FileOutputStream(dest).use { out ->
                            src.inputStream().use { inp ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val r = inp.read(buf)
                                    if (r <= 0) break
                                    out.write(buf, 0, r)
                                }
                            }
                        }
                        src.delete()
                    } catch (t: Throwable) {
                        Logger.log("CIRCLES_MANAGER", LogLevel.ERROR, 
                            "Move file failed ${src.absolutePath} -> ${dest.absolutePath}: ${t.message}")
                    }
                }
            }
        }
    }
}
