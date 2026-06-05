package org.mountaincircles.app.modules.wave.logic

/**
 * GeoTIFF metadata structure
 */
data class GeoTiffMetadata(
    val width: Int,
    val height: Int,
    val pixelScaleLng: Double,
    val pixelScaleLat: Double,
    val originLng: Double,
    val originLat: Double,
    val stripOffsets: LongArray
)

/**
 * Platform-specific GeoTIFF pixel reading utilities
 */
expect object GeoTiffPixelReader {
    suspend fun readPixels(filePath: String, dataOffset: Long, pixelCount: Int, width: Int): DoubleArray
    suspend fun readPixelsWithStripOffsets(filePath: String, stripOffsets: LongArray, pixelIndices: List<Int>, width: Int): DoubleArray
    fun readGeoTiffMetadata(filePath: String): GeoTiffMetadata
}
