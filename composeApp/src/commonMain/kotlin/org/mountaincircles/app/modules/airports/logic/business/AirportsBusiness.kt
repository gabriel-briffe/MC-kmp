package org.mountaincircles.app.modules.airports.logic.business

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.modules.airports.logic.AirportsConstants
import org.mountaincircles.app.modules.airports.logic.data.AirportsProgress
import org.mountaincircles.app.modules.airports.import.logic.AirportsMetadata
import org.mountaincircles.app.modules.airports.import.logic.CountryGeoJsonData
import org.mountaincircles.app.network.ProgressData

// Airport type mappings (same as in AirportsStorage)
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

// Reverse mapping from text to numeric codes for filtering
private val reverseAirportTypeMap = airportTypeMap.entries.associate { (code, text) -> text to code.toString() }

/**
 * Airports Business Logic - core operations for airport data management
 * Cloned from AirspaceBusiness
 */

/**
 * Import airports for selected countries
 */
suspend fun importAirportsForSelectedCountries(module: AirportsModule): Result<Unit> {
    val selectedCountries = module.airportsState.value.selectedCountries
    return importAirportsForCountries(module, selectedCountries)
}

/**
 * Import airport data for specific countries
 */
suspend fun importAirportsForCountries(module: AirportsModule, countryCodes: List<String>): Result<Unit> {
    return try {
        Logger.log("AIRPORTS", LogLevel.INFO, "Starting airport import for countries: ${countryCodes.joinToString()}")

        // Clear all existing airport data and cache before downloading new data
        Logger.log("AIRPORTS", LogLevel.INFO, "Clearing all existing airport data and cache before new import")
        clearAirportsData(module).getOrThrow()

        // Track download progress
        val downloadedData = mutableListOf<CountryGeoJsonData>()
        var completedFiles = 0
        var currentCountryIndex = 0

        // Check CUP files to determine total progress steps
        val cupFiles = module.airportsBusinessService.getCupFilesList()
        val hasCupFiles = cupFiles.isNotEmpty()
        val hasCountries = countryCodes.isNotEmpty()
        val totalSteps = countryCodes.size + cupFiles.size + 1 // +1 for final "completed" step

        // Set initial progress and downloading state in single update
        val initialStatus = if (hasCountries) "Starting airports download..." else "Processing CUP files..."
        module.updateState { it.copy(
            isDownloading = true,
            hasError = false,
            errorMessage = null,
            currentProgress = AirportsProgress(
                current = 0,
                total = totalSteps,
                status = initialStatus,
                percent = 0
            )
        )}

        // Execute download only if countries are selected
        val downloadResult = if (hasCountries) {
            module.airportsDownloadManagerInstance.downloadAirportsForCountries(
                selectedCountryCodes = countryCodes
            ) { progress ->
                // Handle granular progress for each phase
                when {
                    progress.status.contains("Downloading") && progress.status.contains("...") -> {
                        // Extract country name from "Downloading France..."
                        val countryName = progress.status.substringAfter("Downloading ").substringBefore("...")
                        val currentFileNumber = completedFiles + 1
                        val status = "${currentFileNumber}/${countryCodes.size} downloading ${countryName}..."
                        module.updateState { it.copy(
                            currentProgress = AirportsProgress(
                                current = completedFiles,
                                total = totalSteps,
                                status = status,
                                percent = (completedFiles * 100) / totalSteps
                            )
                        )}
                    }
                    progress.status.contains("Parsing") && progress.status.contains("...") -> {
                        // Extract country name from "Parsing France..."
                        val countryName = progress.status.substringAfter("Parsing ").substringBefore("...")
                        val currentFileNumber = completedFiles + 1
                        val status = "${currentFileNumber}/${countryCodes.size} parsing ${countryName}..."
                        module.updateState { it.copy(
                            currentProgress = AirportsProgress(
                                current = completedFiles,
                                total = totalSteps,
                                status = status,
                                percent = (completedFiles * 100) / totalSteps
                            )
                        )}
                    }
                    progress.status.contains("Merging") && progress.status.contains("...") -> {
                        // Extract country name from "Merging France..."
                        val countryName = progress.status.substringAfter("Merging ").substringBefore("...")
                        val currentFileNumber = completedFiles + 1
                        val status = "${currentFileNumber}/${countryCodes.size} merging ${countryName}..."
                        module.updateState { it.copy(
                            currentProgress = AirportsProgress(
                                current = completedFiles,
                                total = totalSteps,
                                status = status,
                                percent = (completedFiles * 100) / totalSteps
                            )
                        )}
                    }
                    progress.status.contains("airport data downloaded and validated") -> {
                        completedFiles++
                        val percent = (completedFiles * 100) / totalSteps
                        val status = "${completedFiles}/${countryCodes.size} countries completed"
                        module.updateState { it.copy(
                            currentProgress = AirportsProgress(
                                current = completedFiles,
                                total = totalSteps,
                                status = status,
                                percent = percent
                            )
                        )}
                        Logger.log("AIRPORTS_DOWNLOAD", LogLevel.DEBUG, "File completed: ${progress.status} (${completedFiles}/${countryCodes.size})")
                    }
                }
            }
        } else {
            // No countries selected - skip download, continue with CUP processing
            Logger.log("AIRPORTS", LogLevel.INFO, "No countries selected, skipping download and proceeding with CUP file processing")
            Result.success(emptyList())
        }

        when {
            downloadResult.isSuccess -> {
                downloadedData.addAll(downloadResult.getOrThrow())
                Logger.log("AIRPORTS", LogLevel.INFO, "Successfully downloaded ${downloadedData.size} countries")

                // Save each country's GeoJSON data to individual files
                for (countryData in downloadedData) {
                    val fileName = "${countryData.country.code}_airports.geojson"
                    val saveResult = module.airportsStorage.saveCountryGeoJson(countryData.country.code, countryData.geoJsonText)

                    if (saveResult.isSuccess) {
                        Logger.log("AIRPORTS", LogLevel.DEBUG, "Saved ${countryData.country.name} airport data to $fileName")
                    } else {
                        val error = saveResult.exceptionOrNull() ?: Exception("Unknown save error")
                        Logger.log("AIRPORTS", LogLevel.ERROR, "Failed to save ${countryData.country.name} airport data: ${error.message}", error)
                        return Result.failure(error)
                    }
                }

                // Check for CUP files and process them if they exist
                val cupFiles = module.airportsBusinessService.getCupFilesList()
                val processedCupFiles = mutableSetOf<String>()

                if (cupFiles.isNotEmpty()) {
                    Logger.log("AIRPORTS", LogLevel.INFO, "Found ${cupFiles.size} CUP files, processing and merging...")

                    // Process and merge each CUP file with individual progress updates
                    for ((index, cupFileName) in cupFiles.withIndex()) {
                        // Update progress to show current CUP file being processed
                        module.updateState { it.copy(
                            currentProgress = AirportsProgress(
                                current = countryCodes.size + index + 1,
                                total = totalSteps,
                                status = "processing $cupFileName",
                                percent = (((countryCodes.size + index + 1).toFloat() / totalSteps) * 100).toInt()
                            )
                        )}

                        try {
                            val cupContent = module.airportsStorage.readCupFile(cupFileName)
                            if (cupContent != null) {
                                val merged = processCupFileAndMergeWithoutCache(cupContent, module, cupFileName)
                                if (merged) {
                                    Logger.log("AIRPORTS", LogLevel.INFO, "Successfully merged CUP file: $cupFileName")
                                    processedCupFiles.add(cupFileName)
                                } else {
                                    Logger.log("AIRPORTS", LogLevel.WARN, "Failed to merge CUP file: $cupFileName")
                                }
                            } else {
                                Logger.log("AIRPORTS", LogLevel.WARN, "Could not read CUP file: $cupFileName")
                            }
                        } catch (e: Exception) {
                            Logger.log("AIRPORTS", LogLevel.ERROR, "Exception processing CUP file $cupFileName: ${e.message}")
                        }
                    }
                }

                // Mark as having data and clear progress after delays
                // Check if we have data from either country downloads or CUP processing
                val hasDataFromCountries = downloadedData.isNotEmpty()
                val hasDataFromCup = module.airportsStorage.hasAirportsData()
                val hasData = hasDataFromCountries || hasDataFromCup
                Logger.log("AIRPORTS", LogLevel.INFO, "hasData calculation: countries=$hasDataFromCountries, cup=$hasDataFromCup, final=$hasData")
                val currentTime = System.currentTimeMillis()

                // Extract available types from FINAL merged/filtered GeoJSON file (now includes CUP data if present)
                val availableTypes = extractAvailableTypesFromMergedFile(module)
                Logger.log("AIRPORTS", LogLevel.INFO, "Extracted ${availableTypes.size} available airport types from final merged file: $availableTypes")

                // Clean up visible types - keep only types that exist in new import
                val currentState = module.airportsState.value
                val cleanedVisibleTypes = module.cleanupVisibleTypes(currentState.currentVisibleTypes, availableTypes)

                // ✅ AUTO-ENABLE ALL TYPES: If no types are visible after cleanup, enable all available types
                val finalVisibleTypes = if (cleanedVisibleTypes.isEmpty() && availableTypes.isNotEmpty()) {
                    Logger.log("AIRPORTS", LogLevel.INFO, "No airport types were visible after import - automatically enabling all ${availableTypes.size} available types")
                    availableTypes
                } else {
                    cleanedVisibleTypes
                }

                // Save metadata with available types from final merged file
                if (hasData) {
                    // Load existing metadata to preserve previously processed CUP files
                    val existingMetadata = module.airportsStorage.loadAirportsMetadata().getOrNull()
                    val updatedProcessedCupFiles = (existingMetadata?.processedCupFiles ?: emptySet()) + processedCupFiles
                    val metadata = AirportsMetadata(
                        lastUpdated = currentTime,
                        totalFeatures = 0, // Will be calculated during rescan
                        availableTypes = availableTypes,
                        processedCupFiles = updatedProcessedCupFiles,
                        importedAt = currentTime
                    )
                    val metadataResult = module.airportsStorage.updateAirportsMetadata(metadata)
                    if (metadataResult.isSuccess) {
                        Logger.log("AIRPORTS", LogLevel.DEBUG, "Airports metadata saved successfully")
                    } else {
                        Logger.log("AIRPORTS", LogLevel.WARN, "Failed to save airports metadata: ${metadataResult.exceptionOrNull()?.message}")
                    }
                }

                module.updateState { it.copy(
                    hasDataToRender = hasData,
                    availableTypes = if (hasData) availableTypes else emptySet(),
                    currentVisibleTypes = if (hasData) finalVisibleTypes else emptySet(), // Use final visible types (may include auto-enabled types)
                    importedAt = if (hasData) currentTime else null
                )}

                if (currentState.currentVisibleTypes.size != finalVisibleTypes.size || finalVisibleTypes != cleanedVisibleTypes) {
                    val removedCount = currentState.currentVisibleTypes.size - cleanedVisibleTypes.size
                    val autoEnabledCount = finalVisibleTypes.size - cleanedVisibleTypes.size
                    Logger.log("AIRPORTS", LogLevel.INFO, "Import visible types cleanup: ${currentState.currentVisibleTypes.size} → ${finalVisibleTypes.size} (removed $removedCount obsolete types${if (autoEnabledCount > 0) ", auto-enabled $autoEnabledCount types" else ""})")
                }

                // 🚀 WARM CACHE: Create filtered file and set cached URI immediately after import
                Logger.log("AIRPORTS", LogLevel.INFO, "Warming filtered cache after successful import")
                // ✅ MapLibre filtering: No cache warming needed - state changes trigger reactive updates

                // Show "completed" message
                module.updateState { it.copy(
                    currentProgress = AirportsProgress(
                        current = totalSteps,
                        total = totalSteps,
                        status = "completed",
                        percent = -1
                    )
                )}

                // Make airports visible after successful import (like airspace module)
                module.updateState { it.copy(airportsVisibility = true) }
                Logger.log("AIRPORTS", LogLevel.INFO, "Airports made visible after successful import")


                // Now clear progress
                module.updateState { it.copy(currentProgress = null) }

                Logger.log("AIRPORTS", LogLevel.INFO, "Airport import completed successfully for ${countryCodes.size} countries${if (hasCupFiles) " and ${cupFiles.size} CUP files" else ""}")

                Result.success(Unit)
            }
            else -> {
                val error = downloadResult.exceptionOrNull() ?: Exception("Unknown download error")
                Logger.log("AIRPORTS", LogLevel.ERROR, "Airport import failed: ${error.message}", error)

                // Update state with error and clear progress
                module.updateState { it.copy(
                    hasError = true,
                    errorMessage = error.message,
                    currentProgress = null // Clear progress on error
                )}
                Result.failure(error)
            }
        }
    } catch (e: Exception) {
        Logger.log("AIRPORTS", LogLevel.ERROR, "Airport import exception: ${e.message}", e)
        module.updateState { it.copy(
            hasError = true,
            errorMessage = e.message,
            currentProgress = null // Clear progress on exception
        )}
        Result.failure(e)
    } finally {
        // Clear downloading state and progress
        module.updateState { it.copy(
            isDownloading = false,
            currentProgress = null // Clear progress in finally
        )}
    }
}

/**
 * Clear all airport data
 */
suspend fun clearAirportsData(module: AirportsModule): Result<Unit> {
    return try {
        Logger.log("AIRPORTS", LogLevel.INFO, "Clearing all airport data")

        // Clear storage
        module.airportsStorage.clearAirportsData().getOrThrow()

        // ✅ CLEAR STALE URI: Clear filtered URI when clearing data
        // ✅ No filtered URI to reset - MapLibre handles filtering dynamically

        // Update state
        module.updateState { it.copy(
            hasDataToRender = false,
            importedAt = null,
            hasError = false,
            errorMessage = null,
            airportsVisibility = false  // ✅ Hide layer when data is cleared (like airspace)
        )}

        Logger.log("AIRPORTS", LogLevel.INFO, "Airport data cleared successfully")
        Result.success(Unit)
    } catch (e: Exception) {
        Logger.log("AIRPORTS", LogLevel.ERROR, "Failed to clear airport data: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Rescan existing airport data
 */
suspend fun rescanData(module: AirportsModule): Result<Unit> {
    return try {
        Logger.log("AIRPORTS", LogLevel.INFO, "Rescanning airport data")

        val hasData = module.airportsStorage.hasAirportsData()
        Logger.log("AIRPORTS", LogLevel.DEBUG, "Airports data exists: $hasData")

        if (hasData) {
            // Load metadata to get available types and imported date
            val metadataResult = module.airportsStorage.loadAirportsMetadata()
            val (availableTypes, importedAt) = if (metadataResult.isSuccess) {
                val metadata = metadataResult.getOrThrow()
                Logger.log("AIRPORTS", LogLevel.INFO, "Loaded ${metadata.availableTypes.size} available airport types from metadata")
                Pair(metadata.availableTypes, metadata.importedAt)
            } else {
                Logger.log("AIRPORTS", LogLevel.WARN, "Failed to load airports metadata: ${metadataResult.exceptionOrNull()?.message}")
                Pair(emptySet<String>(), null)
            }

            // Clean up visible types for rescan - keep only types that exist
            val currentState = module.airportsState.value
            val cleanedVisibleTypes = module.cleanupVisibleTypes(currentState.currentVisibleTypes, availableTypes)

            // ✅ AUTO-ENABLE ALL TYPES: If no types are visible after cleanup, enable all available types
            val finalVisibleTypes = if (cleanedVisibleTypes.isEmpty() && availableTypes.isNotEmpty()) {
                Logger.log("AIRPORTS", LogLevel.INFO, "No airport types were visible after rescan - automatically enabling all ${availableTypes.size} available types")
                availableTypes
            } else {
                cleanedVisibleTypes
            }

            // Update state with available data, final visible types, and imported date
            module.updateState { it.copy(
                hasDataToRender = true,
                availableTypes = availableTypes,
                currentVisibleTypes = finalVisibleTypes, // Use final visible types (may include auto-enabled types)
                importedAt = importedAt
            )}

            if (currentState.currentVisibleTypes.size != finalVisibleTypes.size || finalVisibleTypes != cleanedVisibleTypes) {
                val removedCount = currentState.currentVisibleTypes.size - cleanedVisibleTypes.size
                val autoEnabledCount = finalVisibleTypes.size - cleanedVisibleTypes.size
                Logger.log("AIRPORTS", LogLevel.INFO, "Rescan visible types cleanup: ${currentState.currentVisibleTypes.size} → ${finalVisibleTypes.size} (removed $removedCount obsolete types${if (autoEnabledCount > 0) ", auto-enabled $autoEnabledCount types" else ""})")
            }
        } else {
            // Clear available types if no data
            module.updateState { it.copy(
                hasDataToRender = false,
                availableTypes = emptySet()
            )}
        }

        Logger.log("AIRPORTS", LogLevel.DEBUG, "Airport data rescan completed")
        Result.success(Unit)
    } catch (e: Exception) {
        Logger.log("AIRPORTS", LogLevel.ERROR, "Failed to rescan airport data: ${e.message}", e)
        Result.failure(e)
    }
}

/**
 * Extract available airport types from the final merged and filtered airports.geojson file
 * This ensures only types that actually exist in the final data are included
 */
internal suspend fun extractAvailableTypesFromMergedFile(module: AirportsModule): Set<String> {
    return try {
        // Read the final merged airports.geojson file
        val mergedData = module.airportsStorage.readAirportsDataAsString()
        if (mergedData.isNullOrBlank()) {
            Logger.log("AIRPORTS", LogLevel.WARN, "No merged airports data found for type extraction")
            return emptySet()
        }

        val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(mergedData)
        val features = jsonElement.jsonObject["features"]?.jsonArray

        if (features == null) {
            Logger.log("AIRPORTS", LogLevel.WARN, "No features found in merged airports data")
            return emptySet()
        }

        val availableTypes = mutableSetOf<String>()

        // Extract text type descriptions from the final filtered data
        for (feature in features) {
            val featureObj = feature.jsonObject
            val properties = featureObj["properties"]?.jsonObject
            val airportType = properties?.get("type")?.jsonPrimitive?.content
            if (airportType is String) {
                availableTypes.add(airportType)
            }
        }

        // Always include "disabled" type for filtering disabled airports
        availableTypes.add("disabled")

        // Sort alphabetically for consistent display order
        val sortedTypes = availableTypes.sorted().toSet()

        Logger.log("AIRPORTS", LogLevel.INFO, "Extracted ${sortedTypes.size} airport types from final merged file (including forced 'disabled'): $sortedTypes")
        sortedTypes
    } catch (e: Exception) {
        Logger.log("AIRPORTS", LogLevel.ERROR, "Failed to extract types from merged file: ${e.message}", e)
        emptySet()
    }
}

/**
 * Parse CUP coordinate format (DDMM.mmmN/E or DDDMM.mmmN/E) to decimal degrees
 * CUP format: Latitudes always DDMM.mmm, Longitudes DDDMM.mmm when < 100°
 */
fun parseCupCoordinate(coordStr: String): Double? {
    try {
        if (coordStr.length < 4) return null

        val direction = coordStr.last()
        val coordinatePart = coordStr.dropLast(1)

        // CUP coordinate format: DDMM.mmm or DDDMM.mmm
        // Latitudes (N/S): always DDMM.mmm (4 digits before decimal)
        // Longitudes (E/W): DDDMM.mmm (5 digits before decimal) when degrees < 100

        val degrees: Int
        val minutes: Double

        if (direction == 'N' || direction == 'S') {
            // Latitude: always 4 digits (DDMM)
            degrees = coordinatePart.substring(0, 2).toInt()
            minutes = coordinatePart.substring(2).toDouble()
        } else {
            // Longitude: can be 4 or 5 digits (DDMM or DDDMM)
            // If coordinatePart has 6+ chars, it's DDDMM format
            if (coordinatePart.length >= 6) {
                degrees = coordinatePart.substring(0, 3).toInt()
                minutes = coordinatePart.substring(3).toDouble()
            } else {
                // Fallback to DDMM format for longitudes >= 100°
                degrees = coordinatePart.substring(0, 2).toInt()
                minutes = coordinatePart.substring(2).toDouble()
            }
        }

        var decimal = degrees + (minutes / 60.0)

        // Apply direction
        if (direction == 'S' || direction == 'W') {
            decimal = -decimal
        }

        return decimal

    } catch (e: Exception) {
        Logger.log("AIRPORTS", LogLevel.WARN, "Failed to parse CUP coordinate: $coordStr, error: ${e.message}")
        return null
    }
}

/**
 * Parse CUP elevation string (e.g., "460m") into structured format
 */
fun parseCupElevation(elevStr: String): kotlinx.serialization.json.JsonObject? {
    try {
        if (elevStr.isBlank()) return null

        // Remove 'm' suffix and parse as integer
        val valueStr = elevStr.removeSuffix("m").trim()
        val value = valueStr.toIntOrNull() ?: return null

        return buildJsonObject {
            put("value", JsonPrimitive(value))
            put("unit", JsonPrimitive(0))  // 0 = meters
            put("referenceDatum", JsonPrimitive(1))  // 1 = MSL
        }
    } catch (e: Exception) {
        Logger.log("AIRPORTS", LogLevel.WARN, "Failed to parse elevation: $elevStr")
        return null
    }
}

/**
 * Parse CUP runway data into structured format
 */
fun parseCupRunways(rwdir: Int?, rwlen: String?, rwwidth: String?): kotlinx.serialization.json.JsonArray? {
    try {
        if (rwdir == null || rwlen.isNullOrBlank() || rwwidth.isNullOrBlank()) return null

        // Parse length (e.g., "300m" -> 300)
        val lengthValue = rwlen.removeSuffix("m").trim().toIntOrNull() ?: return null

        // Parse width (e.g., "23m" -> 23)
        val widthValue = rwwidth.removeSuffix("m").trim().toIntOrNull() ?: return null

        // Convert direction (e.g., 110 -> "11", 60 -> "06" for designator)
        val designator = String.format("%02d", rwdir / 10)

        val runway = buildJsonObject {
            put("designator", JsonPrimitive(designator))
            putJsonObject("dimension") {
                putJsonObject("length") {
                    put("value", JsonPrimitive(lengthValue))
                    put("unit", JsonPrimitive(0))  // 0 = meters
                }
                putJsonObject("width") {
                    put("value", JsonPrimitive(widthValue))
                    put("unit", JsonPrimitive(0))  // 0 = meters
                }
            }
        }

        return buildJsonArray {
            add(runway)
        }
    } catch (e: Exception) {
        Logger.log("AIRPORTS", LogLevel.WARN, "Failed to parse runways: dir=$rwdir, len=$rwlen, width=$rwwidth")
        return null
    }
}

/**
 * Parse CSV line with proper quote handling (CUP format)
 */
fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var current = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < line.length) {
        val char = line[i]
        when {
            char == '"' && !inQuotes -> {
                inQuotes = true
            }
            char == '"' && inQuotes -> {
                // Check if next char is also a quote (escaped)
                if (i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++ // Skip next quote
                } else {
                    inQuotes = false
                }
            }
            char == ',' && !inQuotes -> {
                result.add(current.toString())
                current = StringBuilder()
            }
            else -> {
                current.append(char)
            }
        }
        i++
    }

    // Add the last field
    result.add(current.toString())

    return result
}

/**
 * Convert CUP waypoint columns to GeoJSON feature (common implementation)
 */
fun convertCupWaypointToGeoJsonFeatureCommon(
    columns: List<String>,
    fileName: String
): kotlinx.serialization.json.JsonObject? {
    try {
        if (columns.size < 12) return null

        val name = columns[0].removeSurrounding("\"")
        val code = columns[1].removeSurrounding("\"")
        val country = columns[2]
        val latStr = columns[3]
        val lonStr = columns[4]
        val elevStr = columns[5]
        val style = columns[6].toIntOrNull() ?: 3
        val rwdir = columns[7].toIntOrNull()
        val rwlen = columns[8]
        val rwwidth = columns[9]
        val freq = columns[10].takeIf { it.isNotBlank() }
        val desc = columns.getOrNull(11)?.takeIf { it.isNotBlank() }
        val pics = columns.getOrNull(13)?.takeIf { it.isNotBlank() }

        // Convert coordinates from DDMM.mmmN/E format to decimal degrees
        val lat = parseCupCoordinate(latStr) ?: return null
        val lon = parseCupCoordinate(lonStr) ?: return null

        // Map style to type
        val type = when (style) {
            2 -> "outlanding grass runway"
            3 -> "outlanding field"
            4 -> "outlanding Glider site"
            5 -> "outlanding paved runway"
            else -> "outlanding"
        }

        // Parse elevation (e.g., "460m" -> {value: 460, unit: 0, referenceDatum: 1})
        val elevation = parseCupElevation(elevStr)

        // Parse runways from rwdir, rwlen, rwwidth
        val runways = parseCupRunways(rwdir, rwlen, rwwidth)

        return buildJsonObject {
            put("type", JsonPrimitive("Feature"))
            putJsonObject("geometry") {
                put("type", JsonPrimitive("Point"))
                putJsonArray("coordinates") {
                    add(JsonPrimitive(lon))
                    add(JsonPrimitive(lat))
                }
            }
            putJsonObject("properties") {
                put("_id", JsonPrimitive(code)) // Use CUP code as unique identifier
                put("name", JsonPrimitive(name))
                put("type", JsonPrimitive(type))
                if (elevation != null) {
                    put("elevation", elevation)
                }
                if (runways != null) {
                    put("runways", runways)
                }
                if (freq != null && freq.isNotBlank()) {
                    put("frequency", JsonPrimitive(freq))
                }
                if (desc != null) {
                    put("description", JsonPrimitive(desc))
                }

                // Parse and add pics property if available
                if (pics != null && pics.isNotBlank()) {
                    val picFiles = pics.split(";").map { it.trim() }.filter { it.isNotBlank() }
                    if (picFiles.isNotEmpty()) {
                        // Resolve pic paths to the extracted pics directory
                        // fileName should be like "westalpen_en.cup", so baseName is "westalpen_en"
                        val baseName = fileName.substringBeforeLast(".")
                        val picsDir = "${baseName}_pics"

                        putJsonArray("pics") {
                            picFiles.forEach { picFile ->
                                // Create full path relative to airports directory
                                // CUPX pics are stored in Pics/ subdirectory within the extracted zip
                                add(JsonPrimitive("$picsDir/Pics/$picFile"))
                            }
                        }
                    }
                }

                put("source", JsonPrimitive(fileName))
            }
        }

    } catch (e: Exception) {
        Logger.log("AIRPORTS", LogLevel.WARN, "Failed to convert CUP waypoint: ${e.message}")
        return null
    }
}

// ✅ Disabled airports restoration removed - now handled via reactive state

/**
 * Process CUP file content and merge waypoints into airports.geojson (without cache clearing or progress updates)
 * Used during normal import process to avoid duplicate operations
 */
fun processCupFileAndMergeWithoutCache(cupContent: String, module: AirportsModule, fileName: String): Boolean {
    try {
        val fileManager = getGlobalFileManager()
        val airportsDir = "${fileManager.getAppDataDirectory()}/${AirportsConstants.AIRPORTS_DIR}"
        val geoJsonPath = "$airportsDir/${AirportsConstants.AIRPORTS_GEOJSON_FILE}"

        // Ensure airports directory exists
        fileManager.createDirectory(airportsDir)

        // Read existing airports.geojson or create empty collection
        val existingJson = fileManager.readText(geoJsonPath) ?: """{"type":"FeatureCollection","features":[]}"""

        val existingCollection = Json.parseToJsonElement(existingJson) as JsonObject
        val existingFeatures = (existingCollection["features"] as JsonArray).toMutableList()

        Logger.log("CUP_PROCESSING", LogLevel.INFO, "Processing CUP file $fileName with ${cupContent.lines().size} lines")

        // Parse CUP CSV lines (skip header)
        val cupLines = cupContent.lines().drop(1).filter { it.isNotBlank() }

        var processedCount = 0
        cupLines.forEach { line ->
            try {
                val columns = parseCsvLine(line)
                if (columns.size >= 12) {
                    val feature = convertCupWaypointToGeoJsonFeatureCommon(columns, fileName)
                    if (feature != null) {
                        existingFeatures.add(feature)
                        processedCount++
                    }
                }
            } catch (e: Exception) {
                Logger.log("CUP_PROCESSING", LogLevel.WARN, "Failed to process CUP line: $line, error: ${e.message}")
            }
        }

        // Create updated collection
        val updatedCollection = buildJsonObject {
            put("type", JsonPrimitive("FeatureCollection"))
            putJsonArray("features") {
                existingFeatures.forEach { add(it) }
            }
        }

        // Write back to file
        val mergedJson = Json.encodeToString(updatedCollection)
        fileManager.writeText(geoJsonPath, mergedJson)

        Logger.log("CUP_PROCESSING", LogLevel.INFO, "Successfully processed $processedCount CUP waypoints from $fileName and merged into airports.geojson")
        return true
    } catch (e: Exception) {
        Logger.log("CUP_PROCESSING", LogLevel.ERROR, "Exception processing CUP file $fileName: ${e.message}", e)
        return false
    }
}

/**
 * Process CUP file content and merge waypoints into airports.geojson
 * This is platform-independent logic that can be called after reading the file content
 */

/**
 * Complete CUP import post-processing after waypoints have been merged into airports.geojson
 * This performs the same steps as regular airport import: extract types, update state, save metadata
 */
suspend fun completeCupImportPostProcessing(module: AirportsModule, processedCupFiles: Set<String> = emptySet()) {
    try {
        Logger.log("AIRPORTS", LogLevel.INFO, "Starting CUP import post-processing")

        // STEP 1: Extract available types from merged file (like normal import)
        val availableTypes = extractAvailableTypesFromMergedFile(module)
        Logger.log("AIRPORTS", LogLevel.INFO, "Extracted ${availableTypes.size} available airport types from CUP-merged file: $availableTypes")

        // STEP 2: Update module state (like normal import)
        val currentTime = System.currentTimeMillis()
        val currentState = module.airportsState.value
        val cleanedVisibleTypes = module.cleanupVisibleTypes(currentState.currentVisibleTypes, availableTypes)

        // ✅ AUTO-ENABLE ALL TYPES: If no types are visible after cleanup, enable all available types
        val finalVisibleTypes = if (cleanedVisibleTypes.isEmpty() && availableTypes.isNotEmpty()) {
            Logger.log("AIRPORTS", LogLevel.INFO, "No airport types were visible after CUP import - automatically enabling all ${availableTypes.size} available types")
            availableTypes
        } else {
            cleanedVisibleTypes
        }

        module.updateState { it.copy(
            hasDataToRender = true,
            availableTypes = availableTypes,
            currentVisibleTypes = finalVisibleTypes, // Use final visible types (may include auto-enabled types)
            importedAt = currentTime
        )}

        // STEP 3: Save metadata with available types (like normal import)
        // Load existing metadata to preserve previously processed CUP files
        val existingMetadata = module.airportsStorage.loadAirportsMetadata().getOrNull()
        val updatedProcessedCupFiles = (existingMetadata?.processedCupFiles ?: emptySet()) + processedCupFiles
        val metadata = org.mountaincircles.app.modules.airports.import.logic.AirportsMetadata(
            lastUpdated = currentTime,
            totalFeatures = 0, // Will be calculated during rescan
            availableTypes = availableTypes,
            processedCupFiles = updatedProcessedCupFiles,
            importedAt = currentTime
        )
        val metadataResult = module.airportsStorage.updateAirportsMetadata(metadata)
        if (metadataResult.isSuccess) {
            Logger.log("AIRPORTS", LogLevel.DEBUG, "CUP airports metadata saved successfully")
        } else {
            Logger.log("AIRPORTS", LogLevel.WARN, "Failed to save CUP airports metadata: ${metadataResult.exceptionOrNull()?.message}")
        }

        if (currentState.currentVisibleTypes.size != cleanedVisibleTypes.size) {
            Logger.log("AIRPORTS", LogLevel.INFO, "CUP import visible types cleanup: ${currentState.currentVisibleTypes.size} → ${cleanedVisibleTypes.size} (removed ${currentState.currentVisibleTypes.size - cleanedVisibleTypes.size} obsolete types)")
        }

        Logger.log("AIRPORTS", LogLevel.INFO, "CUP import post-processing completed successfully")

        // Final progress update: Complete
        module.updateState { it.copy(
            currentProgress = AirportsProgress(5, 5, "CUP import complete", 100),
            isDownloading = false
        )}

        // Delay 1 seconds before clearing progress to show completion
        delay(1000)

        // Clear progress after completion
        module.updateState { it.copy(currentProgress = null) }

    } catch (e: Exception) {
        Logger.log("AIRPORTS", LogLevel.ERROR, "Failed to complete CUP import post-processing: ${e.message}", e)
        module.updateState { it.copy(
            isDownloading = false,
            currentProgress = null,
            hasError = true,
            errorMessage = "Post-processing failed: ${e.message}"
        )}
    }
}

/**
 * Extract available airport types from downloaded country data (deprecated - use extractAvailableTypesFromMergedFile instead)
 * Returns sorted text descriptions (not numeric codes)
 */
private fun extractAvailableTypes(countryDataList: List<org.mountaincircles.app.modules.airports.import.logic.CountryGeoJsonData>): Set<String> {
    val availableTypeCodes = mutableSetOf<String>()

    // Extract numeric type codes
    for (countryData in countryDataList) {
        try {
            val jsonElement = kotlinx.serialization.json.Json.parseToJsonElement(countryData.geoJsonText)
            val features = jsonElement.jsonObject["features"]?.jsonArray

            if (features != null) {
                for (feature in features) {
                    val featureObj = feature.jsonObject
                    val properties = featureObj["properties"]?.jsonObject
                    val airportType = properties?.get("type")?.jsonPrimitive?.content
                    if (airportType is String) {
                        availableTypeCodes.add(airportType)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.log("AIRPORTS", LogLevel.WARN, "Failed to extract types from ${countryData.country.name} data: ${e.message}")
        }
    }

    // Sort numerically and map to text descriptions
    val sortedTextTypes = availableTypeCodes
        .mapNotNull { code -> code.toIntOrNull() } // Convert to int, skip invalid
        .sorted() // Sort numerically
        .mapNotNull { code -> airportTypeMap[code] } // Map to text, skip unknown
        .toSet()

    Logger.log("AIRPORTS", LogLevel.INFO, "Extracted and sorted ${sortedTextTypes.size} airport types from ${countryDataList.size} countries: $sortedTextTypes")
    return sortedTextTypes
}

// ✅ Cache functions removed - MapLibre handles filtering dynamically

// ✅ GeoJSON modification removed - disabled state now stored in reactive state

// CUPX File Handling Functions

/**
 * Check if a byte array represents a CUPX file
 * CUPX files start with "CUPX" header (256 bytes total header)
 */
fun isCupxFile(fileContent: ByteArray): Boolean {
    return fileContent.size >= 4 && fileContent.sliceArray(0..3).contentEquals("CUPX".encodeToByteArray())
}

/**
 * Find all ZIP signature positions in a byte array
 * ZIP files start with PK\x03\x04 signature
 */
fun findZipSignatures(data: ByteArray, startOffset: Int = 0): List<Int> {
    val zipSignature = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // "PK\x03\x04"
    val positions = mutableListOf<Int>()
    var searchStart = startOffset

    while (true) {
        val pos = data.indexOfByteArray(zipSignature, searchStart)
        if (pos == -1) break
        positions.add(pos)
        searchStart = pos + 1
    }
    return positions
}

/**
 * Find the index of a byte array within another byte array
 * Returns -1 if not found
 */
private fun ByteArray.indexOfByteArray(pattern: ByteArray, startIndex: Int = 0): Int {
    if (pattern.isEmpty()) return 0
    if (startIndex < 0) return -1
    if (startIndex + pattern.size > this.size) return -1

    for (i in startIndex..(this.size - pattern.size)) {
        var found = true
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) {
                found = false
                break
            }
        }
        if (found) return i
    }
    return -1
}

/**
 * Split a CUPX file into its component ZIP files
 * CUPX format: "CUPX" header (256 bytes) + pics.zip + points.zip concatenated
 * Returns Pair(picsData, pointsData) or null if splitting fails
 */
fun splitCupxFile(fileContent: ByteArray): Pair<ByteArray, ByteArray>? {
    try {
        if (!isCupxFile(fileContent)) {
            Logger.log("CUPX_SPLIT", LogLevel.ERROR, "Not a CUPX file - missing CUPX header")
            return null
        }

        val zipStart = 256 // Skip 256-byte CUPX header
        val zipPositions = findZipSignatures(fileContent, zipStart)

        if (zipPositions.size < 2) {
            Logger.log("CUPX_SPLIT", LogLevel.ERROR, "Need at least 2 ZIP signatures to split CUPX file, found ${zipPositions.size}")
            return null
        }

        // The last ZIP signature marks the start of points.zip
        // All ZIP signatures before that belong to files within pics.zip and points.zip
        val splitPos = zipPositions.last()
        val picsData = fileContent.sliceArray(zipPositions[0] until splitPos)
        val pointsData = fileContent.sliceArray(splitPos until fileContent.size)

        Logger.log("CUPX_SPLIT", LogLevel.INFO, "Successfully split CUPX file: pics.zip (${picsData.size} bytes), points.zip (${pointsData.size} bytes)")
        return Pair(picsData, pointsData)

    } catch (e: Exception) {
        Logger.log("CUPX_SPLIT", LogLevel.ERROR, "Error splitting CUPX file: ${e.message}", e)
        return null
    }
}


