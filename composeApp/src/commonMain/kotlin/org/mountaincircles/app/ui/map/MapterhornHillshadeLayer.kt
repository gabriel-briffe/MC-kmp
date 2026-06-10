package org.mountaincircles.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.HillshadeLayer
import org.maplibre.compose.sources.RasterDemEncoding
import org.maplibre.compose.sources.TileSetOptions
import org.maplibre.compose.sources.rememberRasterDemSource

/**
 * Default global hillshade over the OSM basemap, using Mapterhorn Terrarium DEM tiles.
 */
@Composable
fun MapterhornHillshadeLayer() {
    val terrainSource = rememberRasterDemSource(
        tiles = listOf(MapterhornConfig.TILE_URL_TEMPLATE),
        options = TileSetOptions(maxZoom = MapterhornConfig.MAX_ZOOM),
        tileSize = MapterhornConfig.TILE_SIZE,
        encoding = RasterDemEncoding.Terrarium,
    )

    HillshadeLayer(
        id = "mapterhorn-hillshade",
        source = terrainSource,
        maxZoom = MapterhornConfig.MAX_ZOOM.toFloat(),
        shadowColor = const(Color(MapterhornConfig.HILLSHADE_SHADOW_COLOR)),
        highlightColor = const(Color.White),
        illuminationDirection = const(MapterhornConfig.HILLSHADE_ILLUMINATION_DIRECTION),
        exaggeration = const(MapterhornConfig.HILLSHADE_EXAGGERATION),
    )
}
