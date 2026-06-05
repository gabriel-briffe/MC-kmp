package org.mountaincircles.app.modules.skysight.logic.controllers

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import kotlinx.datetime.LocalDate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.RasterResampling
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.sources.rememberImageSource
import org.maplibre.compose.util.PositionQuad
import org.maplibre.spatialk.geojson.Position
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.NetCDFReaderV2
import org.mountaincircles.app.modules.skysight.logic.SkysightUtils
import org.mountaincircles.app.modules.skysight.logic.data.LayerColorStop
import org.mountaincircles.app.modules.skysight.logic.data.TimePair
import org.mountaincircles.app.modules.skysight.logic.data.TileLayer
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.ui.map.LayerDescriptor
import org.mountaincircles.app.ui.map.LayerRegistrationHelper
import org.mountaincircles.app.ui.map.LayerZIndex
import kotlin.math.floor
import kotlin.math.min

/**
 * Tile pipeline for SkysightTilingControllerV2.
 * Extracted for clearer separation of concerns.
 */
object SkysightTilingControllerV2Tiles {

    /**
     * Get list of currently active tile IDs
     */
    fun getActiveTileIds(module: SkysightModule): List<String> {
        val activeTiles = module.state.value.activeTileLayers.keys.toList()
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Active tiles: ${activeTiles.joinToString(", ")}")
        return activeTiles
    }

    fun getTilesToRemove(activeTiles: List<String>, requiredTiles: List<String>): List<String> {
        val tilesToRemove = activeTiles.filter { it !in requiredTiles }
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Tiles to remove: ${tilesToRemove.joinToString(", ")}")
        return tilesToRemove
    }

    fun getTilesToAdd(activeTiles: List<String>, requiredTiles: List<String>): List<String> {
        val tilesToAdd = requiredTiles.filter { it !in activeTiles }
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Tiles to add: ${tilesToAdd.joinToString(", ")}")
        return tilesToAdd
    }

    /**
     * Calculate which tiles are needed for viewport
     */
    fun calculateRequiredTiles(viewportBounds: List<Double>): List<String> {
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Calculating required tiles for viewport bounds: [${viewportBounds.joinToString(", ")}]")
        val requiredTiles = calculateViewportTiles(viewportBounds)
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Required tiles: ${requiredTiles.joinToString(", ") { it.id }}")
        return requiredTiles.map { it.id }
    }

    /**
     * Calculate which 1°×1° geographic tiles intersect the given viewport
     */
    fun calculateViewportTiles(viewportBounds: List<Double>): List<GeoTile> {
        val (west, south, east, north) = viewportBounds
        val minTileLat = kotlin.math.floor(south).toInt().coerceAtLeast(-90)
        val maxTileLat = kotlin.math.floor(north).toInt().coerceAtMost(89)
        val minTileLon = kotlin.math.floor(west).toInt().coerceAtLeast(-180)
        val maxTileLon = kotlin.math.floor(east).toInt().coerceAtMost(179)
        val tiles = mutableListOf<GeoTile>()
        for (tileLat in minTileLat..maxTileLat) {
            for (tileLon in minTileLon..maxTileLon) {
                val tileWest = tileLon.toDouble()
                val tileEast = (tileLon + 1).toDouble()
                val tileSouth = tileLat.toDouble()
                val tileNorth = (tileLat + 1).toDouble()
                if (!(tileEast <= west || tileWest >= east || tileNorth <= south || tileSouth >= north)) {
                    tiles.add(GeoTile(latMin = tileLat, latMax = tileLat + 1, lonMin = tileLon, lonMax = tileLon + 1))
                }
            }
        }
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Calculated ${tiles.size} tiles for viewport [${west}°, ${south}°, ${east}°, ${north}°]")
        return tiles
    }

    suspend fun createSingleTile(
        module: SkysightModule,
        globalState: GlobalState,
        tileId: String,
        layerId: String,
        date: LocalDate,
        timePair: TimePair
    ): Result<String> {
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "ensureNetCDFFileAvailable for tile: $tileId")
        ensureNetCDFFileAvailable(module, layerId, date, timePair)
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "extractAndParseNetCDFSubmatrix for tile: $tileId")
        val parsedSubmatrixResult = extractAndParseNetCDFSubmatrix(module, tileId, layerId, date, timePair)
        if (parsedSubmatrixResult.isFailure) return parsedSubmatrixResult.map { "" }
        val parsedSubmatrix = parsedSubmatrixResult.getOrThrow()
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "createAndRegisterTile for tile: $tileId")
        val fileKey = SkysightTilingControllerV2Viewport.constructDataFileKey(layerId, date, timePair.hour, timePair.minute)
        val netCDFFilePath = module.getLocalFilePath(fileKey)
        val variables = NetCDFReaderV2.readNetCDFFile(netCDFFilePath)
        val latVariable = variables.find { it.name == "lat" || it.name == "latitude" }
        val lonVariable = variables.find { it.name == "lon" || it.name == "longitude" }
        val fileDimensions = NetCDFReaderV2.readNetCDFDimensions(netCDFFilePath)
        val latCoords = NetCDFReaderV2.readVariableData(netCDFFilePath, latVariable!!, fileDimensions)?.map { it.toDouble() } ?: emptyList()
        val lonCoords = NetCDFReaderV2.readVariableData(netCDFFilePath, lonVariable!!, fileDimensions)?.map { it.toDouble() } ?: emptyList()
        return createAndRegisterTile(module, tileId, parsedSubmatrix, layerId, latCoords, lonCoords)
    }

    suspend fun ensureNetCDFFileAvailable(module: SkysightModule, layerId: String, date: LocalDate, timePair: TimePair) {
        SkysightDataControllerV2.ensureNetCDFFileAvailable(module, layerId, date, timePair)
    }

    suspend fun extractAndParseNetCDFSubmatrix(
        module: SkysightModule,
        tileId: String,
        layerId: String,
        date: LocalDate,
        timePair: TimePair
    ): Result<List<List<Float>>> {
        return try {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Starting submatrix extraction for tile $tileId")
            val fileKey = SkysightTilingControllerV2Viewport.constructDataFileKey(layerId, date, timePair.hour, timePair.minute)
            val netCDFFilePath = module.getLocalFilePath(fileKey)
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "NetCDF file path: $netCDFFilePath")
            val geoTile = GeoTile.fromTileId(tileId)
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Tile bounds: lat[${geoTile.latMin}..${geoTile.latMax}], lon[${geoTile.lonMin}..${geoTile.lonMax}]")
            val variables = NetCDFReaderV2.readNetCDFFile(netCDFFilePath)
            val dataVariable = variables.find { it.name == layerId }
            val latVariable = variables.find { it.name == "lat" || it.name == "latitude" }
            val lonVariable = variables.find { it.name == "lon" || it.name == "longitude" }
            if (dataVariable == null) throw Exception("Data variable '$layerId' not found in NetCDF file")
            if (latVariable == null) throw Exception("Latitude variable not found in NetCDF file")
            if (lonVariable == null) throw Exception("Longitude variable not found in NetCDF file")
            val dimensions = NetCDFReaderV2.readNetCDFDimensions(netCDFFilePath)
            val latCoords = NetCDFReaderV2.readVariableData(netCDFFilePath, latVariable, dimensions)?.map { it.toDouble() }
                ?: throw Exception("Could not read latitude coordinates")
            val lonCoords = NetCDFReaderV2.readVariableData(netCDFFilePath, lonVariable, dimensions)?.map { it.toDouble() }
                ?: throw Exception("Could not read longitude coordinates")
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Read ${latCoords.size} lat coords, ${lonCoords.size} lon coords")
            val latIndices = latCoords.withIndex()
                .filter { (_, lat) -> lat >= geoTile.latMin.toDouble() && lat < geoTile.latMax.toDouble() }
                .map { it.index }
            val lonIndices = lonCoords.withIndex()
                .filter { (_, lon) -> lon >= geoTile.lonMin.toDouble() && lon < geoTile.lonMax.toDouble() }
                .map { it.index }
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Tile indices: lat[${latIndices.size} points], lon[${lonIndices.size} points]")
            if (latIndices.isEmpty() || lonIndices.isEmpty()) {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.WARN, "No data points found in tile bounds for tile $tileId")
                return Result.success(emptyList())
            }
            val scaledSubmatrix = NetCDFReaderV2.readVariableDataSelective(
                netCDFFilePath, dataVariable, dimensions, latIndices, lonIndices, latCoords.size, lonCoords.size
            )
            if (scaledSubmatrix == null) {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.WARN, "No submatrix data returned for tile $tileId")
                return Result.success(emptyList())
            }
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Scaled submatrix: ${scaledSubmatrix.size} x ${scaledSubmatrix.firstOrNull()?.size ?: 0}")
            val processedSubmatrix = parseSubmatrixData(scaledSubmatrix, dataVariable.attributes)
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Successfully extracted and parsed submatrix for tile $tileId: ${processedSubmatrix.size}x${processedSubmatrix.firstOrNull()?.size ?: 0}")
            Result.success(processedSubmatrix)
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Failed to extract and parse submatrix for tile $tileId: ${e.message}")
            Result.failure(e)
        }
    }

    fun parseSubmatrixData(submatrix: List<List<Float>>, attributes: Map<String, Any>): List<List<Float>> {
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Parsing submatrix data (already scaled by NetCDFReaderV2)")
        val missingValue = attributes["missing_value"] as? Number
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Missing value: $missingValue")
        return submatrix.map { row ->
            row.map { value ->
                if (missingValue != null && value == missingValue.toFloat()) Float.NaN else value
            }
        }
    }

    private fun lerpScalar(a: Float, b: Float, t: Float): Float = when {
        a.isNaN() && b.isNaN() -> Float.NaN
        a.isNaN() -> b
        b.isNaN() -> a
        else -> a * (1f - t) + b * t
    }

    private fun bilinearSample(src: List<List<Float>>, h: Int, w: Int, sy: Float, sx: Float): Float {
        val maxY = (h - 1).coerceAtLeast(0).toFloat()
        val maxX = (w - 1).coerceAtLeast(0).toFloat()
        val syC = sy.coerceIn(0f, maxY)
        val sxC = sx.coerceIn(0f, maxX)
        val y0 = floor(syC).toInt().coerceIn(0, h - 1)
        val y1 = min(y0 + 1, h - 1)
        val x0 = floor(sxC).toInt().coerceIn(0, w - 1)
        val x1 = min(x0 + 1, w - 1)
        val wy = syC - y0
        val wx = sxC - x0
        val v00 = src[y0][x0]
        val v01 = src[y0][x1]
        val v10 = src[y1][x0]
        val v11 = src[y1][x1]
        val top = lerpScalar(v00, v01, wx)
        val bottom = lerpScalar(v10, v11, wx)
        return lerpScalar(top, bottom, wy)
    }

    /**
     * Doubles grid resolution in each dimension (4× samples) via bilinear interpolation on scalar values.
     */
    private fun upscale2xBilinear(src: List<List<Float>>): List<List<Float>> {
        val h = src.size
        val w = src.firstOrNull()?.size ?: 0
        if (h == 0 || w == 0) return src
        val outH = 2 * h
        val outW = 2 * w
        val outRows = ArrayList<List<Float>>(outH)
        for (oy in 0 until outH) {
            val sy = if (outH <= 1) 0f else oy * (h - 1) / (outH - 1).toFloat()
            val row = FloatArray(outW)
            for (ox in 0 until outW) {
                val sx = if (outW <= 1) 0f else ox * (w - 1) / (outW - 1).toFloat()
                row[ox] = bilinearSample(src, h, w, sy, sx)
            }
            outRows.add(row.toList())
        }
        return outRows
    }

    fun createTileLayer(
        tile: GeoTile,
        tileData: List<List<Float>>,
        colorStops: List<LayerColorStop>,
        filterMin: Float,
        filterMax: Float
    ): ImageBitmap {
        val upscaled = upscale2xBilinear(tileData)
        val height = upscaled.size
        val width = upscaled.firstOrNull()?.size ?: 0
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Creating tile bitmap ${tile.id}: ${width}x${height} pixels (2x bilinear upscaled)")
        if (height == 0 || width == 0) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.WARN, "Empty tile data for ${tile.id}, returning empty bitmap")
            return ImageBitmap(1, 1)
        }
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        val valueColorCache = mutableMapOf<Float, Color>()
        for (localY in 0 until height) {
            val dataRow = upscaled[height - 1 - localY]
            val rowColors = Array(width) { localX ->
                val value = dataRow[localX]
                if (value == 0f) Color(0x00000000)
                else if (value >= filterMin && value <= filterMax) Color(0x00000000)
                else valueColorCache.getOrPut(value) { SkysightUtils.getColorForValue(value, colorStops) }
            }
            var x = 0
            while (x < width) {
                val currentColor = rowColors[x]
                if (currentColor == Color(0x00000000)) { x++; continue }
                var endX = x
                while (endX < width && rowColors[endX] == currentColor) endX++
                val paint = Paint().apply { this.color = currentColor; style = PaintingStyle.Fill }
                canvas.drawRect(x.toFloat(), localY.toFloat(), endX.toFloat(), (localY + 1).toFloat(), paint)
                x = endX
            }
        }
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Tile bitmap created for ${tile.id} (${valueColorCache.size} unique colors)")
        return bitmap
    }

    suspend fun addTileLayerToMap(module: SkysightModule, tileId: String, bitmap: ImageBitmap, bounds: List<Double>) {
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Adding tile layer $tileId to map")
        try {
            val tileZIndex = SkysightTilingControllerV2Viewport.calculateTileZIndex(tileId)
            val timestamp = System.currentTimeMillis()
            val layerName = "tile_${tileId}_${timestamp}"
            LayerRegistrationHelper.registerLayer(
                moduleId = "skysight",
                layerName = layerName,
                zIndex = 10 * LayerZIndex.getZIndex("skysight_bitmap") + tileZIndex.toInt(),
                layerType = LayerDescriptor.LayerType.OVERLAY,
                isInteractive = false,
                description = "SkySight tile layer for $tileId",
                tags = setOf("skysight", "tile", tileId),
                composable = {
                    Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "RENDERING TILE LAYER: $tileId")
                    val currentOpacity by module.layerOpacity.collectAsState()
                    val forecastMinZoom by module.forecastMinZoom.collectAsState()
                    val tileQuad = remember(bounds) {
                        if (bounds.size >= 4) PositionQuad(
                            topLeft = Position(bounds[0], bounds[3]),
                            topRight = Position(bounds[2], bounds[3]),
                            bottomRight = Position(bounds[2], bounds[1]),
                            bottomLeft = Position(bounds[0], bounds[1])
                        ) else null
                    }
                    if (tileQuad != null) {
                        val imageSource = rememberImageSource(position = tileQuad, bitmap = bitmap)
                        RasterLayer(
                            id = layerName,
                            source = imageSource,
                            minZoom = forecastMinZoom,
                            opacity = const(currentOpacity),
                            resampling = const(RasterResampling.Linear)
                        )
                    } else {
                        Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Invalid bounds for tile $tileId: ${bounds.joinToString(", ")}")
                        null
                    }
                }
            )
            Logger.log("NAVIGATION_DEBUG", LogLevel.INFO, "TILE LAYER REGISTERED SUCCESSFULLY: $layerName")
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Failed to add tile layer $tileId to map: ${e.message}")
            throw e
        }
    }

    fun calculateDataBounds(
        tile: GeoTile,
        tileData: List<List<Float>>,
        latCoords: List<Double>,
        lonCoords: List<Double>
    ): List<Double> {
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Calculating extended data bounds for tile ${tile.id}")
        val latIndices = mutableListOf<Int>()
        val lonIndices = mutableListOf<Int>()
        for (latIndex in latCoords.indices) {
            val lat = latCoords[latIndex]
            if (lat >= tile.latMin.toDouble() && lat <= tile.latMax.toDouble()) latIndices.add(latIndex)
        }
        for (lonIndex in lonCoords.indices) {
            val lon = lonCoords[lonIndex]
            if (lon >= tile.lonMin.toDouble() && lon <= tile.lonMax.toDouble()) lonIndices.add(lonIndex)
        }
        if (latIndices.isEmpty() || lonIndices.isEmpty()) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.WARN, "No coordinate data found for tile ${tile.id}, using tile bounds")
            return tile.bounds
        }
        val extractedLatCoords = latIndices.map { latCoords[it] }
        val extractedLonCoords = lonIndices.map { lonCoords[it] }
        val minLat = extractedLatCoords.minOrNull() ?: tile.latMin.toDouble()
        val maxLat = extractedLatCoords.maxOrNull() ?: tile.latMax.toDouble()
        val minLon = extractedLonCoords.minOrNull() ?: tile.lonMin.toDouble()
        val maxLon = extractedLonCoords.maxOrNull() ?: tile.lonMax.toDouble()
        val latInterval = if (extractedLatCoords.size > 1) kotlin.math.abs(extractedLatCoords[1] - extractedLatCoords[0]) else 0.01
        val lonInterval = if (extractedLonCoords.size > 1) kotlin.math.abs(extractedLonCoords[1] - extractedLonCoords[0]) else 0.01
        val extendedMinLat = minLat - (latInterval / 2.0)
        val extendedMaxLat = maxLat + (latInterval / 2.0)
        val extendedMinLon = minLon - (lonInterval / 2.0)
        val extendedMaxLon = maxLon + (lonInterval / 2.0)
        Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Tile ${tile.id} data bounds: lat ${minLat}-${maxLat}, lon ${minLon}-${maxLon}")
        return listOf(extendedMinLon, extendedMinLat, extendedMaxLon, extendedMaxLat)
    }

    suspend fun createAndRegisterTile(
        module: SkysightModule,
        tileId: String,
        submatrix: List<List<Float>>,
        layerId: String,
        latCoords: List<Double>,
        lonCoords: List<Double>
    ): Result<String> {
        return try {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Creating and registering tile: $tileId")
            val selectedLayer = module.state.value.availableLayers.find { it.id == layerId }
            if (selectedLayer == null) {
                Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Layer '$layerId' not found in available layers")
                return Result.failure(Exception("Layer '$layerId' not found"))
            }
            val filterRange = module.getLayerFilterRange(selectedLayer.id)
            val filterMin = filterRange.first
            val filterMax = filterRange.second
            val geoTile = GeoTile.fromTileId(tileId)
            val bitmap = createTileLayer(geoTile, submatrix, selectedLayer.legend.colors, filterMin, filterMax)
            val dataBounds = calculateDataBounds(geoTile, submatrix, latCoords, lonCoords)
            addTileLayerToMap(module, tileId, bitmap, dataBounds)
            val tileLayer = TileLayer(tileId, bitmap, dataBounds)
            module.updateState { it.copy(activeTileLayers = it.activeTileLayers.toMutableMap().apply { put(tileId, tileLayer) }) }
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Successfully created and registered tile: $tileId")
            Result.success(tileId)
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Failed to create and register tile $tileId: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun removeTile(module: SkysightModule, tileId: String): Result<Unit> {
        return try {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Removing tile $tileId from map and state")
            val allLayers = LayerRegistrationHelper.layerManager.layers.value
            val tileLayers = allLayers.filter { it.tags.contains(tileId) && it.tags.contains("tile") }
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.DEBUG, "Found ${tileLayers.size} layer registrations for tile $tileId")
            tileLayers.forEach { layer ->
                try {
                    val wasRegistered = LayerRegistrationHelper.layerManager.isLayerRegistered(layer.id)
                    if (wasRegistered) LayerRegistrationHelper.unregisterLayer(layer.id)
                } catch (e: Exception) {
                    Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Failed to unregister layer ${layer.id}: ${e.message}")
                }
            }
            module.updateState { it.copy(activeTileLayers = it.activeTileLayers.toMutableMap().apply { remove(tileId) }) }
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.INFO, "Successfully removed tile $tileId (${tileLayers.size} layers)")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.log("SKYSIGHT_TILING_V2", LogLevel.ERROR, "Failed to remove tile $tileId: ${e.message}")
            Result.failure(e)
        }
    }
}

/**
 * Geographic tile definition for 1°×1° chunks
 */
data class GeoTile(
    val latMin: Int,
    val latMax: Int,
    val lonMin: Int,
    val lonMax: Int
) {
    val id: String = "${latMin}N_${lonMin}E"
    val bounds = listOf(lonMin.toDouble(), latMin.toDouble(), lonMax.toDouble(), latMax.toDouble())

    companion object {
        fun fromTileId(tileId: String): GeoTile {
            val parts = tileId.split("_")
            val latPart = parts[0]
            val lonPart = parts[1]
            val latMin = latPart.substringBefore("N").toInt()
            val lonMin = lonPart.substringBefore("E").toInt()
            return GeoTile(latMin = latMin, latMax = latMin + 1, lonMin = lonMin, lonMax = lonMin + 1)
        }
    }
}
