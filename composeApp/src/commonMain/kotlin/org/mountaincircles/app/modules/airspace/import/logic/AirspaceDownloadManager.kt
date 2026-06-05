package org.mountaincircles.app.modules.airspace.import.logic

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceData
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceFeature
import org.mountaincircles.app.modules.airspace.import.logic.OpenAirParser
import org.mountaincircles.app.modules.airspace.import.logic.AirspaceStorage
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceSources
import org.mountaincircles.app.modules.airspace.logic.AirspaceConstants
import org.mountaincircles.app.network.DownloadManager
import org.mountaincircles.app.network.ProgressData
import org.mountaincircles.app.utils.currentTimeMillis

/**
 * Download manager for French airspace data
 * Now uses unified DownloadManager for HTTP operations
 */
class AirspaceDownloadManager(
    private val downloadManager: DownloadManager,
    private val parser: OpenAirParser,
    private val storage: AirspaceStorage
) {

    companion object {
        // Note: AIRSPACE_FILE_NAME constant moved to AirspaceStorage for single source of truth
    }



    /**
     * Download and process airspace data for selected countries
     */
    suspend fun downloadAirspaceForCountries(
        selectedCountryCodes: List<String>,
        onProgress: (ProgressData) -> Unit
    ): Result<Unit> {
        return try {
            Logger.log("AIRSPACE_IMPORT", LogLevel.INFO, "Starting airspace download for ${selectedCountryCodes.size} countries: ${selectedCountryCodes.joinToString()}")

            // Get selected countries
            val selectedCountries = AirspaceSources.countries.filter { it.code in selectedCountryCodes }
            if (selectedCountries.isEmpty()) {
                Logger.log("AIRSPACE_IMPORT", LogLevel.ERROR, "No valid countries selected")
                return Result.failure(Exception("No valid countries selected"))
            }

            // 🚀 INCREMENTAL APPROACH: No memory accumulation
            var processedCountries = 0
            var totalFeatures = 0
            val allAvailableTypes = mutableSetOf<String>()

            // 🎯 GLOBAL AI COUNTER: Maintain unique AI values across all countries
            var globalAiCounter = 0

            // 🚀 INITIALIZE: Ensure airspace directory exists
            storage.ensureAirspaceDirectory().getOrThrow()

            // Process each selected country
            for (country in selectedCountries) {
                processedCountries++

                Logger.log("AIRSPACE_IMPORT", LogLevel.INFO, "Downloading ${country.name} airspace data...")

                // Send downloading progress at the start (no byte-level updates)
                onProgress(ProgressData(
                    downloaded = 0,
                    total = 1,
                    status = "Downloading ${country.name}...",
                    percentage = 0.0f
                ))

                // Download OpenAir text for this country (no progress callbacks)
                val openAirText = downloadOpenAirText(country.url, onProgress = null)

                Logger.log("AIRSPACE_IMPORT", LogLevel.DEBUG, "${country.name} download completed, received ${openAirText.length} bytes")

                // Send parsing progress at the start
                onProgress(ProgressData(
                    downloaded = 0,
                    total = 1,
                    status = "Parsing ${country.name}...",
                    percentage = 0.0f
                ))

                // Parse the data for this country (no progress callbacks during parsing)
                val parseResult = parser.parseFrenchAirspace(openAirText) { _ ->
                    // Ignore progress updates during parsing
                }

                when {
                    parseResult.isSuccess -> {
                        val airspaceData = parseResult.getOrThrow()

                        // 🎯 GLOBAL AI ASSIGNMENT: Assign globally unique AI values instead of parser's sequential IDs
                        val featuresWithGlobalAI = airspaceData.features.map { feature ->
                            feature.copy(ai = globalAiCounter++.toString())
                        }

                        // Send merging progress at the start
                        onProgress(ProgressData(
                            downloaded = 0,
                            total = 1,
                            status = "Merging ${country.name}...",
                            percentage = 0.0f
                        ))

                        // 🚀 MERGE: Merge features to GeoJSON file
                        val isFirstCountry = processedCountries == 1
                        storage.mergeCountryFeaturesToGeoJson(
                            AirspaceConstants.AIRSPACE_GEOJSON_FILE,
                            featuresWithGlobalAI,
                            isFirstCountry
                        ).getOrThrow()

                        // Send completion progress after merge completes
                        onProgress(ProgressData.complete(1, "${country.name} airspace data processed"))

                        // 🚀 INCREMENTAL METADATA: Update running totals
                        allAvailableTypes.addAll(featuresWithGlobalAI.map { it.type })
                        totalFeatures += featuresWithGlobalAI.size

                        // 🚀 INCREMENTAL METADATA: Update metadata file with current progress
                        storage.updateIncrementalMetadata(
                            AirspaceConstants.AIRSPACE_GEOJSON_FILE,
                            allAvailableTypes,
                            totalFeatures,
                            currentTimeMillis()
                        ).getOrThrow()

                        Logger.log("AIRSPACE_IMPORT", LogLevel.INFO, "${country.name} parsing completed: ${airspaceData.features.size} features appended incrementally (AI range: ${featuresWithGlobalAI.firstOrNull()?.ai} to ${featuresWithGlobalAI.lastOrNull()?.ai})")
                    }
                    else -> {
                        Logger.log("AIRSPACE_IMPORT", LogLevel.ERROR, "${country.name} parsing failed: ${parseResult.exceptionOrNull()?.message}")
                        return Result.failure(parseResult.exceptionOrNull() ?: Exception("${country.name} parsing failed"))
                    }
                }
            }

            Logger.log("AIRSPACE_IMPORT", LogLevel.INFO, "All countries processed. Total features: $totalFeatures")

            // Log global AI range for debugging (approximate based on counter)
            Logger.log("AIRSPACE_IMPORT", LogLevel.INFO, "Global AI range: 0 to ${globalAiCounter - 1} ($totalFeatures features with AI values)")

            Logger.log("AIRSPACE_IMPORT", LogLevel.INFO, "Combined available airspace types: $allAvailableTypes")

            // 🚀 FINALIZE: Complete the incremental GeoJSON file
            onProgress(ProgressData(
                downloaded = selectedCountries.size.toLong(),  // All files processed
                total = selectedCountries.size.toLong(),       // All files processed
                status = "Finalizing airspace data...",
                percentage = 95f
            ))
            Logger.log("AIRSPACE_IMPORT", LogLevel.DEBUG, "Validating GeoJSON file")

            // Validate the final JSON structure
            storage.validateGeoJsonFile(AirspaceConstants.AIRSPACE_GEOJSON_FILE).getOrThrow()

            // Final metadata update
            storage.updateIncrementalMetadata(
                AirspaceConstants.AIRSPACE_GEOJSON_FILE,
                allAvailableTypes,
                totalFeatures,
                currentTimeMillis()
            ).getOrThrow()

            Logger.log("AIRSPACE_IMPORT", LogLevel.INFO, "Incremental airspace data finalized successfully in ${AirspaceConstants.AIRSPACE_GEOJSON_FILE}")

            onProgress(ProgressData.complete(selectedCountries.size.toLong(), "Airspace import completed successfully"))
            Logger.log("AIRSPACE", LogLevel.INFO, "Airspace import completed successfully for ${selectedCountryCodes.size} countries")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.log("AIRSPACE_IMPORT", LogLevel.ERROR, "Download process failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Download OpenAir text from a specific URL
     */
    private suspend fun downloadOpenAirText(url: String, onProgress: ((ProgressData) -> Unit)?): String {
        val result = downloadManager.downloadText(url, onProgress = onProgress ?: {})

        if (result.isSuccess) {
            return result.getOrThrow()
        } else {
            throw result.exceptionOrNull() ?: Exception("Text download failed")
        }
    }

    /**
     * Check if French airspace data is already available
     */
    suspend fun hasFrenchAirspaceData(): Boolean {
        return storage.hasAirspaceData()
    }

    /**
     * Clear existing airspace data
     */
    suspend fun clearAirspaceData(): Result<Unit> {
        Logger.log("AIRSPACE_IMPORT", LogLevel.INFO, "Clearing existing airspace data")
        return storage.clearAirspaceData()
    }
}
