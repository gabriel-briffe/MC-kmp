package org.mountaincircles.app.ui.map

/**
 * Shared HTTPS basemap style for live map and MapLibre offline packs.
 *
 * Source of truth: [styles/basemap-raster-osm.json] in the repo (served via raw.githubusercontent.com).
 * Mapterhorn hillshade is added at runtime via [MapterhornHillshadeLayer] on the live map only.
 */
object BasemapStyle {
    /**
     * OSM raster-only style — same URL for [org.maplibre.compose.map.MaplibreMap] and offline regions.
     * Must stay in sync with `styles/basemap-raster-osm.json` on the `main` branch.
     */
    const val SHARED_STYLE_URL: String =
        "https://raw.githubusercontent.com/gabriel-briffe/MC-kmp/main/styles/basemap-raster-osm.json"
}
