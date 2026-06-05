package org.mountaincircles.app.modules.airports.import.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.mountaincircles.app.io.FileManager
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airports.logic.AirportsConstants
import org.mountaincircles.app.modules.airspace.logic.data.GeoJsonGeometry

// JSON serializer for GeoJSON operations
private val compactJson = Json { encodeDefaults = true }

// Airport type mappings
private val airportTypeMap = mapOf(
    0 to "Airport (civil/military)",
    1 to "Glider Site",
    2 to "Airfield Civil",
    3 to "International Airport",
    4 to "Heliport Military",
    5 to "Military Aerodrome",
    6 to "Ultra Light Flying Site",
    7 to "Heliport Civil",
    8 to "Aerodrome Closed",
    9 to "Airport resp. Airfield IFR",
    10 to "Airfield Water",
    11 to "Landing Strip",
    12 to "Agricultural Landing Strip",
    13 to "Altiport"
)

// Traffic type mappings
private val trafficTypeMap = mapOf(
    0 to "VFR",
    1 to "IFR"
)

// Standard GeoJSON FeatureCollection for airports data
@Serializable
data class AirportGeoJsonFeatureCollection(
    val type: String = "FeatureCollection",
    val features: List<AirportGeoJsonFeature>
)

@Serializable
data class AirportGeoJsonFeature(
    val type: String = "Feature",
    val id: JsonElement? = null, // Optional ID field that can be string, number, or null per GeoJSON spec
    val properties: Map<String, JsonElement> = emptyMap(), // Generic properties map that can contain any JSON value type
    val geometry: GeoJsonGeometry
)

/**
 * Minimal airports storage for download phase
 * Cloned from AirspaceStorage but simplified for download-only functionality
 */
class AirportsStorage(private val fileManager: FileManager) {

    /**
     * Ensure the airports directory exists
     * Called before starting downloads
     */
    suspend fun ensureAirportsDirectory(): Result<Unit> {
        return try {
            Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Ensuring airports directory exists")

            val dataDir = fileManager.getAppDataDirectory()
            val airportsDir = "$dataDir/${AirportsConstants.AIRPORTS_DIR}"

            // Create directory if it doesn't exist
            val success = fileManager.createDirectory(airportsDir)

            if (success) {
                Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Airports directory ready: $airportsDir")
                Result.success(Unit)
            } else {
                Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Failed to create airports directory: $airportsDir")
                Result.failure(Exception("Failed to create airports directory"))
            }
        } catch (e: Exception) {
            Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Error ensuring airports directory: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Save country-specific GeoJSON data to file
     */
    suspend fun saveCountryGeoJson(countryCode: String, geoJsonContent: String): Result<Unit> {
        return try {
            Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Saving GeoJSON data for country: $countryCode")

            val dataDir = fileManager.getAppDataDirectory()
            val airportsDir = "$dataDir/${AirportsConstants.AIRPORTS_DIR}"
            val fileName = "${countryCode}_airports.geojson"
            val filePath = "$airportsDir/$fileName"

            // Ensure directory exists
            ensureAirportsDirectory().getOrThrow()

            // Write the GeoJSON content to file
            val success = fileManager.writeText(filePath, geoJsonContent)

            if (success) {
                Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Successfully saved $fileName (${geoJsonContent.length} bytes)")
                Result.success(Unit)
            } else {
                Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Failed to write GeoJSON file: $filePath")
                Result.failure(Exception("Failed to write GeoJSON file"))
            }
        } catch (e: Exception) {
            Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Error saving GeoJSON for country $countryCode: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Clear all airports data and cache
     */
    suspend fun clearAirportsData(): Result<Unit> {
        return try {
            Logger.log("AIRPORTS_STORAGE", LogLevel.INFO, "Clearing all airports data and cache")

            val dataDir = fileManager.getAppDataDirectory()
            val airportsDir = "$dataDir/${AirportsConstants.AIRPORTS_DIR}"

            // Delete individual country GeoJSON files
            val files = fileManager.listFiles(airportsDir)
            var deletedCount = 0

            for (fileName in files) {
                if (fileName.endsWith("_airports.geojson")) {
                    val filePath = "$airportsDir/$fileName"
                    val deleted = fileManager.delete(filePath)
                    if (deleted) {
                        deletedCount++
                        Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Deleted country file: $fileName")
                    }
                }
            }

            // Delete main GeoJSON file (when it exists in future phases)
            val geoJsonPath = "$airportsDir/${AirportsConstants.AIRPORTS_GEOJSON_FILE}"
            val geoJsonSuccess = fileManager.delete(geoJsonPath)
            Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Deleted merged airports GeoJSON file: $geoJsonSuccess")

            // Delete metadata JSON file (when it exists in future phases)
            val metadataPath = "$airportsDir/${AirportsConstants.AIRPORTS_METADATA_FILE}"
            val metadataSuccess = fileManager.delete(metadataPath)
            Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Deleted metadata JSON file: $metadataSuccess")

            // Delete entire cache directory and all filtered files
            val cacheDir = "$airportsDir/${AirportsConstants.CACHE_DIR}"
            val cacheSuccess = deleteDirectoryRecursively(cacheDir)
            Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Deleted cache directory: $cacheSuccess")

            // Check if any deletion succeeded (don't fail if files don't exist)
            val anySuccess = geoJsonSuccess || metadataSuccess || (deletedCount > 0)

            if (anySuccess) {
                Logger.log("AIRPORTS_STORAGE", LogLevel.INFO, "All airports data cleared successfully (deleted $deletedCount country files)")
                Result.success(Unit)
            } else {
                Logger.log("AIRPORTS_STORAGE", LogLevel.INFO, "No airports data found to clear (directory may be empty)")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Error clearing airports data: ${e.message}", e)
            Result.failure(e)
        }
    }

/**
 * Process airport features to map numerical values to readable strings and remove unwanted properties
 */
private fun processAirportFeatures(features: List<AirportGeoJsonFeature>): List<AirportGeoJsonFeature> {
    // Define airport types to skip
    val skipTypes = setOf(4, 7, 8, 10) // Heliport Military, Heliport Civil, Aerodrome Closed, Airfield Water

    return features
        .filter { feature ->
            // Skip unwanted airport types
            val typeElement = feature.properties["type"] as? JsonPrimitive
            val typeInt = typeElement?.content?.toIntOrNull()
            val shouldInclude = typeInt !in skipTypes

            if (!shouldInclude) {
                // Skip unwanted airport types (heliports, etc.) - no logging needed
            }

            shouldInclude
        }
        .map { feature ->
            val processedProperties = feature.properties.toMutableMap()

        // Keep existing _id if present, otherwise set to ICAO code
        val existingId = processedProperties["_id"]
        if (existingId == null) {
            val icaoCode = processedProperties["icaoCode"] as? JsonPrimitive
            if (icaoCode != null) {
                processedProperties["_id"] = icaoCode
            }
        }
        // Remove unwanted properties (but keep _id)
        processedProperties.remove("magneticDeclination")
        processedProperties.remove("country")
        processedProperties.remove("createdAt")
        processedProperties.remove("updatedAt")
        processedProperties.remove("createdBy")
        processedProperties.remove("updatedBy")
        processedProperties.remove("services")

        // Map numerical values to readable strings
        val typeElement = processedProperties["type"] as? JsonPrimitive
        if (typeElement != null && typeElement.isString.not()) {
            val typeInt = typeElement.content.toIntOrNull()
            if (typeInt != null) {
                processedProperties["type"] = JsonPrimitive(airportTypeMap[typeInt] ?: "Unknown Type")
            }
        }

        val trafficArray = processedProperties["trafficType"] as? JsonArray
        if (trafficArray != null) {
            val mappedTrafficTypes = trafficArray.mapNotNull { item ->
                val primitive = item as? JsonPrimitive
                if (primitive != null && primitive.isString.not()) {
                    primitive.content.toIntOrNull()?.let { trafficTypeMap[it] }
                } else null
            }
            processedProperties["trafficType"] = JsonArray(mappedTrafficTypes.map { JsonPrimitive(it) })
        }


        feature.copy(properties = processedProperties)
    }
}

/**
 * Merge downloaded GeoJSON text directly into the main airports.geojson file
 */
    suspend fun mergeCountryGeoJsonTextToMainFile(
        countryGeoJsonText: String,
        isFirstCountry: Boolean = false
    ): Result<Unit> {
        return try {
            Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Merging country GeoJSON text to main airports file")

            val dataDir = fileManager.getAppDataDirectory()
            val airportsDir = "$dataDir/${AirportsConstants.AIRPORTS_DIR}"
            val mainFilePath = "$airportsDir/${AirportsConstants.AIRPORTS_GEOJSON_FILE}"

            // Parse the country GeoJSON
            val countryCollection = compactJson.decodeFromString<AirportGeoJsonFeatureCollection>(countryGeoJsonText)
            Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Parsed ${countryCollection.features.size} features from country GeoJSON")

            // Process airport features to map values and remove unwanted properties
            val processedFeatures = processAirportFeatures(countryCollection.features)
            val processedCollection = countryCollection.copy(features = processedFeatures)

            val success: Boolean = if (isFirstCountry) {
                // First country: write complete FeatureCollection with processed features
                Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Writing complete FeatureCollection for first country")
                val processedJson = compactJson.encodeToString(processedCollection)
                fileManager.writeText(mainFilePath, processedJson)
            } else {
                // Subsequent countries: merge with existing FeatureCollection
                Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Merging features with existing airports collection")
                val existingContent = fileManager.readText(mainFilePath) ?: ""
                if (existingContent.isNotEmpty()) {
                    try {
                        // Parse existing FeatureCollection
                        val existingCollection = compactJson.decodeFromString<AirportGeoJsonFeatureCollection>(existingContent)
                        // Merge features
                        val mergedFeatures = existingCollection.features + processedCollection.features
                        val mergedCollection = AirportGeoJsonFeatureCollection(features = mergedFeatures)
                        val mergedJson = compactJson.encodeToString(mergedCollection)
                        fileManager.writeText(mainFilePath, mergedJson)
                        Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Merged ${processedCollection.features.size} features (total: ${mergedFeatures.size})")
                        true // Success
                    } catch (e: Exception) {
                        Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Failed to merge airport FeatureCollections: ${e.message}", e)
                        return Result.failure(Exception("Failed to merge airport FeatureCollections: ${e.message}"))
                    }
                } else {
                    // Fallback: write as new collection
                    Logger.log("AIRPORTS_STORAGE", LogLevel.WARN, "Existing airports file was empty, writing as new collection")
                    val processedJson = compactJson.encodeToString(processedCollection)
                    fileManager.writeText(mainFilePath, processedJson)
                }
            }

            if (success) {
                // Apply disabled airports from persistent storage
                // ✅ Disabled airports restoration removed - now handled via reactive state

                Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Successfully merged airport data to main file")
                Result.success(Unit)
            } else {
                Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Failed to write merged airports data")
                Result.failure(Exception("Failed to write merged airports data"))
            }
        } catch (e: Exception) {
            Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Error merging country GeoJSON: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Read airports data as string for layer rendering
     */
    suspend fun readAirportsDataAsString(): String? {
        return try {
            // Check for the GeoJSON file that we save
            val dataDir = fileManager.getAppDataDirectory()
            val airportsDir = "$dataDir/${AirportsConstants.AIRPORTS_DIR}"
            val filePath = "$airportsDir/${AirportsConstants.AIRPORTS_GEOJSON_FILE}"

            if (fileManager.exists(filePath)) {
                fileManager.readText(filePath)
            } else {
                Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Airports file not found: $filePath")
                null
            }
        } catch (e: Exception) {
            Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Exception reading airports data as string: ${e.message}", e)
            null
        }
    }

    /**
     * Load airports metadata from file
     */
    suspend fun loadAirportsMetadata(): Result<AirportsMetadata> {
        return try {
            val dataDir = fileManager.getAppDataDirectory()
            val metadataPath = "$dataDir/${AirportsConstants.AIRPORTS_DIR}/${AirportsConstants.AIRPORTS_METADATA_FILE}"

            if (!fileManager.exists(metadataPath)) {
                Logger.log("AIRPORTS_STORAGE", LogLevel.INFO, "Airports metadata file not found: $metadataPath")
                return Result.failure(Exception("Metadata file not found"))
            }

            val jsonString = fileManager.readText(metadataPath)
            if (jsonString.isNullOrBlank()) {
                Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Airports metadata file is empty or unreadable: $metadataPath")
                return Result.failure(Exception("Metadata file empty or unreadable"))
            }

            val metadata = Json.decodeFromString<AirportsMetadata>(jsonString)
            Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Loaded airports metadata: $metadata")
            Result.success(metadata)
        } catch (e: Exception) {
            Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Error loading airports metadata: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Update airports metadata file
     */
    suspend fun updateAirportsMetadata(metadata: AirportsMetadata): Result<Unit> {
        return try {
            val dataDir = fileManager.getAppDataDirectory()
            val metadataPath = "$dataDir/${AirportsConstants.AIRPORTS_DIR}/${AirportsConstants.AIRPORTS_METADATA_FILE}"

            ensureAirportsDirectory().getOrThrow() // Ensure directory exists

            val jsonString = compactJson.encodeToString(metadata)
            val success = fileManager.writeText(metadataPath, jsonString)

            if (success) {
                Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Airports metadata updated successfully")
                Result.success(Unit)
            } else {
                Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Failed to write airports metadata to $metadataPath")
                Result.failure(Exception("Failed to write metadata"))
            }
        } catch (e: Exception) {
            Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Error updating airports metadata: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Check if the main airports GeoJSON file exists and is valid (basic check)
     */
    suspend fun hasAirportsData(): Boolean {
        val dataDir = fileManager.getAppDataDirectory()
        val airportsDir = "$dataDir/${AirportsConstants.AIRPORTS_DIR}"
        val mainGeoJsonPath = "$airportsDir/${AirportsConstants.AIRPORTS_GEOJSON_FILE}"

        val exists = fileManager.exists(mainGeoJsonPath)
        val content = fileManager.readText(mainGeoJsonPath)
        val length = content?.length ?: 0
        val hasData = exists && length > 10

        Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "hasAirportsData check: exists=$exists, length=$length, hasData=$hasData, path=$mainGeoJsonPath")

        return hasData
    }

    /**
     * Get list of CUP files in the airports directory
     */
    suspend fun getCupFilesList(): List<String> {
        return try {
            val dataDir = fileManager.getAppDataDirectory()
            val airportsDir = "$dataDir/${AirportsConstants.AIRPORTS_DIR}"

            if (!fileManager.exists(airportsDir)) {
                return emptyList()
            }

            val allFiles = fileManager.listFiles(airportsDir)
            allFiles.filter { it.lowercase().endsWith(".cup") }
        } catch (e: Exception) {
            Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Failed to list CUP files: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Delete a directory from the airports directory
     */
    suspend fun deleteDirectory(dirName: String): Boolean {
        return try {
            val dataDir = fileManager.getAppDataDirectory()
            val airportsDir = "$dataDir/${AirportsConstants.AIRPORTS_DIR}"
            val dirPath = "$airportsDir/$dirName"

            Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Deleting directory: $dirPath")
            val success = deleteDirectoryRecursively(dirPath)
            Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Directory deletion result: $success for $dirPath")
            success
        } catch (e: Exception) {
            Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Exception deleting directory $dirName: ${e.message}", e)
            false
        }
    }

    /**
     * Delete a CUP file from the airports directory
     */
    suspend fun deleteCupFile(fileName: String): Boolean {
        return try {
            val dataDir = fileManager.getAppDataDirectory()
            val airportsDir = "$dataDir/${AirportsConstants.AIRPORTS_DIR}"
            val filePath = "$airportsDir/$fileName"

            if (!fileManager.exists(filePath)) {
                Logger.log("AIRPORTS_STORAGE", LogLevel.WARN, "CUP file does not exist: $fileName")
                return false
            }

            val deleted = fileManager.delete(filePath)

            if (deleted) {
                Logger.log("AIRPORTS_STORAGE", LogLevel.INFO, "Deleted CUP file: $fileName")
            } else {
                Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Failed to delete CUP file: $fileName")
            }

            deleted
        } catch (e: Exception) {
            Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Exception while deleting CUP file $fileName: ${e.message}", e)
            false
        }
    }

    /**
     * Read a CUP file from the airports directory
     */
    suspend fun readCupFile(fileName: String): String? {
        return try {
            val dataDir = fileManager.getAppDataDirectory()
            val airportsDir = "$dataDir/${AirportsConstants.AIRPORTS_DIR}"
            val filePath = "$airportsDir/$fileName"

            if (!fileManager.exists(filePath)) {
                Logger.log("AIRPORTS_STORAGE", LogLevel.WARN, "CUP file does not exist: $fileName")
                return null
            }

            fileManager.readText(filePath)
        } catch (e: Exception) {
            Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Exception while reading CUP file $fileName: ${e.message}", e)
            null
        }
    }

    /**
     * Recursively delete a directory and all contents
     */
    private suspend fun deleteDirectoryRecursively(dirPath: String): Boolean {
        return try {
            if (!fileManager.exists(dirPath)) {
                Logger.log("AIRPORTS_STORAGE", LogLevel.DEBUG, "Directory does not exist: $dirPath")
                return true // Consider non-existent directory as successfully "deleted"
            }

            val fileNames = fileManager.listFiles(dirPath)
            var allDeleted = true

            for (fileName in fileNames) {
                val filePath = "$dirPath/$fileName"
                if (fileManager.exists(filePath)) {
                    // Try to delete as file first
                    val fileDeleted = fileManager.delete(filePath)
                    if (!fileDeleted) {
                        // If file deletion failed, try recursive directory deletion
                        val dirDeleted = deleteDirectoryRecursively(filePath)
                        if (!dirDeleted) {
                            Logger.log("AIRPORTS_STORAGE", LogLevel.WARN, "Failed to delete: $filePath")
                            allDeleted = false
                        }
                    }
                }
            }

            // Finally delete the directory itself
            val dirDeleted = fileManager.delete(dirPath)
            if (!dirDeleted) {
                allDeleted = false
                Logger.log("AIRPORTS_STORAGE", LogLevel.WARN, "Failed to delete directory: $dirPath")
            }

            allDeleted
        } catch (e: Exception) {
            Logger.log("AIRPORTS_STORAGE", LogLevel.ERROR, "Error deleting directory recursively: $dirPath", e)
            false
        }
    }
}

@Serializable
data class AirportsMetadata(
    val lastUpdated: Long,
    val totalFeatures: Int,
    val availableTypes: Set<String>,
    val processedCupFiles: Set<String> = emptySet(),
    val importedAt: Long? = null
)
