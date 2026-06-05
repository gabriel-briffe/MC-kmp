package org.mountaincircles.app.modules.skysight.logic.controllers

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.NetCDFReaderV2
import org.mountaincircles.app.modules.skysight.logic.data.TimePair
import org.mountaincircles.app.modules.skysight.logic.data.formatValue
import org.mountaincircles.app.state.GlobalState

/**
 * Labels pipeline for SkysightTilingControllerV2.
 * Extracted for clearer separation of concerns.
 */
object SkysightTilingControllerV2Labels {

    /**
     * Data class for selective viewport data (labels)
     */
    data class SelectiveViewportData(
        val latCoords: List<Double>,
        val lonCoords: List<Double>,
        val dataMatrix: List<List<Float>>
    )

    /**
     * Update labels for navigation change (V2 approach - selective NetCDF reading)
     */
    suspend fun updateLabels(
        module: SkysightModule,
        globalState: GlobalState,
        viewportBounds: List<Double>,
        layerId: String,
        date: LocalDate,
        timePair: TimePair
    ): Result<Unit> {
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Updating labels for navigation change")
        return try {
            val fileKey = SkysightTilingControllerV2Viewport.constructDataFileKey(layerId, date, timePair.hour, timePair.minute)
            val netCDFFilePath = module.getLocalFilePath(fileKey)
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "NetCDF file path for labels: $netCDFFilePath")
            val submatrixData = extractAndParseNetCDFSubmatrixForLabels(
                netCDFFilePath = netCDFFilePath,
                viewportBounds = viewportBounds,
                layerId = layerId
            )
            if (submatrixData.isFailure) {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Failed to extract submatrix for labels: ${submatrixData.exceptionOrNull()?.message}")
                module.updateState { it.copy(viewportData = null) }
                return Result.failure(submatrixData.exceptionOrNull() ?: Exception("Unknown error"))
            }
            val labelFeatures = createLabelFeaturesFromSubmatrix(
                submatrix = submatrixData.getOrThrow(),
                layerId = layerId,
                module = module
            )
            module.updateState { it.copy(viewportData = labelFeatures) }
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Successfully updated labels: ${labelFeatures.features.size} features")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Failed to update labels: ${e.message}")
            module.updateState { it.copy(viewportData = null) }
            Result.failure(e)
        }
    }

    /**
     * Extract selective submatrix data for viewport labels (similar to tiles but for entire viewport)
     */
    suspend fun extractAndParseNetCDFSubmatrixForLabels(
        netCDFFilePath: String,
        viewportBounds: List<Double>,
        layerId: String
    ): Result<SelectiveViewportData> {
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Extracting selective submatrix for labels from viewport: ${viewportBounds.joinToString(", ")}")
        return try {
            val variables = NetCDFReaderV2.readNetCDFFile(netCDFFilePath)
            val dataVariable = variables.find { it.name == layerId }
            val latVariable = variables.find { it.name == "lat" || it.name == "latitude" }
            val lonVariable = variables.find { it.name == "lon" || it.name == "longitude" }
            if (dataVariable == null) throw Exception("Data variable '$layerId' not found in NetCDF file")
            if (latVariable == null) throw Exception("Latitude variable not found in NetCDF file")
            if (lonVariable == null) throw Exception("Longitude variable not found in NetCDF file")
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Found variables: data=$layerId, lat=${latVariable.name}, lon=${lonVariable.name}")
            val dimensions = NetCDFReaderV2.readNetCDFDimensions(netCDFFilePath)
            val latitudes = NetCDFReaderV2.readVariableData(netCDFFilePath, latVariable, dimensions)?.map { it.toDouble() }
                ?: throw Exception("Could not read latitude coordinates")
            val longitudes = NetCDFReaderV2.readVariableData(netCDFFilePath, lonVariable, dimensions)?.map { it.toDouble() }
                ?: throw Exception("Could not read longitude coordinates")
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Read ${latitudes.size} latitudes and ${longitudes.size} longitudes")
            val (west, south, east, north) = viewportBounds
            val latIndices = latitudes.withIndex()
                .filter { (_, lat) -> lat >= south && lat <= north }
                .map { it.index }
                .sorted()
            val lonIndices = longitudes.withIndex()
                .filter { (_, lon) -> lon >= west && lon <= east }
                .map { it.index }
                .sorted()
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Viewport contains ${latIndices.size} lat points and ${lonIndices.size} lon points")
            if (latIndices.isEmpty() || lonIndices.isEmpty()) {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.WARN, "No data points found in viewport")
                return Result.success(SelectiveViewportData(emptyList(), emptyList(), emptyList()))
            }
            val selectiveData = NetCDFReaderV2.readVariableDataSelective(
                filePath = netCDFFilePath,
                variable = dataVariable,
                dimensions = dimensions,
                latIndices = latIndices,
                lonIndices = lonIndices,
                fullLatSize = latitudes.size,
                fullLonSize = longitudes.size
            )
            if (selectiveData == null) return Result.failure(Exception("Failed to read selective data"))
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Raw selective data: ${selectiveData.size} x ${selectiveData.firstOrNull()?.size ?: 0}")
            val scaledData = SkysightTilingControllerV2Tiles.parseSubmatrixData(selectiveData, dataVariable.attributes)
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Scaled data: ${scaledData.size} x ${scaledData.firstOrNull()?.size ?: 0}")
            val viewportLatCoords = latIndices.map { latitudes[it] }
            val viewportLonCoords = lonIndices.map { longitudes[it] }
            val result = SelectiveViewportData(latCoords = viewportLatCoords, lonCoords = viewportLonCoords, dataMatrix = scaledData)
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Successfully extracted selective viewport data: ${viewportLatCoords.size} x ${viewportLonCoords.size}")
            Result.success(result)
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Failed to extract selective submatrix for labels: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Create label features from selective viewport data
     */
    suspend fun createLabelFeaturesFromSubmatrix(
        submatrix: SelectiveViewportData,
        layerId: String,
        module: SkysightModule
    ): FeatureCollection<Point, JsonObject> {
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Creating label features from submatrix")
        val features = mutableListOf<Feature<Point, JsonObject>>()
        val selectedLayer = module.state.value.availableLayers.find { it.id == layerId }
        if (selectedLayer == null) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.WARN, "Layer '$layerId' not found for label creation")
            return FeatureCollection(emptyList())
        }
        val (filterMin, filterMax) = module.getLayerFilterRange(layerId)
        val firstColorStopValue = selectedLayer.legend.colors.firstOrNull()?.value?.toFloatOrNull() ?: Float.NEGATIVE_INFINITY
        var nullCount = 0
        var withinRangeCount = 0
        var belowFirstStopCount = 0
        var validCount = 0
        var nanCount = 0
        for (latIdx in submatrix.latCoords.indices) {
            for (lonIdx in submatrix.lonCoords.indices) {
                val lat = submatrix.latCoords[latIdx]
                val lon = submatrix.lonCoords[lonIdx]
                val value = submatrix.dataMatrix.getOrNull(latIdx)?.getOrNull(lonIdx)
                when {
                    value == null -> nullCount++
                    value.isNaN() || value.isInfinite() -> nanCount++
                    value >= filterMin && value <= filterMax -> withinRangeCount++
                    value < firstColorStopValue -> belowFirstStopCount++
                    else -> {
                        val point = Point(longitude = lon, latitude = lat)
                        val properties = buildJsonObject {
                            put("value", JsonPrimitive(selectedLayer.formatValue(value)))
                            put("lat", JsonPrimitive(lat))
                            put("lon", JsonPrimitive(lon))
                        }
                        features.add(Feature(point, properties))
                        validCount++
                    }
                }
            }
        }
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG,
            "Label analysis: $nullCount null, $nanCount NaN, $withinRangeCount within-range (skipped), $belowFirstStopCount below-first-stop (skipped), $validCount valid")
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Created ${features.size} label features")
        return FeatureCollection(features)
    }
}
