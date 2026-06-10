package org.mountaincircles.app.ui.map

/**
 * Hosted basemap styles (HTTPS) for live map and MapLibre offline packs.
 * Source files live under `styles/` on the `main` branch.
 */
object BasemapStyle {
    private const val BASE =
        "https://raw.githubusercontent.com/gabriel-briffe/MC-kmp/main/styles"

    const val STYLE_OSM: String = "$BASE/basemap-raster-osm.json"
    const val STYLE_OSM_TERRAIN: String = "$BASE/basemap-raster-osm-terrain.json"
    const val STYLE_TERRAIN: String = "$BASE/basemap-terrain-only.json"

    /** Neutral background when OSM is disabled on the live map. */
    const val EMPTY_STYLE_JSON: String = """
{
    "version": 8,
    "layers": [
        {
            "id": "background",
            "type": "background",
            "paint": { "background-color": "#d8d8d8" }
        }
    ]
}
"""

    /** HTTPS style for offline packs matching enabled basemap layers. */
    fun offlineStyleUrl(osmEnabled: Boolean, terrainEnabled: Boolean): String? = when {
        osmEnabled && terrainEnabled -> STYLE_OSM_TERRAIN
        osmEnabled -> STYLE_OSM
        terrainEnabled -> STYLE_TERRAIN
        else -> null
    }

    fun offlineLayersLabel(osmEnabled: Boolean, terrainEnabled: Boolean): String = buildList {
        if (osmEnabled) add("OSM")
        if (terrainEnabled) add("Terrain")
    }.joinToString(" + ").ifEmpty { "none" }
}
