package org.mountaincircles.app.ui.map

/**
 * Unified basemap style: OSM raster + Mapterhorn Terrarium DEM hillshade.
 * Used for live rendering and MapLibre offline region packs (same tile URLs).
 */
object BasemapStyle {
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
