package org.mountaincircles.app.modules.airports.logic

/**
 * Centralized constants for the airports module
 * Single source of truth for all airport-related paths, filenames, and constants
 * Cloned from AirspaceConstants
 */

object AirportsConstants {

    // Directory and file names
    const val AIRPORTS_DIR = "airports"
    const val AIRPORTS_GEOJSON_FILE = "airports.geojson"
    const val CACHE_DIR = "cache"

    // File extensions
    const val GEOJSON_EXT = ".geojson"
    const val JSON_EXT = ".json"

    // Derived file paths
    val AIRPORTS_METADATA_FILE: String
        get() = AIRPORTS_GEOJSON_FILE.replace(GEOJSON_EXT, JSON_EXT)

    // Full directory paths (relative to app data directory)
    val AIRPORTS_DIR_PATH: String
        get() = AIRPORTS_DIR

    val CACHE_DIR_PATH: String
        get() = "$AIRPORTS_DIR/$CACHE_DIR"
}
