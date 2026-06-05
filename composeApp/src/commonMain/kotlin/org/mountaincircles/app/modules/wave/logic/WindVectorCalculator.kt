package org.mountaincircles.app.modules.wave.logic

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import kotlin.math.*
import java.io.File
import org.mountaincircles.app.modules.wave.logic.GeoTiffPixelReader
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put


/**
 * Wind Vector Calculator
 *
 * Handles calculation of wind vectors from U/V component GeoTIFF files.
 * Processes geospatial data to generate wind speed and direction information.
 */
class WindVectorCalculator(private val waveDirectory: String) {

    /**
     * Cached GeoTIFF metadata to avoid re-parsing expensive header information
     */
    private data class CachedGeoTiffMetadata(
        val filePath: String,
        val stripOffsets: LongArray,
        val width: Int,
        val height: Int,
        val pixelScaleLng: Double,
        val pixelScaleLat: Double,
        val originLng: Double,
        val originLat: Double
    )

    // Cache for GeoTIFF metadata - key is "forecastDate_targetDate_hour_pressure_component"
    private val metadataCache = mutableMapOf<String, CachedGeoTiffMetadata>()
    private val cacheMutex = Mutex()

    /**
     * Generate cache key for GeoTIFF metadata
     */
    private fun getCacheKey(forecastDate: String, targetDate: String, hour: Int, pressure: Int, component: String, region: String): String {
        return "${forecastDate}_${targetDate}_${hour}_${pressure}_${component}_${region}"
    }

    /**
     * Get cached GeoTIFF metadata, parsing and caching if not available
     */
    private suspend fun getCachedMetadata(
        filePath: String,
        forecastDate: String,
        targetDate: String,
        hour: Int,
        pressure: Int,
        component: String,
        region: String
    ): CachedGeoTiffMetadata = cacheMutex.withLock {
        val cacheKey = getCacheKey(forecastDate, targetDate, hour, pressure, component, region)

        metadataCache[cacheKey] ?: run {
            Logger.log("WIND_CALCULATOR", LogLevel.DEBUG, "Cache miss for $cacheKey, parsing metadata")

            // Parse metadata (expensive operation)
            val metadata = parseGeoTiffMetadata(filePath)

            // Cache it
            metadataCache[cacheKey] = metadata
            Logger.log("WIND_CALCULATOR", LogLevel.DEBUG, "Cached metadata for $cacheKey")

            metadata
        }
    }

    /**
     * Parse GeoTIFF metadata from file (expensive operation)
     * Validates file integrity and throws exception for corrupted files
     */
    private suspend fun parseGeoTiffMetadata(filePath: String): CachedGeoTiffMetadata = withContext(Dispatchers.IO) {
        // Read all metadata from TIFF header (no more hardcoded values!)
        val metadata = GeoTiffPixelReader.readGeoTiffMetadata(filePath)

        // ✅ VALIDATE: Check if we have valid strip offsets
        if (metadata.stripOffsets.isEmpty() || metadata.stripOffsets.any { it <= 0 }) {
            throw IllegalStateException("Corrupted TIFF file $filePath: invalid strip offsets")
        }

        // ✅ VALIDATE: Basic sanity checks on metadata
        if (metadata.width <= 0 || metadata.height <= 0) {
            throw IllegalStateException("Corrupted TIFF file $filePath: invalid dimensions ${metadata.width}x${metadata.height}")
        }

        Logger.log("WIND_CALCULATOR", LogLevel.DEBUG, "Successfully parsed TIFF metadata: ${metadata.width}x${metadata.height}, strips=${metadata.stripOffsets.size}")

        CachedGeoTiffMetadata(
            filePath = filePath,
            stripOffsets = metadata.stripOffsets,
            width = metadata.width,
            height = metadata.height,
            pixelScaleLng = metadata.pixelScaleLng,
            pixelScaleLat = metadata.pixelScaleLat,
            originLng = metadata.originLng,
            originLat = metadata.originLat
        )
    }

    /**
     * Clear the metadata cache (call when selection changes)
     */
    fun invalidateMetadataCache() {
        metadataCache.clear()
        Logger.log("WIND_CALCULATOR", LogLevel.DEBUG, "Metadata cache cleared")
    }

    /**
     * Build region-specific file path for wind component files
     */
    private fun buildRegionFilePath(
        forecastDate: String,
        targetDate: String,
        hour: Int,
        pressure: Int,
        component: String,
        region: String
    ): String {
        val hourPadded = hour.toString().padStart(2, '0')
        val filename = "arome_${component}_${region}_${forecastDate}_${targetDate}_${hourPadded}_${pressure}.tiff"
        return "${getWaveDirectory()}/$filename"
    }

    /**
     * Get wave directory path
     */
    private fun getWaveDirectory(): String = waveDirectory

    /**
     * Extract region from filename (e.g., "arome_u_MiddleEast_..." → "MiddleEast")
     * Uses simple string splitting instead of regex for reliability
     */
    private fun extractRegionFromFilename(filePath: String): String {
        val fileName = java.io.File(filePath).name
        // Filename format: arome_[u|v]_[region]_[dates]_[pressure].tiff
        // Split by underscore: ["arome", "u", "MiddleEast", "2026-01-04", ...]
        val parts = fileName.split("_")
        return parts.getOrElse(2) { "" }  // Region is at index 2
    }


    /**
     * Calculate wind vectors for a given map viewport
     *
     * @param viewportBounds The current map viewport bounds [west, south, east, north]
     * @param uFilePath Path to U component GeoTIFF file
     * @param vFilePath Path to V component GeoTIFF file
     * @return List of Feature objects representing wind vectors
     */
    suspend fun calculateWindVectors(
        viewportBounds: List<Double>,
        uFilePath: String,
        vFilePath: String,
        barbInterval: Float = 10f,
        geographicSpacingLng: Double,
        geographicSpacingLat: Double,
        forecastDate: String? = null,
        targetDate: String? = null,
        hour: Int? = null,
        pressure: Int? = null,
        cameraBearing: Float = 0.0f,
        showZeroWindBarbs: Boolean = false
    ): List<Feature<Point, kotlinx.serialization.json.JsonObject>> = withContext(Dispatchers.Default) {
        Logger.log("WIND_CALCULATOR", LogLevel.INFO, "Starting wind vector calculation")

        try {
            // Check if wind component files exist
            val uFile = File(uFilePath)
            val vFile = File(vFilePath)

            if (!uFile.exists() || !vFile.exists()) {
                Logger.log("WIND_CALCULATOR", LogLevel.WARN, "Wind component files not found - returning dummy point at North Pole")
                Logger.log("WIND_CALCULATOR", LogLevel.DEBUG, "U file exists: ${uFile.exists()}, V file exists: ${vFile.exists()}")
                val dummyFeature = Feature<Point, kotlinx.serialization.json.JsonObject>(
                    geometry = Point(Position(0.0, 90.0)),
                    properties = buildJsonObject {
                        put("speed", 0.0)
                        put("direction", 0.0)
                    }
                )
                return@withContext listOf(dummyFeature)
            }

            // Get cached GeoTIFF metadata (avoids re-parsing expensive header data)
            // Validation happens during parsing - throws exception for corrupted files
            val uMetadata = try {
                if (forecastDate != null && targetDate != null && hour != null && pressure != null) {
                    val region = extractRegionFromFilename(uFilePath)
                    getCachedMetadata(uFilePath, forecastDate, targetDate, hour, pressure, "u", region)
                } else {
                    // Fallback to parsing if parameters not provided
                    parseGeoTiffMetadata(uFilePath)
                }
            } catch (e: Exception) {
                Logger.log("WIND_CALCULATOR", LogLevel.WARN, "Failed to parse U component metadata: ${e.message}")
                Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "ABOUT TO RETURN EMPTY FEATURES FOR U COMPONENT FAILURE")
                return@withContext emptyList<Feature<Point, kotlinx.serialization.json.JsonObject>>()
            }

            val vMetadata = try {
                if (forecastDate != null && targetDate != null && hour != null && pressure != null) {
                    val region = extractRegionFromFilename(vFilePath)
                    getCachedMetadata(vFilePath, forecastDate, targetDate, hour, pressure, "v", region)
                } else {
                    // Fallback to parsing if parameters not provided
                    parseGeoTiffMetadata(vFilePath)
                }
            } catch (e: Exception) {
                Logger.log("WIND_CALCULATOR", LogLevel.WARN, "Failed to parse V component metadata: ${e.message}")
                Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "ABOUT TO RETURN EMPTY FEATURES FOR V COMPONENT FAILURE")
                return@withContext emptyList<Feature<Point, kotlinx.serialization.json.JsonObject>>()
            }

            // Use cached metadata
            val geotiffWidth = uMetadata.width
            val geotiffHeight = uMetadata.height
            val originLng = uMetadata.originLng
            val originLat = uMetadata.originLat
            val pixelScaleLng = uMetadata.pixelScaleLng
            val pixelScaleLat = uMetadata.pixelScaleLat


            // Generate wind vectors within the viewport bounds
            val features = mutableListOf<Feature<Point, kotlinx.serialization.json.JsonObject>>()

            // Sample ALL pixels within the viewport bounds (not just a fixed grid)
            val west = viewportBounds[0]
            val south = viewportBounds[1]
            val east = viewportBounds[2]
            val north = viewportBounds[3]

            var totalSampled = 0
            var inBounds = 0
            var withSpeed = 0

            // Convert viewport bounds to pixel bounds in the GeoTIFF
            val minPixelX = maxOf(0, ((west - originLng) / pixelScaleLng).toInt())
            val maxPixelX = minOf(geotiffWidth - 1, ((east - originLng) / pixelScaleLng).toInt())
            val minPixelY = maxOf(0, ((originLat - north) / pixelScaleLat).toInt())  // Note: Y increases downward
            val maxPixelY = minOf(geotiffHeight - 1, ((originLat - south) / pixelScaleLat).toInt())

            // Calculate downsampling intervals by converting geographic spacing to pixel spacing for this TIFF file
            val horizontalInterval = maxOf(1, (geographicSpacingLng / pixelScaleLng).toInt())
            val verticalInterval = maxOf(1, (geographicSpacingLat / pixelScaleLat).toInt())

            // Read and process pixels in chunks for optimal memory usage
            // Instead of loading entire 803K pixel arrays, read only viewport pixels
            readWindVectorsFromViewport(
                uFilePath, vFilePath,
                minPixelX, maxPixelX, minPixelY, maxPixelY,
                horizontalInterval, verticalInterval,
                geotiffWidth, geotiffHeight, originLng, originLat, pixelScaleLng, pixelScaleLat,
                viewportBounds, geographicSpacingLng, geographicSpacingLat,
                uMetadata, vMetadata
            ) { pixelX, pixelY, lng, lat, uValue, vValue ->
                totalSampled++
                inBounds++

                // Calculate wind speed and direction
                // U = eastward component, V = northward component
                // Direction = direction wind is blowing TO (as shown in the logs)
                val speed = sqrt(uValue * uValue + vValue * vValue)
                val direction = (atan2(uValue, vValue) * 180.0 / PI).toFloat()

                // Normalize direction to 0-360 degrees
                val normalizedDirection = if (direction < 0) direction + 360f else direction

                // Adjust direction for camera rotation (add camera bearing)
                val adjustedDirection = (normalizedDirection - cameraBearing) % 360.0f

                // Only add vectors with meaningful speed
                // If showZeroWindBarbs is false, skip speeds < 1.0 m/s to avoid showing wind barbs for very low winds
                val minSpeedThreshold = if (showZeroWindBarbs) 0.1f else 1.0f
                if (speed > minSpeedThreshold) {
                    withSpeed++

                    // Create Feature object using maplibre-spatialk
                    val feature = Feature<Point, kotlinx.serialization.json.JsonObject>(
                        geometry = Point(Position(lng, lat)),
                        properties = buildJsonObject {
                            put("speed", speed)
                            put("direction", adjustedDirection)
                        }
                    )
                    features.add(feature)
                }
            }

            // Removed excessive pixel sampling stats logging

            Logger.log("WIND_CALCULATOR", LogLevel.INFO, "Wind vector calculation completed - generated ${features.size} vectors")

            features

        } catch (e: Exception) {
            Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "Failed to calculate wind vectors: ${e.message}", e)
            // Return dummy point at North Pole on error
            val dummyFeature = Feature<Point, kotlinx.serialization.json.JsonObject>(
                geometry = Point(Position(0.0, 90.0)),
                properties = buildJsonObject {
                    put("speed", 0.0)
                    put("direction", 0.0)
                }
            )
            listOf(dummyFeature)
        }
    }

    /**
     * Extract viewport bounds using meters-per-pixel calculation
     * This uses MapLibre's Web Mercator projection math for accurate geographic bounds
     */
    fun extractViewportBounds(cameraState: org.maplibre.compose.camera.CameraState): List<Double> {
        try {
            val projection = cameraState.projection
            if (projection == null) {
                Logger.log("WIND_CALCULATOR", LogLevel.WARN, "Camera projection not available")
                // Fallback to small bounds around camera center
                val centerLat = cameraState.position.target.latitude
                val centerLng = cameraState.position.target.longitude
                return listOf(centerLng - 0.1, centerLat - 0.1, centerLng + 0.1, centerLat + 0.1)
            }

            // Get the exact visible region from MapLibre
            val visibleRegion = projection.queryVisibleRegion()

            // Removed excessive viewport corner logging

            // For wind vectors, we'll use a bounding box that encompasses all four corners
            // This handles cases where the visible region is a trapezoid due to camera tilt
            val lngs = listOf(
                visibleRegion.farLeft.longitude,
                visibleRegion.farRight.longitude,
                visibleRegion.nearLeft.longitude,
                visibleRegion.nearRight.longitude
            )
            val lats = listOf(
                visibleRegion.farLeft.latitude,
                visibleRegion.farRight.latitude,
                visibleRegion.nearLeft.latitude,
                visibleRegion.nearRight.latitude
            )

            val west = lngs.minOrNull() ?: visibleRegion.farLeft.longitude
            val east = lngs.maxOrNull() ?: visibleRegion.farRight.longitude
            val south = lats.minOrNull() ?: visibleRegion.nearLeft.latitude
            val north = lats.maxOrNull() ?: visibleRegion.farLeft.latitude

            // Removed bounds calculation debug logging

            return listOf(west, south, east, north)

        } catch (e: Exception) {
            Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "Failed to get visible region: ${e.message}", e)
            // Fallback to small bounds around camera center
            val centerLat = cameraState.position.target.latitude
            val centerLng = cameraState.position.target.longitude
            return listOf(centerLng - 0.1, centerLat - 0.1, centerLng + 0.1, centerLat + 0.1)
        }
    }


    /**
     * Calculate the first offset for sampling positions using systematic alignment.
     *
     * This ensures consistent geographic spacing across all weather data files by
     * starting from the viewport edge and finding the next valid sampling position
     * that falls within available raster data.
     */
    private fun calculateFirstOffset(viewportEdge: Double, interval: Double, rasterStart: Double): Double {
        require(interval > 0.0) { "Interval must be positive" }

        val idealStart = viewportEdge + interval / 2  // Half interval from viewport edge
        val effectiveBoundary = maxOf(viewportEdge, rasterStart)  // Don't start before viewport or raster
        val delta = effectiveBoundary - idealStart

        val skips = if (delta <= 0.0) 0 else kotlin.math.ceil(delta / interval).toInt()

        return (interval / 2) + skips * interval
    }

    /**
     * Read and process wind vectors from viewport pixels only (chunked reading)
     */
    private suspend fun readWindVectorsFromViewport(
        uFilePath: String,
        vFilePath: String,
        minPixelX: Int,
        maxPixelX: Int,
        minPixelY: Int,
        maxPixelY: Int,
        horizontalInterval: Int,
        verticalInterval: Int,
        geotiffWidth: Int,
        geotiffHeight: Int,
        originLng: Double,
        originLat: Double,
        pixelScaleLng: Double,
        pixelScaleLat: Double,
        viewportBounds: List<Double>,
        geographicSpacingLng: Double,
        geographicSpacingLat: Double,
        uMetadata: CachedGeoTiffMetadata,
        vMetadata: CachedGeoTiffMetadata,
        processPixel: (pixelX: Int, pixelY: Int, lng: Double, lat: Double, uValue: Double, vValue: Double) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Calculate sampling start positions using systematic offset calculation
        val viewportWest = viewportBounds[0]
        val viewportSouth = viewportBounds[1]

        // Longitude: calculate offset from viewport west to first valid sampling position
        val lngOffset = calculateFirstOffset(viewportWest, geographicSpacingLng, originLng)
        val startLng = viewportWest + lngOffset

        // Convert geographic start position to pixel coordinates within this file
        val lngPixelOffset = ((startLng - originLng) / pixelScaleLng).toInt()
        val startPixelX = lngPixelOffset

        // Latitude: calculate offset from viewport south to first valid sampling position
        // Note: latitude increases northward, originLat is top-left (northernmost) corner
        val fileSouthLat = originLat - (geotiffHeight * pixelScaleLat)  // Southernmost latitude in file
        val latOffset = calculateFirstOffset(viewportSouth, geographicSpacingLat, maxOf(viewportSouth, fileSouthLat))
        val startLat = viewportSouth + latOffset

        val latPixelOffset = ((originLat - startLat) / pixelScaleLat).toInt()  // Y increases downward
        val startPixelY = maxOf(minPixelY, minOf(maxPixelY, latPixelOffset))

        // Collect all pixel coordinates we need to read
        val pixelCoordinates = mutableListOf<Pair<Int, Int>>()

        for (pixelY in startPixelY downTo minPixelY step verticalInterval) {  // Y decreases northward
            for (pixelX in startPixelX..maxPixelX step horizontalInterval) {
                if (pixelX >= 0 && pixelX < geotiffWidth && pixelY >= 0 && pixelY < geotiffHeight) {
                    pixelCoordinates.add(pixelX to pixelY)
                }
            }
        }

        Logger.log("WIND_CALCULATOR", LogLevel.DEBUG, "Reading ${pixelCoordinates.size} pixels from viewport")

        // Read U and V values for all needed pixels at once using cached metadata (no re-parsing!)
        val uValues = readPixelValues(uFilePath, pixelCoordinates, geotiffWidth, uMetadata)
        val vValues = readPixelValues(vFilePath, pixelCoordinates, geotiffWidth, vMetadata)

        if (uValues.size != pixelCoordinates.size || vValues.size != pixelCoordinates.size) {
            Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "Failed to read pixel values - size mismatch")
            return@withContext
        }

        // Process each pixel
        pixelCoordinates.forEachIndexed { index, (pixelX, pixelY) ->
            val lng = originLng + pixelX * pixelScaleLng
            val lat = originLat - pixelY * pixelScaleLat  // Note: Y increases downward
            val uValue = uValues[index]
            val vValue = vValues[index]

            processPixel(pixelX, pixelY, lng, lat, uValue, vValue)
        }
    }

    /**
     * Read specific pixel values from GeoTIFF file (chunked reading)
     */
    private suspend fun readPixelValues(
        filePath: String,
        pixelCoordinates: List<Pair<Int, Int>>,
        width: Int,
        cachedMetadata: CachedGeoTiffMetadata
    ): DoubleArray = withContext(Dispatchers.IO) {
        try {
            if (pixelCoordinates.isEmpty()) {
                return@withContext DoubleArray(0)
            }

            // Convert pixel coordinates to linear indices
            val linearIndices = pixelCoordinates.map { (pixelX, pixelY) ->
                pixelY * width + pixelX
            }

            // Use cached strip offsets for efficient pixel reading (no re-parsing!)
            val pixelData = readPixelsWithCachedOffsets(filePath, cachedMetadata.stripOffsets, linearIndices, width)

            if (pixelData.isEmpty()) {
                Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "Failed to read pixel data with cached offsets")
                return@withContext DoubleArray(pixelCoordinates.size)
            }

            // Extract the values for our specific pixel coordinates
            val result = DoubleArray(pixelCoordinates.size)
            // pixelData[i] contains the value for pixel at linearIndices[i]
            pixelData.forEachIndexed { index, value ->
                result[index] = value
            }

            result
        } catch (e: Exception) {
            Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "Failed to read pixel values: ${e.message}", e)
            DoubleArray(pixelCoordinates.size)
        }
    }

    /**
     * Read pixels using pre-cached strip offsets (no expensive re-parsing!)
     */
    private suspend fun readPixelsWithCachedOffsets(
        filePath: String,
        stripOffsets: LongArray,
        pixelIndices: List<Int>,
        width: Int
    ): DoubleArray {
        // Use the platform-specific implementation that handles cached strip offsets
        return GeoTiffPixelReader.readPixelsWithStripOffsets(filePath, stripOffsets, pixelIndices, width)
    }

    /**
     * Validate that required files exist and are readable
     */
}
