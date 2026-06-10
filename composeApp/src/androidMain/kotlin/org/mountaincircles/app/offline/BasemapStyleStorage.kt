package org.mountaincircles.app.offline

import org.mountaincircles.app.io.getFilesDirectory
import org.mountaincircles.app.ui.map.BasemapStyle
import java.io.File

private const val STYLE_RELATIVE_PATH = "offline/basemap-style.json"

fun ensureBasemapStyleFile(): File {
    val file = File(getFilesDirectory(), STYLE_RELATIVE_PATH)
    file.parentFile?.mkdirs()
    val json = BasemapStyle.buildOfflinePackJson()
    if (!file.exists() || file.readText() != json) {
        file.writeText(json)
    }
    return file
}

fun getBasemapStyleUrl(): String = "file://${ensureBasemapStyleFile().absolutePath}"
