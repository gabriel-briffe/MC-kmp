package org.mountaincircles.app.modules.airspace.import.logic

import org.mountaincircles.app.io.FileManager
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceData
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceFeature
import org.mountaincircles.app.modules.airspace.logic.data.GeoJsonGeometry
import org.mountaincircles.app.modules.airspace.logic.AirspaceConstants

/**
 * Simple airspace storage for saving/loading parsed airspace data
 */
class AirspaceStorage(private val fileManager: FileManager) {

    /**
     * Metadata-only structure to avoid duplicating features in JSON
     * Contains essential info without the large feature array
     */
    @Serializable
    data class AirspaceMetadata(
        val availableTypes: Set<String>,
        val lastUpdated: Long,
        val featureCount: Int,
        val geoJsonFile: String  // Reference to the GeoJSON file containing features
    )

    // Standard GeoJSON FeatureCollection for airspace data
    @Serializable
    data class StandardGeoJsonFeatureCollection(
        val type: String = "FeatureCollection",
        val features: List<GeoJsonFeature>
    )

    @Serializable
    data class GeoJsonFeature(
        val type: String = "Feature",
        val properties: AirspaceProperties,
        val geometry: GeoJsonGeometry // Use existing GeoJsonGeometry
    )

    @Serializable
    data class AirspaceProperties(
        val type: String,
        val name: String?,
        val upperLimit: String?,
        val lowerLimit: String?,
        val upperLimitMeters: Int?,
        val lowerLimitMeters: Int?,
        val AI: String?,
        val AG: String?,
        val AF: String?
    )


    companion object {
        // 🚀 COMPACT JSON: Optimized for file size (no whitespace, no line breaks)
        private val compactJson = Json {
            prettyPrint = false  // ✅ No indentation, no line breaks, maximum compactness
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }



    /**
     * Check if airspace data exists (file-based, no deserialization)
     */
    suspend fun hasAirspaceData(): Boolean {
        return try {
            val dataDir = fileManager.getAppDataDirectory()
            val airspaceDir = "$dataDir/${AirspaceConstants.AIRSPACE_DIR}"
            val geoJsonFilePath = "$airspaceDir/${AirspaceConstants.AIRSPACE_GEOJSON_FILE}"
            fileManager.exists(geoJsonFilePath)
        } catch (e: Exception) {
            Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Exception checking airspace data: ${e.message}", e)
            false
        }
    }

    /**
     * Load airspace metadata only (no features deserialization)
     */
    suspend fun loadAirspaceMetadata(): Result<AirspaceMetadata?> {
        return try {
            Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Loading airspace metadata")

            val dataDir = fileManager.getAppDataDirectory()
            val airspaceDir = "$dataDir/${AirspaceConstants.AIRSPACE_DIR}"
            val metadataFilePath = "$airspaceDir/${AirspaceConstants.AIRSPACE_METADATA_FILE}"

            if (fileManager.exists(metadataFilePath)) {
                val jsonString = fileManager.readText(metadataFilePath)
                if (jsonString != null) {
                    val metadata = compactJson.decodeFromString<AirspaceMetadata>(jsonString)
                    Logger.log("AIRSPACE_STORAGE", LogLevel.INFO, "Airspace metadata loaded: ${metadata.featureCount} features, ${metadata.availableTypes.size} available types")
                    return Result.success(metadata)
                }
            }

            Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "No airspace metadata found")
            Result.success(null)
        } catch (e: Exception) {
            Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Exception loading airspace metadata: ${e.message}", e)
            Result.failure(e)
        }
    }



    /**
     * Ensure airspace directory exists
     */
    suspend fun ensureAirspaceDirectory(): Result<Unit> {
        return try {
            Logger.log("AIRSPACE_STORAGE", LogLevel.INFO, "Ensuring airspace directory exists")

        val dataDir = fileManager.getAppDataDirectory()
        val airspaceDir = "$dataDir/${AirspaceConstants.AIRSPACE_DIR}"
        val success = fileManager.createDirectory(airspaceDir)

            if (success) {
                Logger.log("AIRSPACE_STORAGE", LogLevel.INFO, "Airspace directory ready: $airspaceDir")
                Result.success(Unit)
            } else {
                Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Failed to create airspace directory")
                Result.failure(Exception("Failed to create airspace directory"))
            }
        } catch (e: Exception) {
            Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Exception ensuring airspace directory: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Merge country features to GeoJSON file
     */
    suspend fun mergeCountryFeaturesToGeoJson(
        fileName: String,
        features: List<AirspaceFeature>,
        isFirstCountry: Boolean = false
    ): Result<Unit> {
        return try {
            Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Merging ${features.size} features to GeoJSON: $fileName")

            if (features.isEmpty()) {
                Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "No features to merge")
                return Result.success(Unit)
            }

            val dataDir = fileManager.getAppDataDirectory()
            val airspaceDir = "$dataDir/${AirspaceConstants.AIRSPACE_DIR}"
            val geoJsonFilePath = "$airspaceDir/$fileName"

            // Convert features to GeoJSON format
            val geoJsonFeatures = features.map { feature ->
                val upperLimitMeters = convertAltitudeToMeters(feature.upperLimit)
                val lowerLimitMeters = convertAltitudeToMeters(feature.lowerLimit)

                val properties = AirspaceProperties(
                    type = feature.type,
                    name = feature.name,
                    upperLimit = feature.upperLimit,
                    lowerLimit = feature.lowerLimit,
                    upperLimitMeters = upperLimitMeters,
                    lowerLimitMeters = lowerLimitMeters,
                    AI = feature.ai,
                    AG = feature.ag,
                    AF = feature.af
                )

                GeoJsonFeature(
                    properties = properties,
                    geometry = feature.geometry
                )
            }

            // Serialize features using StandardGeoJsonFeatureCollection for consistent cross-platform serialization
            val featureCollection = StandardGeoJsonFeatureCollection(features = geoJsonFeatures)
            val featuresJson = compactJson.encodeToString(featureCollection)
            Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Serialized FeatureCollection JSON: ${featuresJson.take(300)}...")

            val success = if (isFirstCountry) {
                // First country: write complete FeatureCollection
                Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Writing complete FeatureCollection for first country")
                fileManager.writeText(geoJsonFilePath, featuresJson)
            } else {
                // Subsequent countries: merge with existing FeatureCollection
                Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Merging features with existing FeatureCollection")
                val existingContent = fileManager.readText(geoJsonFilePath) ?: ""
                if (existingContent.isNotEmpty()) {
                    try {
                        // Parse existing FeatureCollection
                        val existingCollection = compactJson.decodeFromString<StandardGeoJsonFeatureCollection>(existingContent)
                        // Merge features
                        val mergedFeatures = existingCollection.features + geoJsonFeatures
                        val mergedCollection = StandardGeoJsonFeatureCollection(features = mergedFeatures)
                        val mergedJson = compactJson.encodeToString(mergedCollection)
                        fileManager.writeText(geoJsonFilePath, mergedJson)
                    } catch (e: Exception) {
                        Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Failed to merge FeatureCollections: ${e.message}", e)
                        false
                    }
                } else {
                    // Fallback: write as new collection
                    fileManager.writeText(geoJsonFilePath, featuresJson)
                }
            }

            if (success) {
                Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Successfully merged ${features.size} features to GeoJSON")
                Result.success(Unit)
            } else {
                Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Failed to merge features to GeoJSON")
                Result.failure(Exception("Failed to merge features to GeoJSON"))
            }
        } catch (e: Exception) {
            Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Exception merging features to GeoJSON: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Validate GeoJSON file structure
     */
    suspend fun validateGeoJsonFile(fileName: String): Result<Unit> {
        return try {
            Logger.log("AIRSPACE_STORAGE", LogLevel.INFO, "Validating GeoJSON file: $fileName")

            val dataDir = fileManager.getAppDataDirectory()
            val airspaceDir = "$dataDir/${AirspaceConstants.AIRSPACE_DIR}"
            val geoJsonFilePath = "$airspaceDir/$fileName"

            Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Finalization file path: $geoJsonFilePath")
            Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Data dir: $dataDir, airspace dir: $airspaceDir")

            // Validate the final JSON structure
            val finalContent = fileManager.readText(geoJsonFilePath)
            Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Final content read result: ${finalContent?.length ?: 0} characters")
            if (finalContent != null && finalContent.isNotEmpty()) {
                Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Final content starts with: ${finalContent.take(200)}")
                try {
                    // Try to parse as StandardGeoJsonFeatureCollection to validate
                    compactJson.decodeFromString<StandardGeoJsonFeatureCollection>(finalContent)
                    Logger.log("AIRSPACE_STORAGE", LogLevel.INFO, "GeoJSON file validation successful: $fileName")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "GeoJSON file validation failed: ${e.message}", e)
                    Result.failure(Exception("Invalid GeoJSON structure: ${e.message}"))
                }
            } else {
                Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "GeoJSON file is empty or null: content=$finalContent")
                Result.failure(Exception("GeoJSON file is empty"))
            }
        } catch (e: Exception) {
            Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Exception finalizing GeoJSON: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update incremental metadata file
     */
    suspend fun updateIncrementalMetadata(
        fileName: String,
        availableTypes: Set<String>,
        totalFeatures: Int,
        lastUpdated: Long
    ): Result<Unit> {
        return try {
            Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Updating incremental metadata: $totalFeatures features, ${availableTypes.size} types")

            val dataDir = fileManager.getAppDataDirectory()
            val airspaceDir = "$dataDir/${AirspaceConstants.AIRSPACE_DIR}"
            fileManager.createDirectory(airspaceDir)

            val metadataFilePath = "$airspaceDir/${AirspaceConstants.AIRSPACE_METADATA_FILE}"

            val metadata = AirspaceMetadata(
                availableTypes = availableTypes,
                lastUpdated = lastUpdated,
                featureCount = totalFeatures,
                geoJsonFile = fileName
            )

            val metadataJson = compactJson.encodeToString(metadata)
            val success = fileManager.writeText(metadataFilePath, metadataJson)

            if (success) {
                Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Incremental metadata updated successfully")
                Result.success(Unit)
            } else {
                Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Failed to update incremental metadata")
                Result.failure(Exception("Failed to update incremental metadata"))
            }
        } catch (e: Exception) {
            Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Exception updating incremental metadata: ${e.message}", e)
            Result.failure(e)
        }
    }




    /**
     * Convert altitude string to meters (same as Android native)
     */
    private fun convertAltitudeToMeters(alt: String?): Int? {
        if (alt == null) return null
        val s = alt.uppercase().trim()
        return when {
            s.startsWith("FL") -> s.substring(2).toDoubleOrNull()?.let { (it * 100 * 0.3048).toInt() }
            Regex("^(\\d+(?:.\\d+)?)FT").containsMatchIn(s) -> Regex("^(\\d+(?:.\\d+)?)").find(s)?.groupValues?.get(1)?.toDoubleOrNull()?.times(0.3048)?.toInt()
            Regex("^(\\d+(?:.\\d+)?)M").containsMatchIn(s) -> Regex("^(\\d+(?:.\\d+)?)").find(s)?.groupValues?.get(1)?.toDoubleOrNull()?.toInt()
            s == "GND" -> 0
            else -> null
        }
    }


    /**
     * Read airspace data as string for layer rendering
     */
    suspend fun readAirspaceDataAsString(): String? {
        return try {
            // Check for the GeoJSON file that we save
            val dataDir = fileManager.getAppDataDirectory()
            val airspaceDir = "$dataDir/${AirspaceConstants.AIRSPACE_DIR}"
            val filePath = "$airspaceDir/${AirspaceConstants.AIRSPACE_GEOJSON_FILE}"

            if (fileManager.exists(filePath)) {
                fileManager.readText(filePath)
            } else {
                Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Airspace file not found: $filePath")
                null
            }
        } catch (e: Exception) {
            Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Exception reading airspace data as string: ${e.message}", e)
            null
        }
    }

    /**
     * Clear airspace data - deletes ALL airspace files including cache
     */
    suspend fun clearAirspaceData(): Result<Unit> {
        return try {
            Logger.log("AIRSPACE_STORAGE", LogLevel.INFO, "Clearing all airspace data and cache")

            val dataDir = fileManager.getAppDataDirectory()
            val airspaceDir = "$dataDir/${AirspaceConstants.AIRSPACE_DIR}"

            // Delete main GeoJSON file
            val geoJsonPath = "$airspaceDir/${AirspaceConstants.AIRSPACE_GEOJSON_FILE}"
            val geoJsonSuccess = fileManager.delete(geoJsonPath)
            Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Deleted main GeoJSON file: $geoJsonSuccess")

            // Delete metadata JSON file
            val metadataPath = "$airspaceDir/${AirspaceConstants.AIRSPACE_METADATA_FILE}"
            val metadataSuccess = fileManager.delete(metadataPath)
            Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Deleted metadata JSON file: $metadataSuccess")


            // Delete entire cache directory and all filtered files
            val cacheDir = "$airspaceDir/${AirspaceConstants.CACHE_DIR}"
            val cacheSuccess = deleteDirectoryRecursively(cacheDir)
            Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Deleted cache directory: $cacheSuccess")

            // Check if any deletion succeeded (don't fail if files don't exist)
            val anySuccess = geoJsonSuccess || metadataSuccess || cacheSuccess

            if (anySuccess) {
                Logger.log("AIRSPACE_STORAGE", LogLevel.INFO, "All airspace data and cache cleared successfully")
                Result.success(Unit)
            } else {
                Logger.log("AIRSPACE_STORAGE", LogLevel.WARN, "No airspace files found to clear")
                Result.success(Unit) // Not an error if nothing existed
            }
        } catch (e: Exception) {
            Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Exception clearing airspace data: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Recursively delete a directory and all its contents
     */
    private suspend fun deleteDirectoryRecursively(dirPath: String): Boolean {
        return try {
            if (!fileManager.exists(dirPath)) {
                Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Directory does not exist: $dirPath")
                return true // Not an error if directory doesn't exist
            }

            // Get all files and subdirectories in the directory
            val files = fileManager.listFiles(dirPath)
            Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Deleting ${files.size} items from directory: $dirPath")

            // Delete all files and subdirectories
            var allDeleted = true
            for (file in files) {
                val filePath = "$dirPath/$file"
                if (fileManager.exists(filePath)) {
                    // Try to delete as file first
                    val fileDeleted = fileManager.delete(filePath)
                    if (!fileDeleted) {
                        // If file deletion failed, try recursive directory deletion
                        val dirDeleted = deleteDirectoryRecursively(filePath)
                        if (!dirDeleted) {
                            Logger.log("AIRSPACE_STORAGE", LogLevel.WARN, "Failed to delete: $filePath")
                            allDeleted = false
                        }
                    }
                }
            }

            // If all contents are deleted, delete the directory itself
            if (allDeleted) {
                // Note: FileManager.delete() may not work on empty directories on some platforms
                // We'll consider the operation successful if we deleted all contents
                Logger.log("AIRSPACE_STORAGE", LogLevel.DEBUG, "Successfully deleted all contents of: $dirPath")
                true
            } else {
                Logger.log("AIRSPACE_STORAGE", LogLevel.WARN, "Failed to delete some contents of: $dirPath")
                false
            }
        } catch (e: Exception) {
            Logger.log("AIRSPACE_STORAGE", LogLevel.ERROR, "Exception deleting directory recursively: ${e.message}", e)
            false
        }
    }
}
