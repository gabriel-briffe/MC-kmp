package org.mountaincircles.app.modules.airspace.logic

/**
 * Centralized constants for the airspace module
 * Single source of truth for all airspace-related paths, filenames, and constants
 */
object AirspaceConstants {

    // Directory and file names
    const val AIRSPACE_DIR = "airspace"
    const val AIRSPACE_GEOJSON_FILE = "airspace.geojson"
    const val CACHE_DIR = "cache"

    // File extensions
    const val GEOJSON_EXT = ".geojson"
    const val JSON_EXT = ".json"

    // Derived file paths
    val AIRSPACE_METADATA_FILE: String
        get() = AIRSPACE_GEOJSON_FILE.replace(GEOJSON_EXT, JSON_EXT)

    // Full directory paths (relative to app data directory)
    val AIRSPACE_DIR_PATH: String
        get() = AIRSPACE_DIR

    val CACHE_DIR_PATH: String
        get() = "$AIRSPACE_DIR/$CACHE_DIR"
}
