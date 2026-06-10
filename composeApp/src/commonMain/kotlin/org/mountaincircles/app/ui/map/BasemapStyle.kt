package org.mountaincircles.app.ui.map

import org.mountaincircles.app.state.OfflineRegionConfig

/**
 * Unified basemap style: OSM raster + Mapterhorn Terrarium DEM hillshade.
 * Live map uses [buildJson]; offline packs use [buildOfflinePackJson] (OSM tiles only).
 */
object BasemapStyle {
    /**
     * Style for MapLibre offline packs.
     *
     * OSM raster only — MapLibre offline tile pyramids do not reliably prefetch
     * `raster-dem` / hillshade layers (pack stays at required=1 with no tile HTTP).
     * Hillshade continues to stream online via [buildJson] on the live map.
     */
    fun buildOfflinePackJson(): String = """
{
    "version": 8,
    "name": "Mountain Circles Offline OSM",
    "sources": {
        "osm": {
            "type": "raster",
            "tiles": [
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            ],
            "tileSize": 256,
            "scheme": "xyz",
            "minzoom": 0,
            "maxzoom": ${OfflineRegionConfig.MAX_ZOOM},
            "attribution": "© OpenStreetMap contributors"
        }
    },
    "layers": [
        {
            "id": "osm-layer",
            "type": "raster",
            "source": "osm",
            "minzoom": 0,
            "maxzoom": ${OfflineRegionConfig.MAX_ZOOM}
        }
    ]
}
""".trimIndent()

    fun buildJson(glyphsBaseUri: String): String = """
{
    "version": 8,
    "name": "Mountain Circles Basemap",
    "metadata": {
        "mapbox:autocomposite": false,
        "mapbox:type": "template"
    },
    "sources": {
        "osm": {
            "type": "raster",
            "tiles": [
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            ],
            "tileSize": 256,
            "minzoom": 0,
            "maxzoom": 18,
            "attribution": "© OpenStreetMap contributors"
        },
        "mapterhorn-dem": {
            "type": "raster-dem",
            "tiles": [
                "${MapterhornConfig.TILE_URL_TEMPLATE}"
            ],
            "tileSize": ${MapterhornConfig.TILE_SIZE},
            "minzoom": 0,
            "maxzoom": ${MapterhornConfig.MAX_ZOOM},
            "encoding": "terrarium",
            "attribution": "${MapterhornConfig.ATTRIBUTION}"
        }
    },
    "sprite": "",
    "glyphs": "$glyphsBaseUri{fontstack}/{range}.pbf",
    "layers": [
        {
            "id": "osm-layer",
            "type": "raster",
            "source": "osm",
            "minzoom": 0,
            "maxzoom": 18
        },
        {
            "id": "mapterhorn-hillshade",
            "type": "hillshade",
            "source": "mapterhorn-dem",
            "maxzoom": ${MapterhornConfig.MAX_ZOOM},
            "paint": {
                "hillshade-exaggeration": ${MapterhornConfig.HILLSHADE_EXAGGERATION},
                "hillshade-shadow-color": "#473B24",
                "hillshade-highlight-color": "#ffffff",
                "hillshade-illumination-direction": ${MapterhornConfig.HILLSHADE_ILLUMINATION_DIRECTION.toInt()}
            }
        }
    ]
}
""".trimIndent()
}
