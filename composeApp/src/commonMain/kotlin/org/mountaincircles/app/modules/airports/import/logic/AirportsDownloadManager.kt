package org.mountaincircles.app.modules.airports.import.logic

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airports.logic.data.AirportSources
import org.mountaincircles.app.modules.airports.logic.AirportsConstants
import org.mountaincircles.app.network.DownloadManager
import org.mountaincircles.app.network.ProgressData
import org.mountaincircles.app.utils.currentTimeMillis

/**
 * Download manager for airport GeoJSON data
 * Cloned from AirspaceDownloadManager but adapted for GeoJSON downloads
 * Only handles download phase - no parsing or merging
 */
class AirportsDownloadManager(
    private val downloadManager: DownloadManager,
    private val storage: AirportsStorage
) {

    /**
     * Download GeoJSON data for selected countries
     * Returns raw GeoJSON strings without parsing
     */
    suspend fun downloadAirportsForCountries(
        selectedCountryCodes: List<String>,
        onProgress: (ProgressData) -> Unit
    ): Result<List<CountryGeoJsonData>> {
        return try {
            Logger.log("AIRPORTS_IMPORT", LogLevel.INFO, "Starting airports download for ${selectedCountryCodes.size} countries: ${selectedCountryCodes.joinToString()}")

            // Get selected countries
            val selectedCountries = AirportSources.countries.filter { it.code in selectedCountryCodes }
            if (selectedCountries.isEmpty()) {
                Logger.log("AIRPORTS_IMPORT", LogLevel.ERROR, "No valid countries selected")
                return Result.failure(Exception("No valid countries selected"))
            }

            // Ensure airports directory exists
            storage.ensureAirportsDirectory().getOrThrow()

            // Track results
            val downloadedData = mutableListOf<CountryGeoJsonData>()

            // Process each selected country
            for (country in selectedCountries) {
                Logger.log("AIRPORTS_IMPORT", LogLevel.INFO, "Downloading ${country.name} airport data...")

                // Download GeoJSON text for this country with byte-level tracking
                var currentFileBytes = 0L
                var currentFileTotal = 0L

                val geoJsonText = downloadGeoJsonText(country.url) { progressData ->
                    // Store the actual byte progress for this file
                    currentFileBytes = progressData.downloaded
                    currentFileTotal = progressData.total

                    // Update progress with country-specific status
                    val fileProgress = progressData.copy(
                        status = "Downloading ${country.name}..."
                    )
                    onProgress(fileProgress)
                }

                Logger.log("AIRPORTS_IMPORT", LogLevel.DEBUG, "${country.name} download completed, received ${geoJsonText.length} bytes")

                // Report parsing phase progress
                onProgress(ProgressData(
                    downloaded = currentFileBytes,
                    total = currentFileTotal,
                    status = "Parsing ${country.name}...",
                    percentage = 100.0f
                ))

                // Basic JSON validation (ensure it's valid JSON)
                if (!isValidJson(geoJsonText)) {
                    Logger.log("AIRPORTS_IMPORT", LogLevel.ERROR, "${country.name} GeoJSON validation failed")
                    return Result.failure(Exception("${country.name} GeoJSON validation failed"))
                }

                // Report merging phase progress
                onProgress(ProgressData(
                    downloaded = currentFileBytes,
                    total = currentFileTotal,
                    status = "Merging ${country.name}...",
                    percentage = 100.0f
                ))

                // Store the downloaded data (pass through without parsing)
                downloadedData.add(CountryGeoJsonData(country, geoJsonText))

                // Merge country GeoJSON directly into main airports.geojson file
                val isFirstCountry = downloadedData.size == 1
                storage.mergeCountryGeoJsonTextToMainFile(geoJsonText, isFirstCountry).getOrThrow()

                // Report completion progress
                onProgress(ProgressData.complete(downloadedData.size.toLong(), "${country.name} airport data downloaded and validated"))

                Logger.log("AIRPORTS_IMPORT", LogLevel.INFO, "${country.name} airport data downloaded and validated (${geoJsonText.length} bytes)")
            }

            Logger.log("AIRPORTS_IMPORT", LogLevel.INFO, "All countries downloaded successfully: ${selectedCountryCodes.size} countries, ${downloadedData.sumOf { it.geoJsonText.length }} total bytes")

            onProgress(ProgressData.complete(selectedCountries.size.toLong(), "Airport download completed successfully"))
            Logger.log("AIRPORTS", LogLevel.INFO, "Airport download completed successfully for ${selectedCountryCodes.size} countries")

            Result.success(downloadedData)
        } catch (e: Exception) {
            Logger.log("AIRPORTS_IMPORT", LogLevel.ERROR, "Download process failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Download GeoJSON text from a specific URL
     */
    private suspend fun downloadGeoJsonText(url: String, onProgress: (ProgressData) -> Unit): String {
        val result = downloadManager.downloadText(url, onProgress = onProgress)

        if (result.isSuccess) {
            return result.getOrThrow()
        } else {
            throw result.exceptionOrNull() ?: Exception("Download failed for $url")
        }
    }

    /**
     * Basic JSON validation (checks if string starts and ends with braces/brackets)
     * More thorough validation can be added later if needed
     */
    private fun isValidJson(jsonText: String): Boolean {
        val trimmed = jsonText.trim()
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
               (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }
}

/**
 * Container for downloaded country GeoJSON data
 * Passed through without parsing for later processing
 */
data class CountryGeoJsonData(
    val country: org.mountaincircles.app.modules.airports.logic.data.AirportSource,
    val geoJsonText: String
)
