package org.mountaincircles.app.modules.skysight.logic

import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.data.SkysightDataUrl

/**
 * Pure data helpers for Skysight (layer URLs, filter ranges, file paths).
 * Extracted from SkysightModule for clearer separation of concerns.
 */

fun getLayerDataUrls(module: SkysightModule, layerId: String, date: String): List<SkysightDataUrl> {
    return module.state.value.availableLayers
        .find { it.id == layerId }
        ?.dataUrls?.get(date) ?: emptyList()
}

fun findDataUrl(module: SkysightModule, layerId: String, date: String, timestamp: Long): SkysightDataUrl? {
    return getLayerDataUrls(module, layerId, date).find { it.time == timestamp }
}

fun hasLayerDataUrls(module: SkysightModule, layerId: String, date: String): Boolean {
    return getLayerDataUrls(module, layerId, date).isNotEmpty()
}

fun getLayerFilterRange(module: SkysightModule, layerId: String): Pair<Float, Float> {
    return when {
        layerId == "wblmaxmin" -> Pair(module.state.value.wblmaxminFilterMin, module.state.value.wblmaxminFilterMax)
        layerId.startsWith("w_") -> Pair(module.state.value.waveFilterMin, module.state.value.waveFilterMax)
        else -> Pair(0f, 0f)
    }
}

fun getLocalFilePath(module: SkysightModule, fileKey: String): String {
    return module.storage.getLocalFilePath(fileKey)
}
