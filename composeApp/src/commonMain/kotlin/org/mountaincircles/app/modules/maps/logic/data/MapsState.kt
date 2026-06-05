package org.mountaincircles.app.modules.maps.logic.data

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import org.mountaincircles.app.modules.ModuleState

/**
 * Maps module state extending ModuleState
 *
 * This represents the complete state of the maps module,
 * including map availability, download progress, and installed maps.
 */
@Serializable
data class MapsState(
    // Module state properties (from ModuleState interface)
    override val isInitialized: Boolean = false,
    override val hasError: Boolean = false,
    override val errorMessage: String? = null,
    override val hasDataToRender: Boolean? = null,

    // Maps-specific state
    val installedMaps: List<String> = emptyList(), // List of installed map IDs
    val isDownloading: Boolean = false,
    @Serializable(with = DownloadProgressSerializer::class)
    val downloadProgress: DownloadProgress? = null,
    val availableMaps: List<MapSource> = MapSources.availableMaps,
    // NEW: Download UI state management
    val activeDownloadMapId: String? = null,  // Which map is currently active
    val isDownloadActive: Boolean = false     // Whether any download is active
) : ModuleState() {

}

/**
 * Custom serializer for DownloadProgress to handle nullable serialization
 */
object DownloadProgressSerializer : KSerializer<DownloadProgress?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DownloadProgress") {
        element<String>("mapId")
        element<String>("mapName")
        element<Int>("current")
        element<Int>("total")
        element<Long>("bytesDownloaded")
        element<Long>("totalBytes")
        element<String>("status")
        element<Int>("percentComplete")
    }

    override fun serialize(encoder: Encoder, value: DownloadProgress?) {
        if (value == null) {
            encoder.encodeNull()
            return
        }

        val compositeEncoder = encoder.beginStructure(descriptor)
        compositeEncoder.encodeStringElement(descriptor, 0, value.mapId)
        compositeEncoder.encodeStringElement(descriptor, 1, value.mapName)
        compositeEncoder.encodeIntElement(descriptor, 2, value.current)
        compositeEncoder.encodeIntElement(descriptor, 3, value.total)
        compositeEncoder.encodeLongElement(descriptor, 4, value.bytesDownloaded)
        compositeEncoder.encodeLongElement(descriptor, 5, value.totalBytes)
        compositeEncoder.encodeStringElement(descriptor, 6, value.status)
        compositeEncoder.encodeIntElement(descriptor, 7, value.percentComplete)
        compositeEncoder.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): DownloadProgress? {
        if (decoder.decodeNotNullMark()) {
            val compositeDecoder = decoder.beginStructure(descriptor)
            val mapId = compositeDecoder.decodeStringElement(descriptor, 0)
            val mapName = compositeDecoder.decodeStringElement(descriptor, 1)
            val current = compositeDecoder.decodeIntElement(descriptor, 2)
            val total = compositeDecoder.decodeIntElement(descriptor, 3)
            val bytesDownloaded = compositeDecoder.decodeLongElement(descriptor, 4)
            val totalBytes = compositeDecoder.decodeLongElement(descriptor, 5)
            val status = compositeDecoder.decodeStringElement(descriptor, 6)
            val percentComplete = compositeDecoder.decodeIntElement(descriptor, 7)
            compositeDecoder.endStructure(descriptor)

            return DownloadProgress(
                mapId = mapId,
                mapName = mapName,
                current = current,
                total = total,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                status = status,
                percentComplete = percentComplete
            )
        }
        return null
    }
}
