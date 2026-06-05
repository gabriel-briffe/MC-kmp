package org.mountaincircles.app.modules.skysight.logic

import org.mountaincircles.app.io.getGlobalFileManager
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Minimal NetCDF3 reader based on netchdf library functions - V2
 */
object NetCDFReaderV2 {

    // Constants from netchdf N3builder
    private const val MAGIC_DIM = 10
    private const val MAGIC_VAR = 11
    private const val MAGIC_ATT = 12

    // Simplified data classes for our use case
    data class NetCDFVariable(
        val name: String,
        val dataType: Int, // 1=byte, 2=char, 3=short, 4=int, 5=float, 6=double
        val dimensions: List<Int>, // dimension indices
        val attributes: Map<String, Any> = emptyMap(),
        val offset: Int,
        val vsize: Int
    )

    data class NetCDFDimension(
        val name: String,
        val length: Int
    )

    /**
     * Geographic tile definition for 1°×1° chunks
     */
    data class GeoTile(
        val latMin: Int,  // Southern latitude boundary (e.g., 45 for 45°N-46°N)
        val latMax: Int,  // Northern latitude boundary (e.g., 46 for 45°N-46°N)
        val lonMin: Int,  // Western longitude boundary (e.g., 10 for 10°E-11°E)
        val lonMax: Int   // Eastern longitude boundary (e.g., 11 for 10°E-11°E)
    ) {
        val id: String = "${latMin}N_${lonMin}E"
        val bounds = listOf(lonMin.toDouble(), latMin.toDouble(), lonMax.toDouble(), latMax.toDouble())
    }

    /**
     * Calculate which 1°×1° geographic tiles intersect the given viewport
     * @param viewportBounds [west, south, east, north] in degrees
     * @return List of GeoTile objects that intersect the viewport
     */
    fun calculateViewportTiles(viewportBounds: List<Double>): List<GeoTile> {
        val (west, south, east, north) = viewportBounds

        // Convert viewport bounds to tile indices (1° tiles)
        // Use floor() to properly handle negative coordinates and ensure edge tiles are included
        val minTileLat = kotlin.math.floor(south).toInt().coerceAtLeast(-90)
        val maxTileLat = kotlin.math.floor(north).toInt().coerceAtMost(89)  // 89 because 89°-90° is the last tile
        val minTileLon = kotlin.math.floor(west).toInt().coerceAtLeast(-180)
        val maxTileLon = kotlin.math.floor(east).toInt().coerceAtMost(179)  // 179 because 179°-180° is the last tile

        val tiles = mutableListOf<GeoTile>()

        // Generate all 1°×1° tiles that intersect the viewport
        for (tileLat in minTileLat..maxTileLat) {
            for (tileLon in minTileLon..maxTileLon) {
                // Check if this tile actually intersects the viewport
                val tileWest = tileLon.toDouble()
                val tileEast = (tileLon + 1).toDouble()
                val tileSouth = tileLat.toDouble()
                val tileNorth = (tileLat + 1).toDouble()

                // Check for intersection with viewport
                if (!(tileEast <= west || tileWest >= east || tileNorth <= south || tileSouth >= north)) {
                    tiles.add(GeoTile(
                        latMin = tileLat,
                        latMax = tileLat + 1,
                        lonMin = tileLon,
                        lonMax = tileLon + 1
                    ))
                }
            }
        }

        Logger.log("NETCDF_TILING", LogLevel.DEBUG,
            "Calculated ${tiles.size} tiles for viewport [${west}°, ${south}°, ${east}°, ${north}°]")

        return tiles
    }

    /**
     * Extract data for a specific 1°×1° geographic tile from NetCDF file
     * @param filePath Path to the NetCDF file
     * @param tile The geographic tile to extract
     * @return 2D matrix of float values for the tile, or null if extraction fails
     */
    fun readNetCDFTile(filePath: String, tile: GeoTile): List<List<Float>>? {
        return try {
            Logger.log("NETCDF_TILING", LogLevel.DEBUG, "Extracting tile ${tile.id} from $filePath")

            val fileManager = getGlobalFileManager()
            val fileData = fileManager.readBytes(filePath) ?: return null

            // Parse NetCDF structure to get variable and dimensions
            val variables = readNetCDFFileFromBytes(fileData)
            val dataVariable = variables.find { it.name != "lat" && it.name != "lon" } ?: return null

            // Read dimensions from the file
            val buffer = ByteBufferV2(fileData)
            val dimensions = readDimensions(buffer)

            // Use existing viewport matrix logic with tile bounds
            val matrixResult = readNetCDFViewportMatrix(filePath, dataVariable, dimensions,
                listOf(tile.lonMin.toDouble(), tile.latMin.toDouble(),
                       tile.lonMax.toDouble(), tile.latMax.toDouble()))

            val tileData = matrixResult?.first?.first

            if (tileData.isNullOrEmpty()) {
                Logger.log("NETCDF_TILING", LogLevel.DEBUG, "No data found for tile ${tile.id}")
                return emptyList()
            }

            Logger.log("NETCDF_TILING", LogLevel.DEBUG,
                "Successfully extracted tile ${tile.id}: ${tileData.size}×${tileData.firstOrNull()?.size ?: 0} matrix")

            tileData

        } catch (e: Exception) {
            Logger.log("NETCDF_TILING", LogLevel.ERROR, "Failed to extract tile ${tile.id}: ${e.message}")
            null
        }
    }

    /**
     * Read NetCDF3 file and return variables
     */
    fun readNetCDFFile(filePath: String): List<NetCDFVariable> {
        val fileManager = getGlobalFileManager()
        val fileData = fileManager.readBytes(filePath)
            ?: throw Exception("Could not read file: $filePath")
        return readNetCDFFileFromBytes(fileData)
    }

    // For testing - read directly from byte array
    internal fun readNetCDFFileFromBytes(fileData: ByteArray): List<NetCDFVariable> {
        Logger.log("NETCDF", LogLevel.INFO, "Reading NetCDF data from byte array, size: ${fileData.size}")
        return try {
            val buffer = ByteBufferV2(fileData)

            // Check NetCDF magic
            val magicBytes = buffer.readBytes(4)
            buffer.seek(buffer.position() - 4) // rewind for actual reading
            val magic = buffer.readInt()

            if (magic != 0x43444601) { // "CDF" + version 1
                Logger.log("NETCDF", LogLevel.ERROR, "Not a NetCDF file (invalid magic: got 0x${magic.toString(16)})")
                return emptyList()
            }

            // Skip numrecs for now
            buffer.readInt()

            // Read dimensions
            val dimensions = readDimensions(buffer)

            // Skip global attributes
            readAttributes(buffer)

            // Read variables - this is the main focus
            val variables = try {
                readVariables(buffer, dimensions)
            } catch (e: Throwable) {
                Logger.log("NETCDF", LogLevel.ERROR, "Exception in readVariables: ${e.message}")
                emptyList()
            }

            Logger.log("NETCDF", LogLevel.INFO, "netcdf metadata read")
            variables

        } catch (e: Throwable) {
            Logger.log("NETCDF", LogLevel.ERROR, "Error reading NetCDF data: ${e.message}")
            Logger.log("NETCDF", LogLevel.ERROR, "Stack trace: ${e.stackTraceToString()}")
            emptyList()
        }
    }

    /**
     * Read NetCDF data within viewport bounds (returns lng, lat, value triples)
     */
    fun readNetCDFViewportData(
        filePath: String,
        variable: NetCDFVariable,
        dimensions: List<NetCDFDimension>,
        viewportBounds: List<Double>
    ): List<Triple<Double, Double, Float>> {
        return try {
            val fileManager = getGlobalFileManager()
            val fileData = fileManager.readBytes(filePath) ?: return emptyList()

            // Get dimension information
            val latDim = dimensions.find { it.name == "lat" } ?: return emptyList()
            val lonDim = dimensions.find { it.name == "lon" } ?: return emptyList()

            val latSize = latDim.length
            val lonSize = lonDim.length

            // Determine the actual dimension order from the variable
            val latDimIndex = dimensions.indexOf(latDim)
            val lonDimIndex = dimensions.indexOf(lonDim)

            Logger.log("NETCDF_VIEWPORT", LogLevel.DEBUG, "NetCDF dimensions: lat=$latSize (index $latDimIndex), lon=$lonSize (index $lonDimIndex)")

            // Get the variable's dimension order
            val varLatDimIndex = variable.dimensions.indexOf(latDimIndex)
            val varLonDimIndex = variable.dimensions.indexOf(lonDimIndex)

            Logger.log("NETCDF_VIEWPORT", LogLevel.DEBUG, "Variable dimension order: lat at position $varLatDimIndex, lon at position $varLonDimIndex")

            val west = viewportBounds[0]
            val south = viewportBounds[1]
            val east = viewportBounds[2]
            val north = viewportBounds[3]

            // Parse the NetCDF file to get coordinate variables
            val variables = readNetCDFFileFromBytes(fileData)
            val latVar = variables.find { it.name == "lat" } ?: return emptyList()
            val lonVar = variables.find { it.name == "lon" } ?: return emptyList()

            val latCoords = readVariableData(filePath, latVar, dimensions)?.map { it.toDouble() } ?: return emptyList()
            val lonCoords = readVariableData(filePath, lonVar, dimensions)?.map { it.toDouble() } ?: return emptyList()

            Logger.log("NETCDF_VIEWPORT", LogLevel.DEBUG, "Read ${latCoords.size} lat coords, ${lonCoords.size} lon coords")

            // Find all grid points within viewport bounds
            val dataPoints = mutableListOf<Triple<Double, Double, Float>>()

            for (latIndex in latCoords.indices) {
                val lat = latCoords[latIndex]
                if (lat < south || lat > north) continue

                for (lonIndex in lonCoords.indices) {
                    val lon = lonCoords[lonIndex]
                    if (lon < west || lon > east) continue

                    // Calculate linear index based on actual dimension order
                    fun calculateLinearIndex(latIdx: Int, lonIdx: Int): Int {
                        val indices = mutableListOf<Int>()
                        // Map variable dimensions to actual indices
                        for (dimIndex in variable.dimensions) {
                            if (dimIndex == latDimIndex) {
                                indices.add(latIdx)
                            } else if (dimIndex == lonDimIndex) {
                                indices.add(lonIdx)
                            }
                        }

                        // Calculate linear index using row-major order
                        var linearIndex = 0
                        var stride = 1
                        for (i in indices.indices.reversed()) { // Reverse to start from innermost dimension
                            linearIndex += indices[i] * stride
                            stride *= if (variable.dimensions[i] == latDimIndex) latSize else lonSize
                        }
                        return linearIndex
                    }

                    val linearIndex = calculateLinearIndex(latIndex, lonIndex)

                    // Read the data value at this position
                    val dataValue = readSingleDataValue(fileData, variable, dimensions, linearIndex)

                    // Only include non-missing values
                    if (!dataValue.isNaN()) {
                        dataPoints.add(Triple(lon, lat, dataValue))
                    }
                }
            }

            Logger.log("NETCDF_VIEWPORT", LogLevel.INFO, "Read ${dataPoints.size} data points within viewport")
            dataPoints

        } catch (e: Throwable) {
            Logger.log("NETCDF_VIEWPORT", LogLevel.ERROR, "Error reading NetCDF viewport data: ${e.message}")
            emptyList()
        }
    }

    /**
     * Read NetCDF viewport data as a 2D matrix
     * Returns the data matrix, lat indices, lon indices, and bitmap corners (extended by half pixel intervals)
     */
    fun readNetCDFViewportMatrix(
        filePath: String,
        variable: NetCDFVariable,
        dimensions: List<NetCDFDimension>,
        viewportBounds: List<Double>
    ): Pair<Triple<List<List<Float>>, IntRange, IntRange>, List<Double>>? {
        return try {
            val fileManager = getGlobalFileManager()
            val fileData = fileManager.readBytes(filePath) ?: return null

            // Get dimension information
            val latDim = dimensions.find { it.name == "lat" } ?: return null
            val lonDim = dimensions.find { it.name == "lon" } ?: return null

            val latSize = latDim.length
            val lonSize = lonDim.length

            // Determine the actual dimension order from the variable
            val latDimIndex = dimensions.indexOf(latDim)
            val lonDimIndex = dimensions.indexOf(lonDim)

            Logger.log("NETCDF_MATRIX", LogLevel.DEBUG, "NetCDF dimensions: lat=$latSize (index $latDimIndex), lon=$lonSize (index $lonDimIndex)")

            // Get the variable's dimension order
            val varLatDimIndex = variable.dimensions.indexOf(latDimIndex)
            val varLonDimIndex = variable.dimensions.indexOf(lonDimIndex)

            Logger.log("NETCDF_MATRIX", LogLevel.DEBUG, "Variable dimension order: lat at position $varLatDimIndex, lon at position $varLonDimIndex")

            val west = viewportBounds[0]
            val south = viewportBounds[1]
            val east = viewportBounds[2]
            val north = viewportBounds[3]

            // Parse the NetCDF file to get coordinate variables
            val variables = readNetCDFFileFromBytes(fileData)
            val latVar = variables.find { it.name == "lat" } ?: return null
            val lonVar = variables.find { it.name == "lon" } ?: return null

            val latCoords = readVariableData(filePath, latVar, dimensions)?.map { it.toDouble() } ?: return null
            val lonCoords = readVariableData(filePath, lonVar, dimensions)?.map { it.toDouble() } ?: return null

            Logger.log("SKYSIGHT_BITMAP", LogLevel.DEBUG, "Read ${latCoords.size} lat coords, ${lonCoords.size} lon coords")
            Logger.log("SKYSIGHT_BITMAP", LogLevel.DEBUG, "Viewport bounds: west=$west, south=$south, east=$east, north=$north")

            // Find the lat/lon indices that fall within viewport bounds
            val latIndices = mutableListOf<Int>()
            val lonIndices = mutableListOf<Int>()

            for (latIndex in latCoords.indices) {
                val lat = latCoords[latIndex]
                if (lat >= south && lat <= north) {
                    latIndices.add(latIndex)
                }
            }

            for (lonIndex in lonCoords.indices) {
                val lon = lonCoords[lonIndex]
                if (lon >= west && lon <= east) {
                    lonIndices.add(lonIndex)
                }
            }

            Logger.log("SKYSIGHT_BITMAP", LogLevel.DEBUG, "Found ${latIndices.size} lat indices, ${lonIndices.size} lon indices")

            if (latIndices.isEmpty() || lonIndices.isEmpty()) {
                Logger.log("SKYSIGHT_BITMAP", LogLevel.DEBUG, "No data points found in viewport")
                return null
            }

            // Expand ranges by one row/column on each side, but stay within bounds
            val minLatIndex = maxOf(0, latIndices.min() - 1)
            val maxLatIndex = minOf(latCoords.size - 1, latIndices.max() + 1)
            val minLonIndex = maxOf(0, lonIndices.min() - 1)
            val maxLonIndex = minOf(lonCoords.size - 1, lonIndices.max() + 1)

            val latRange = minLatIndex..maxLatIndex
            val lonRange = minLonIndex..maxLonIndex

            Logger.log("SKYSIGHT_BITMAP", LogLevel.DEBUG, "Extended matrix: lat ${latRange}, lon ${lonRange} (${latRange.count()}x${lonRange.count()})")

            // Log submatrix dimensions with specific tag
            Logger.log("SUBMATRIX_DIMENSIONS", LogLevel.INFO, "Submatrix dimensions: ${latRange.count()} x ${lonRange.count()} (rows: ${latRange.count()}, cols: ${lonRange.count()})")

            // Extract coordinates for the submatrix
            val latCoordsSubset = latCoords.slice(latRange)
            val lonCoordsSubset = lonCoords.slice(lonRange)

            // Calculate intervals (assuming uniform spacing)
            val latInterval = if (latCoordsSubset.size > 1) {
                kotlin.math.abs(latCoordsSubset[1] - latCoordsSubset[0])
            } else 0.0

            val lonInterval = if (lonCoordsSubset.size > 1) {
                kotlin.math.abs(lonCoordsSubset[1] - lonCoordsSubset[0])
            } else 0.0

            Logger.log("SKYSIGHT_BITMAP", LogLevel.DEBUG, "Intervals: lat=$latInterval, lon=$lonInterval")

            // Calculate bitmap corners (extend by half pixel intervals)
            val bitmapMinLon = lonCoordsSubset.minOrNull()?.minus(lonInterval / 2) ?: 0.0
            val bitmapMaxLon = lonCoordsSubset.maxOrNull()?.plus(lonInterval / 2) ?: 0.0
            val bitmapMinLat = latCoordsSubset.minOrNull()?.minus(latInterval / 2) ?: 0.0
            val bitmapMaxLat = latCoordsSubset.maxOrNull()?.plus(latInterval / 2) ?: 0.0

            Logger.log("SKYSIGHT_BITMAP", LogLevel.DEBUG, "Bitmap corners: ($bitmapMinLon, $bitmapMinLat) to ($bitmapMaxLon, $bitmapMaxLat)")

            // Read the data matrix for this subregion
            val dataMatrix = mutableListOf<MutableList<Float>>()

            // Calculate linear index based on actual dimension order
            fun calculateLinearIndex(latIndex: Int, lonIndex: Int): Int {
                val indices = mutableListOf<Int>()
                // Map variable dimensions to actual indices
                for (dimIndex in variable.dimensions) {
                    if (dimIndex == latDimIndex) {
                        indices.add(latIndex)
                    } else if (dimIndex == lonDimIndex) {
                        indices.add(lonIndex)
                    }
                }

                // Calculate linear index using row-major order
                var linearIndex = 0
                var stride = 1
                for (i in indices.indices.reversed()) { // Reverse to start from innermost dimension
                    linearIndex += indices[i] * stride
                    stride *= if (variable.dimensions[i] == latDimIndex) latSize else lonSize
                }
                return linearIndex
            }

            for (latIndex in latRange) {
                val row = mutableListOf<Float>()
                for (lonIndex in lonRange) {
                    val linearIndex = calculateLinearIndex(latIndex, lonIndex)
                    val dataValue = readSingleDataValue(fileData, variable, dimensions, linearIndex)
                    row.add(dataValue)
                }
                dataMatrix.add(row)
            }

            // Reverse the dataMatrix rows to correct latitude orientation
            // NetCDF often stores latitudes from north to south, but maps need north at top
            dataMatrix.reverse()

            Pair(
                Triple(dataMatrix, latRange, lonRange),
                listOf(bitmapMinLon, bitmapMaxLon, bitmapMinLat, bitmapMaxLat)
            )
        } catch (e: Throwable) {
            Logger.log("SKYSIGHT_BITMAP", LogLevel.ERROR, "Error reading NetCDF viewport matrix: ${e.message}")
            null
        }
    }

    /**
     * Read a single data value from NetCDF file data at specific index
     */
    internal fun readSingleDataValue(
        fileData: ByteArray,
        variable: NetCDFVariable,
        dimensions: List<NetCDFDimension>,
        linearIndex: Int
    ): Float {
        return try {
            val buffer = ByteBufferV2(fileData)

            // Seek to the variable's data offset plus the index offset
            val dataTypeSize = when (variable.dataType) {
                1 -> 1 // int8
                2 -> 1 // char
                3 -> 2 // int16
                4 -> 4 // int32
                5 -> 4 // float32
                6 -> 8 // float64
                else -> 1
            }

            val valueOffset = variable.offset + (linearIndex * dataTypeSize)
            buffer.seek(valueOffset)

            // Read the raw value
            val rawValue = when (variable.dataType) {
                1 -> buffer.readByte().toInt()
                3 -> buffer.readShort().toInt()
                4 -> buffer.readInt()
                5 -> buffer.readFloat()
                6 -> buffer.readDouble()
                else -> 0
            }

            // Apply scaling
            val scaleFactor = variable.attributes["scale_factor"] as? Number
            val addOffset = variable.attributes["add_offset"] as? Number
            val missingValue = variable.attributes["missing_value"] as? Number

            // Check for missing value
            if (missingValue != null && rawValue.toFloat() == missingValue.toFloat()) {
                return Float.NaN
            }

            // Apply scaling if available
            val scaledValue = if (scaleFactor != null && addOffset != null) {
                rawValue.toFloat() * scaleFactor.toFloat() + addOffset.toFloat()
            } else {
                rawValue.toFloat()
            }

            scaledValue

        } catch (e: Throwable) {
            Logger.log("NETCDF_VIEWPORT", LogLevel.ERROR, "Error reading single data value: ${e.message}")
            Float.NaN
        }
    }

    /**
     * Read variable data from NetCDF file for coordinate calculation
     */
    private fun getDataTypeSize(dataType: Int): Int {
        return when (dataType) {
            1 -> 1  // int8
            3 -> 2  // int16
            4 -> 4  // int32
            5 -> 4  // float32
            6 -> 8  // float64
            else -> 4  // default to 4 bytes
        }
    }

    internal fun readVariableDataSelective(filePath: String, variable: NetCDFVariable, dimensions: List<NetCDFDimension>, latIndices: List<Int>, lonIndices: List<Int>, fullLatSize: Int, fullLonSize: Int): List<List<Float>>? {
        return try {
            val fileManager = getGlobalFileManager()
            val fileData = fileManager.readBytes(filePath)
                ?: throw Exception("Could not read file: $filePath")

            val buffer = ByteBufferV2(fileData)
            buffer.seek(variable.offset)

            // Get scaling attributes
            val scaleFactor = variable.attributes["scale_factor"] as? Number
            val addOffset = variable.attributes["add_offset"] as? Number
            val missingValue = variable.attributes["missing_value"] as? Number

            // Create result matrix for the subset
            val resultMatrix = mutableListOf<MutableList<Float>>()

            for (latIdx in latIndices) {
                val row = mutableListOf<Float>()
                for (lonIdx in lonIndices) {
                    // Calculate the flat index: lat * lonSize + lon
                    val flatIndex = latIdx * fullLonSize + lonIdx

                    // Seek to the specific element
                    buffer.seek(variable.offset + flatIndex * getDataTypeSize(variable.dataType))

                    val rawValue = when (variable.dataType) {
                        1 -> buffer.readByte().toInt() // int8
                        3 -> buffer.readShort().toInt() // int16
                        4 -> buffer.readInt() // int32
                        5 -> buffer.readFloat() // float32
                        6 -> buffer.readDouble() // float64
                        else -> 0
                    }

                    // Apply scaling
                    val scaledValue = if (scaleFactor != null && addOffset != null) {
                        // Check for missing values
                        if (missingValue != null && rawValue.toFloat() == missingValue.toFloat()) {
                            Float.NaN
                        } else {
                            rawValue.toFloat() * scaleFactor.toFloat() + addOffset.toFloat()
                        }
                    } else {
                        rawValue.toFloat()
                    }

                    // Format to 2 decimal places before returning
                    val formattedValue = if (scaledValue.isNaN()) {
                        Float.NaN
                    } else {
                        (kotlin.math.round(scaledValue * 100) / 100).toFloat()
                    }

                    row.add(formattedValue)
                }
                resultMatrix.add(row)
            }

            Logger.log("NETCDF_EXTENT", LogLevel.DEBUG, "Successfully read selective ${latIndices.size}x${lonIndices.size} matrix for ${variable.name}")
            resultMatrix
        } catch (e: Throwable) {
            Logger.log("NETCDF_EXTENT", LogLevel.ERROR, "Error reading selective variable data for ${variable.name}: ${e.message}")
            null
        }
    }

    internal fun readVariableData(filePath: String, variable: NetCDFVariable, dimensions: List<NetCDFDimension>): FloatArray? {
        val fileManager = getGlobalFileManager()
        val fileData = fileManager.readBytes(filePath)
            ?: return null

        val buffer = ByteBufferV2(fileData)
        buffer.seek(variable.offset)

        // Calculate total elements
        val totalElements = variable.dimensions.fold(1) { acc, dimIndex ->
            acc * dimensions[dimIndex].length
        }

        return try {

            // Get scaling attributes
            val scaleFactor = variable.attributes["scale_factor"] as? Number
            val addOffset = variable.attributes["add_offset"] as? Number
            val missingValue = variable.attributes["missing_value"] as? Number

            val data = FloatArray(totalElements)
            for (i in 0 until totalElements) {
                val rawValue = when (variable.dataType) {
                    1 -> buffer.readByte().toInt() // int8
                    3 -> buffer.readShort().toInt() // int16
                    4 -> buffer.readInt() // int32
                    5 -> buffer.readFloat() // float32
                    6 -> buffer.readDouble() // float64
                    else -> 0
                }

                // Apply scaling
                data[i] = if (scaleFactor != null && addOffset != null) {
                    // Check for missing values
                    if (missingValue != null && rawValue.toFloat() == missingValue.toFloat()) {
                        Float.NaN
                    } else {
                        rawValue.toFloat() * scaleFactor.toFloat() + addOffset.toFloat()
                    }
                } else {
                    rawValue.toFloat()
                }
            }

            Logger.log("NETCDF_EXTENT", LogLevel.DEBUG, "Successfully read ${data.size} scaled float values for ${variable.name}")
            data
        } catch (e: Throwable) {
            Logger.log("NETCDF_EXTENT", LogLevel.ERROR, "Error reading variable data for ${variable.name}: ${e.message}")
            null
        }
    }


    /**
     * Read dimensions from NetCDF file
     */
    fun readNetCDFDimensions(filePath: String): List<NetCDFDimension> {
        val fileManager = getGlobalFileManager()
        val fileData = fileManager.readBytes(filePath)
            ?: throw Exception("Could not read file: $filePath")
        val buffer = ByteBufferV2(fileData)

        // Skip NetCDF header like V1 does
        val magic = buffer.readInt()
        if (magic != 0x43444601) { // "CDF" + version 1
            Logger.log("NETCDF", LogLevel.ERROR, "Not a NetCDF file (invalid magic: got 0x${magic.toString(16)})")
            return emptyList()
        }
        buffer.readInt() // Skip numrecs

        return readDimensions(buffer)
    }

    /**
     * Read dimensions section - copied from netchdf N3builder.readDimensions
     */
    internal fun readDimensions(buffer: ByteBufferV2): List<NetCDFDimension> {
        val startPos = buffer.position()

        val magic = buffer.readInt()

        val numDims = if (magic == 0) {
            // Check if this is ABSENT (ZERO ZERO) or invalid
            val secondZero = buffer.readInt()
            if (secondZero == 0) {
                // ABSENT = ZERO ZERO - no dimensions
                0
            } else {
                // Invalid - magic=0 but second value != 0
                Logger.log("NETCDF", LogLevel.ERROR, "Invalid dimension section: magic=0 but second value=$secondZero")
                buffer.seek(startPos) // rewind
                return emptyList()
            }
        } else if (magic == MAGIC_DIM) {
            buffer.readInt() // Read actual count
        } else {
            Logger.log("NETCDF", LogLevel.ERROR, "Invalid dimension magic: $magic")
            buffer.seek(startPos) // rewind
            return emptyList()
        }

        val dimensions = mutableListOf<NetCDFDimension>()
        repeat(numDims) {
            val name = readString(buffer)
            val length = buffer.readInt()
            if (name != null) {
                dimensions.add(NetCDFDimension(name, length))
            }
        }

        return dimensions
    }

    /**
     * Read variables section - copied from netchdf N3builder.readVariables
     */
    internal fun readVariables(buffer: ByteBufferV2, dimensions: List<NetCDFDimension>): List<NetCDFVariable> {
        val startPos = buffer.position()

        val magic = buffer.readInt()

        // Check if we have more data to read
        val remainingBytes = buffer.remainingBytes()

        if (remainingBytes <= 0) {
            return emptyList()
        }

        val numVars = if (magic == 0) {
            // Check if this is ABSENT (ZERO ZERO) or invalid
            val secondZero = buffer.readInt()
            if (secondZero == 0) {
                // ABSENT = ZERO ZERO - no variables
                0
            } else {
                // Invalid - magic=0 but second value != 0
                Logger.log("NETCDF", LogLevel.ERROR, "Invalid variable section: magic=0 but second value=$secondZero")
                buffer.seek(startPos) // rewind
                return emptyList()
            }
        } else if (magic == MAGIC_VAR) {
            buffer.readInt() // Read actual count
        } else {
            Logger.log("NETCDF", LogLevel.ERROR, "Invalid variable magic: $magic")
            buffer.seek(startPos) // rewind
            return emptyList()
        }
        val variables = mutableListOf<NetCDFVariable>()
        repeat(numVars) {
            val name = readString(buffer)

            if (name != null) {
                // Read dimension count
                val rank = buffer.readInt()

                val dimensionIndices = mutableListOf<Int>()

                // Read dimension indices
                repeat(rank) {
                    val dimIndex = buffer.readInt()
                    // Validate that dimIndex is valid
                    if (dimIndex < 0 || dimIndex >= dimensions.size) {
                        Logger.log("NETCDF", LogLevel.WARN, "Invalid dimension index $dimIndex, only ${dimensions.size} dimensions available")
                    }
                    dimensionIndices.add(dimIndex)
                }

                // Read variable attributes
                val attributes = readAttributes(buffer)

                // Read data type
                val dataType = buffer.readInt()

                // Read vsize and offset
                val vsize = buffer.readInt()
                val offset = buffer.readInt()

                variables.add(NetCDFVariable(
                    name = name,
                    dataType = dataType,
                    dimensions = dimensionIndices,
                    attributes = attributes,
                    offset = offset,
                    vsize = vsize
                ))
            }
        }

        return variables
    }

    /**
     * Read attributes section - copied from netchdf N3builder.readAttributes
     */
    internal fun readAttributes(buffer: ByteBufferV2): Map<String, Any> {
        val attributes = mutableMapOf<String, Any>()
        val startPos = buffer.position()

        val magic = buffer.readInt()

        val numAtts = if (magic == 0) {
            // Check if this is ABSENT (ZERO ZERO) or invalid
            val secondZero = buffer.readInt()
            if (secondZero == 0) {
                // ABSENT = ZERO ZERO - no attributes
                0
            } else {
                // Invalid - magic=0 but second value != 0
                Logger.log("NETCDF", LogLevel.ERROR, "Invalid attribute section: magic=0 but second value=$secondZero")
                buffer.seek(startPos) // rewind
                return attributes
            }
        } else if (magic == MAGIC_ATT) {
            buffer.readInt() // Read actual count
        } else {
            Logger.log("NETCDF", LogLevel.ERROR, "Invalid attribute magic: $magic")
            buffer.seek(startPos) // rewind
            return attributes
        }

        repeat(numAtts) {
            val name = readString(buffer)

            if (name == null) {
                // If name is null, we still need to read type and skip the data
                val type = buffer.readInt()

                if (type == 2) { // CHAR - string attribute
                    readString(buffer) // skip the string value
                } else {
                    val count = buffer.readInt()
                    skipAttributeData(type, count, buffer)
                }
                return@repeat // Continue to next attribute
            }

            val type = buffer.readInt() // type

            if (type == 2) { // CHAR - string attribute
                val value = readString(buffer)
                if (value != null) {
                    attributes[name] = value
                }
            } else {
                val count = buffer.readInt() // count

                if (count == 1) { // Only handle single-value attributes for now
                    val value: Any? = when (type) {
                        1 -> { // NC_BYTE (1 byte + 3 padding)
                            val byteVal = buffer.readByte()
                            // Skip 3 padding bytes
                            repeat(3) { buffer.readByte() }
                            byteVal
                        }
                        2 -> readString(buffer) // NC_CHAR (string)
                        3 -> { // NC_SHORT (2 bytes + 2 padding)
                            val shortVal = buffer.readShort()
                            // Skip 2 padding bytes
                            repeat(2) { buffer.readByte() }
                            shortVal
                        }
                        4 -> buffer.readInt() // NC_INT (4 bytes, already aligned)
                        5 -> buffer.readFloat() // NC_FLOAT (4 bytes, already aligned)
                        6 -> buffer.readDouble() // NC_DOUBLE (8 bytes)
                        2 -> readString(buffer) // NC_CHAR (string)
                        3 -> { // NC_SHORT (2 bytes + 2 padding)
                            val shortVal = buffer.readShort()
                            // Skip 2 padding bytes
                            repeat(2) { buffer.readByte() }
                            shortVal
                        }
                        4 -> buffer.readInt() // NC_INT (4 bytes, already aligned)
                        5 -> buffer.readFloat() // NC_FLOAT (4 bytes, already aligned)
                        6 -> buffer.readDouble() // NC_DOUBLE (8 bytes)
                        2 -> readString(buffer) // NC_CHAR (string)
                        3 -> { // NC_SHORT (2 bytes + 2 padding)
                            val shortVal = buffer.readShort()
                            // Skip 2 padding bytes
                            repeat(2) { buffer.readByte() }
                            shortVal
                        }
                        4 -> buffer.readInt() // NC_INT (4 bytes, already aligned)
                        5 -> buffer.readFloat() // NC_FLOAT (4 bytes, already aligned)
                        6 -> buffer.readDouble() // NC_DOUBLE (8 bytes)
                        2 -> readString(buffer) // NC_CHAR (string)
                        3 -> { // NC_SHORT (2 bytes + 2 padding)
                            val shortVal = buffer.readShort()
                            // Skip 2 padding bytes
                            repeat(2) { buffer.readByte() }
                            shortVal
                        }
                        4 -> buffer.readInt() // NC_INT (4 bytes, already aligned)
                        5 -> buffer.readFloat() // NC_FLOAT (4 bytes, already aligned)
                        6 -> buffer.readDouble() // NC_DOUBLE (8 bytes)
                        else -> {
                            // Skip unknown types
                            skipAttributeData(type, count, buffer)
                            null
                        }
                    }

                    if (value != null) {
                        attributes[name] = value
                    }
                } else {
                    // Skip multi-value attributes
                    skipAttributeData(type, count, buffer)
                }
            }
        }

        return attributes
    }

    private fun skipAttributeData(type: Int, count: Int, buffer: ByteBufferV2) {
        val dataBytes = when (type) {
            1 -> count // byte
            2 -> count // char
            3 -> count * 2 // short
            4 -> count * 4 // int
            5 -> count * 4 // float
            6 -> count * 8 // double
            else -> count * 4 // unknown, assume 4 bytes
        }

        // Skip the data bytes
        repeat(dataBytes) { buffer.readByte() }

        // Skip to 4-byte boundary
        val paddingBytes = padding(dataBytes)
        repeat(paddingBytes) { buffer.readByte() }
    }

    /**
     * Read string from NetCDF format - copied from netchdf N3builder.readString
     */
    private fun readString(buffer: ByteBufferV2): String? {
        val nelems = buffer.readInt()

        if (nelems == 0) {
            return null
        }

        if (nelems < 0 || nelems > 1000) {
            Logger.log("NETCDF", LogLevel.ERROR, "Invalid string length: $nelems at position ${buffer.position()}")
            return null
        }

        // Add bounds checking
        if (buffer.position() + nelems > 1147952) { // Use actual file size for now
            Logger.log("NETCDF", LogLevel.ERROR, "String length $nelems would exceed buffer size at position ${buffer.position()}")
            return null
        }

        val bytes = ByteArray(nelems)
        repeat(nelems) { bytes[it] = buffer.readByte() }

        // Skip padding to 4-byte boundary
        val padding = padding(nelems)
        repeat(padding) { buffer.readByte() }

        return String(bytes, Charsets.UTF_8).trim('\u0000')
    }

    /**
     * Calculate padding to 4-byte boundary - copied from netchdf
     */
    private fun padding(nbytes: Int): Int {
        var pad = nbytes % 4
        if (pad != 0) pad = 4 - pad
        return pad
    }
}

/**
 * Simple ByteBuffer for reading binary data from byte arrays - V2
 */
internal class ByteBufferV2(private val data: ByteArray) {
    private var position = 0

    fun readByte(): Byte {
        if (position >= data.size) {
            throw IndexOutOfBoundsException("Cannot read byte at position $position (buffer size: ${data.size})")
        }
        return data[position++]
    }

    fun readShort(): Short {
        if (position + 2 > data.size) {
            throw IndexOutOfBoundsException("Cannot read 2 bytes at position $position (buffer size: ${data.size})")
        }
        val value = ((data[position].toInt() and 0xFF) shl 8) or
                   (data[position + 1].toInt() and 0xFF)
        position += 2
        return value.toShort()
    }

    fun readInt(): Int {
        if (position + 4 > data.size) {
            throw IndexOutOfBoundsException("Cannot read 4 bytes at position $position (buffer size: ${data.size})")
        }
        val value = ((data[position].toInt() and 0xFF) shl 24) or
                   ((data[position + 1].toInt() and 0xFF) shl 16) or
                   ((data[position + 2].toInt() and 0xFF) shl 8) or
                   (data[position + 3].toInt() and 0xFF)
        position += 4
        return value
    }

    fun readFloat(): Float {
        val intValue = readInt()
        return Float.fromBits(intValue)
    }

    fun readDouble(): Double {
        val high = readInt().toLong() and 0xFFFFFFFF
        val low = readInt().toLong() and 0xFFFFFFFF
        val longValue = (high shl 32) or low
        return Double.fromBits(longValue)
    }

    fun readBytes(count: Int): ByteArray {
        val result = data.copyOfRange(position, position + count)
        position += count
        return result
    }

    fun position() = position
    fun seek(pos: Int) { position = pos }
    fun remainingBytes() = data.size - position
}