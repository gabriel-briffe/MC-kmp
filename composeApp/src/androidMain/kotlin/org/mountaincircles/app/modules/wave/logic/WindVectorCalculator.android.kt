package org.mountaincircles.app.modules.wave.logic

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder


/**
 * Android-specific implementation of GeoTIFF pixel reading
 */
actual object GeoTiffPixelReader {

    actual suspend fun readPixels(filePath: String, dataOffset: Long, pixelCount: Int, width: Int): DoubleArray {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "GeoTIFF file does not exist: $filePath")
                return doubleArrayOf()
            }

            // Read pixel data from GeoTIFF
            // From tiffdump analysis:
            // - 717 strips of 8968 bytes each (1121 pixels * 8 bytes)
            // - Rows/Strip: 1 (each strip contains one row)
            // - Strips are NOT contiguous - each has its own offset

            val data = DoubleArray(pixelCount)
            val bytesPerPixel = 8
            val buffer = ByteArray(bytesPerPixel)
            val pixelsPerRow = width // Use actual width from TIFF metadata

            // Use the provided data offset
            Logger.log("TIFF_METADATA", LogLevel.DEBUG, "Using provided data offset: $dataOffset")
            val stripOffsets = longArrayOf(dataOffset)

            Logger.log("TIFF_METADATA", LogLevel.DEBUG, "Using ${stripOffsets.size} strip offsets for reading $pixelCount pixels")

            RandomAccessFile(file, "r").use { raf ->
                // Read pixels individually by seeking to correct positions
                for (pixelIndex in 0 until pixelCount) {
                    // Calculate which strip and position within strip this pixel belongs to
                    val stripIndex = pixelIndex / pixelsPerRow
                    val pixelIndexWithinStrip = pixelIndex % pixelsPerRow

                    if (stripIndex >= stripOffsets.size) {
                        Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "Pixel $pixelIndex requires strip $stripIndex but only ${stripOffsets.size} strips available")
                        return doubleArrayOf()
                    }

                    // Calculate the absolute file offset for this pixel
                    val stripOffset = stripOffsets[stripIndex]
                    val pixelOffsetWithinStrip = pixelIndexWithinStrip * bytesPerPixel
                    val absolutePixelOffset = stripOffset + pixelOffsetWithinStrip

                    // Seek to the exact pixel position
                    raf.seek(absolutePixelOffset)

                    // Read the 8-byte double value
                    if (raf.read(buffer) != bytesPerPixel) {
                        Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "Failed to read pixel data at index $pixelIndex (strip $stripIndex, offset $absolutePixelOffset)")
                        return doubleArrayOf()
                    }

                    // Convert 8 bytes to double (little-endian)
                    val value = ByteBuffer.wrap(buffer)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .getDouble(0)

                    data[pixelIndex] = value
                }
            }

            Logger.log("WIND_CALCULATOR", LogLevel.DEBUG, "Successfully read ${data.size} pixels from GeoTIFF")
            return data

    } catch (e: Exception) {
        Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "Failed to read GeoTIFF pixels: ${e.message}", e)
        return doubleArrayOf()
    }

}

    actual suspend fun readPixelsWithStripOffsets(filePath: String, stripOffsets: LongArray, pixelIndices: List<Int>, width: Int): DoubleArray {
        try {
            if (pixelIndices.isEmpty()) {
                return DoubleArray(0)
            }

            val result = DoubleArray(pixelIndices.size)
            val bytesPerPixel = 8
            val buffer = ByteArray(bytesPerPixel)
            val pixelsPerRow = width // Use actual width from TIFF metadata

            RandomAccessFile(File(filePath), "r").use { raf ->
                // Read pixels individually by seeking to correct positions using cached strip offsets
                pixelIndices.forEachIndexed { index, pixelIndex ->
                    // Calculate which strip and position within strip this pixel belongs to
                    val stripIndex = pixelIndex / pixelsPerRow
                    val pixelIndexWithinStrip = pixelIndex % pixelsPerRow

                    if (stripIndex >= stripOffsets.size) {
                        Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "Pixel $pixelIndex requires strip $stripIndex but only ${stripOffsets.size} strips available")
                        result[index] = 0.0
                        return@forEachIndexed
                    }

                    // Calculate the absolute file offset for this pixel using cached strip offsets
                    val stripOffset = stripOffsets[stripIndex]
                    val pixelOffsetWithinStrip = pixelIndexWithinStrip * bytesPerPixel
                    val absolutePixelOffset = stripOffset + pixelOffsetWithinStrip

                    // Seek to the exact pixel position
                    raf.seek(absolutePixelOffset)

                    // Read the 8-byte double value
                    if (raf.read(buffer) != bytesPerPixel) {
                        Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "Failed to read pixel data at index $pixelIndex (strip $stripIndex, offset $absolutePixelOffset)")
                        result[index] = 0.0
                        return@forEachIndexed
                    }

                    // Convert 8 bytes to double (little-endian)
                    val value = ByteBuffer.wrap(buffer)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .getDouble(0)

                    result[index] = value
                }
            }

            Logger.log("WIND_CALCULATOR", LogLevel.DEBUG, "Successfully read ${result.size} pixels using cached strip offsets")
            return result

        } catch (e: Exception) {
            Logger.log("WIND_CALCULATOR", LogLevel.ERROR, "Failed to read pixels with cached offsets: ${e.message}", e)
            return DoubleArray(pixelIndices.size)
        }
    }

    actual fun readGeoTiffMetadata(filePath: String): GeoTiffMetadata {
        val file = File(filePath)
        try {
            Logger.log("TIFF_METADATA", LogLevel.DEBUG, "Parsing complete GeoTIFF metadata: ${file.name}")
            RandomAccessFile(file, "r").use { raf ->
                // Read TIFF header (8 bytes)
                val header = ByteArray(8)
                raf.readFully(header)

                // Check byte order (first 2 bytes)
                val byteOrder = if (header[0] == 'I'.code.toByte() && header[1] == 'I'.code.toByte()) {
                    ByteOrder.LITTLE_ENDIAN
                } else if (header[0] == 'M'.code.toByte() && header[1] == 'M'.code.toByte()) {
                    ByteOrder.BIG_ENDIAN
                } else {
                    Logger.log("TIFF_METADATA", LogLevel.ERROR, "Invalid TIFF byte order in $filePath")
                    throw IllegalStateException("Invalid TIFF byte order")
                }

                // Check TIFF version (bytes 2-3 must be 42)
                val version = ByteBuffer.wrap(header, 2, 2)
                    .order(byteOrder)
                    .getShort()
                    .toInt() and 0xFFFF // Unsigned
                if (version != 42) {
                    Logger.log("TIFF_METADATA", LogLevel.ERROR, "Invalid TIFF version: $version (expected 42)")
                    throw IllegalStateException("Not a classic TIFF file (version $version)")
                }

                // Read IFD offset (bytes 4-7) - unsigned
                val ifdOffset = ByteBuffer.wrap(header, 4, 4)
                    .order(byteOrder)
                    .getInt()
                    .toLong() and 0xFFFFFFFFL // Unsigned

                // Initialize metadata variables
                var width = 0L
                var height = 0L
                var pixelScaleLng = 0.0
                var pixelScaleLat = 0.0
                var originLng = 0.0
                var originLat = 0.0
                val stripOffsets = mutableListOf<Long>()

                // Temporary storage for tiepoint data (processed after all tags read)
                var tiepointData: DoubleArray? = null

                // Seek to IFD and read it
                raf.seek(ifdOffset)

                // Read number of directory entries (2 bytes) - unsigned
                val numEntriesBytes = ByteArray(2)
                raf.readFully(numEntriesBytes)
                val numEntries = ByteBuffer.wrap(numEntriesBytes)
                    .order(byteOrder)
                    .getShort()
                    .toInt() and 0xFFFF // Unsigned

                Logger.log("TIFF_METADATA", LogLevel.DEBUG, "IFD has $numEntries entries")

                // Scan through directory entries
                for (i in 0 until numEntries) {
                    val entryBytes = ByteArray(12) // Each IFD entry is 12 bytes
                    raf.readFully(entryBytes)

                    // Read all fields as unsigned
                    val tag = ByteBuffer.wrap(entryBytes, 0, 2)
                        .order(byteOrder)
                        .getShort()
                        .toInt() and 0xFFFF // Unsigned

                    val type = ByteBuffer.wrap(entryBytes, 2, 2)
                        .order(byteOrder)
                        .getShort()
                        .toInt() and 0xFFFF // Unsigned

                    val count = ByteBuffer.wrap(entryBytes, 4, 4)
                        .order(byteOrder)
                        .getInt()
                        .toLong() and 0xFFFFFFFFL // Unsigned

                    val valueOffset = ByteBuffer.wrap(entryBytes, 8, 4)
                        .order(byteOrder)
                        .getInt()
                        .toLong() and 0xFFFFFFFFL // Unsigned

                    // DEBUG: Log all tags found
                    Logger.log("TIFF_METADATA", LogLevel.DEBUG, "Found tag $tag (type=$type, count=$count, offset=$valueOffset)")

                    when (tag) {
                        256 -> { // ImageWidth
                            if (type == 3 && count == 1L) { // SHORT
                                width = valueOffset // Inline value
                            } else if (type == 4 && count == 1L) { // LONG
                                if (count * 4 <= 4) { // Inline
                                    width = valueOffset
                                } else { // Offset
                                    val currentPos = raf.filePointer
                                    raf.seek(valueOffset)
                                    val widthBytes = ByteArray(4)
                                    raf.readFully(widthBytes)
                                    width = ByteBuffer.wrap(widthBytes).order(byteOrder).getInt().toLong() and 0xFFFFFFFFL
                                    raf.seek(currentPos) // Restore position
                                }
                            }
                            Logger.log("TIFF_METADATA", LogLevel.DEBUG, "Found ImageWidth: $width")
                        }
                        257 -> { // ImageLength/Height
                            if (type == 3 && count == 1L) { // SHORT
                                height = valueOffset // Inline value
                            } else if (type == 4 && count == 1L) { // LONG
                                if (count * 4 <= 4) { // Inline
                                    height = valueOffset
                                } else { // Offset
                                    val currentPos = raf.filePointer
                                    raf.seek(valueOffset)
                                    val heightBytes = ByteArray(4)
                                    raf.readFully(heightBytes)
                                    height = ByteBuffer.wrap(heightBytes).order(byteOrder).getInt().toLong() and 0xFFFFFFFFL
                                    raf.seek(currentPos) // Restore position
                                }
                            }
                            Logger.log("TIFF_METADATA", LogLevel.DEBUG, "Found ImageHeight: $height")
                        }
                        273 -> { // StripOffsets
                            if (count == 1L) {
                                stripOffsets.add(valueOffset)
                            } else {
                                val currentPos = raf.filePointer
                                raf.seek(valueOffset)
                                for (j in 0 until count) {
                                    val offsetBytes = ByteArray(4)
                                    raf.readFully(offsetBytes)
                                    val offset = ByteBuffer.wrap(offsetBytes).order(byteOrder).getInt().toLong() and 0xFFFFFFFFL
                                    stripOffsets.add(offset)
                                }
                                raf.seek(currentPos) // Restore position
                            }
                            Logger.log("TIFF_METADATA", LogLevel.DEBUG, "Found ${stripOffsets.size} StripOffsets")
                        }
                        33550 -> { // ModelPixelScale (GeoTIFF pixel scale)
                            if (type == 12 && count == 3L) { // DOUBLE array
                                val currentPos = raf.filePointer
                                raf.seek(valueOffset)
                                val scaleBytes = ByteArray(24) // 3 doubles = 24 bytes
                                raf.readFully(scaleBytes)
                                val buffer = ByteBuffer.wrap(scaleBytes).order(byteOrder)
                                pixelScaleLng = buffer.getDouble() // ScaleX
                                pixelScaleLat = buffer.getDouble() // ScaleY
                                // Skip ScaleZ
                                raf.seek(currentPos) // Restore position
                                Logger.log("TIFF_METADATA", LogLevel.DEBUG, "Found ModelPixelScale: $pixelScaleLng, $pixelScaleLat")
                            }
                        }
                        33922 -> { // ModelTiepoint (GeoTIFF origin)
                            if (type == 12 && count >= 6L) { // DOUBLE array, at least 6 values for 3D tiepoint
                                val currentPos = raf.filePointer
                                raf.seek(valueOffset)
                                val tiepointBytes = ByteArray((count * 8).toInt()) // 8 bytes per double
                                raf.readFully(tiepointBytes)
                                val buffer = ByteBuffer.wrap(tiepointBytes).order(byteOrder)
                                // Store raw tiepoint data for processing after all tags are read
                                tiepointData = DoubleArray(count.toInt()) { buffer.getDouble() }
                                raf.seek(currentPos) // Restore position

                                Logger.log("TIFF_METADATA", LogLevel.DEBUG, "Found ModelTiepoint with ${count} values (raw data stored for post-processing)")
                            }
                        }
                    }
                }

                // Process tiepoint data now that all tags are read and scales are available
                if (tiepointData != null && tiepointData!!.size >= 6) {
                    val I = tiepointData!![0] // Raster X coordinate (pixel column)
                    val J = tiepointData!![1] // Raster Y coordinate (pixel row)
                    val K = tiepointData!![2] // Raster Z coordinate (usually 0)
                    val X = tiepointData!![3] // Model X coordinate (longitude)
                    val Y = tiepointData!![4] // Model Y coordinate (latitude)
                    // tiepointData[5] is Z (can be skipped)

                    // Adjust origin to represent actual raster corner (0,0), not tiepoint location
                    // GeoTIFF formula: Origin = Tiepoint - (I,J,K) * Scale
                    originLng = X - I * pixelScaleLng
                    originLat = Y - J * pixelScaleLat

                    Logger.log("TIFF_METADATA", LogLevel.DEBUG, "Processed ModelTiepoint: I=$I, J=$J, X=$X, Y=$Y -> adjusted origin: $originLng, $originLat")
                }

                // Validate that we found all required metadata
                if (width == 0L || height == 0L) {
                    throw IllegalStateException("Missing required TIFF metadata: width=$width, height=$height")
                }
                if (stripOffsets.isEmpty()) {
                    throw IllegalStateException("No strip offsets found in TIFF file")
                }

                Logger.log("TIFF_METADATA", LogLevel.DEBUG, "Successfully parsed GeoTIFF metadata: ${width}x${height}, strips=${stripOffsets.size}")
                Logger.log("TIFF_METADATA", LogLevel.DEBUG, "Geospatial metadata - pixelScale: ($pixelScaleLng, $pixelScaleLat), origin: ($originLng, $originLat)")

                return GeoTiffMetadata(
                    width = width.toInt(),
                    height = height.toInt(),
                    pixelScaleLng = pixelScaleLng,
                    pixelScaleLat = pixelScaleLat,
                    originLng = originLng,
                    originLat = originLat,
                    stripOffsets = stripOffsets.toLongArray()
                )
            }
        } catch (e: Exception) {
            Logger.log("TIFF_METADATA", LogLevel.ERROR, "Failed to parse GeoTIFF metadata from $filePath: ${e.message}")
            throw IllegalStateException("Failed to parse GeoTIFF metadata: ${e.message}", e)
        }
    }
}
