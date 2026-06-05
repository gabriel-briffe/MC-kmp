package org.mountaincircles.app.modules.circles.layer.ui.composables

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.expressions.dsl.*
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.io.getGlobalFileManager

/**
 * Polygon layer with color coding based on color_id property (sectors)
 */
@Composable
fun CirclesPolygonLayer(module: CirclesModule) {
    Logger.log("RECOMPOSITION_LAYERS", LogLevel.DEBUG, "Circles polygon layer recomposing")

    // ✅ Selective reactivity - observe only polygon-relevant flows
    val isAvailable by module.hasDataToRender.collectAsState()
    val activeConfig by module.activeConfig.collectAsState()
    val sectorsMinZoom by module.sectorsMinZoom.collectAsState()
    val sectorsOpacity by module.sectorsOpacity.collectAsState() // ← Only this triggers polygon recomposition

    // Only render if available and has active config
    if (isAvailable && activeConfig != null) {
        val config = activeConfig!!

        // Use key for stable layer identity to prevent unnecessary recreations
        key(config.packId, config.configId) {
            // Use the actual file structure: circles/packId/configId/
            val fileManager = getGlobalFileManager()
            val basePath = "${fileManager.getAppDataDirectory()}/circles/${config.packId}/${config.configId}"
            val metadata = config.metadata
            val prefix = metadata?.prefix ?: config.configId.split("-").dropLast(1).joinToString("-")
            val policy = metadata?.policy ?: config.packId
            val sectorsFileName = "aa_${policy}_${prefix}_sectors.geojson"
            val geoJsonPath = "$basePath/$sectorsFileName"

            // Use URI approach instead of loading file content
            val fileUri = "file://$geoJsonPath"

            val geoJsonSource = rememberGeoJsonSource(
                data = GeoJsonData.Uri(fileUri)
            )

            // Create polygon fill layer with color mapping and configurable opacity
            FillLayer(
                id = "polygons-${config.packId}-${config.configId}",
                source = geoJsonSource,
                minZoom = sectorsMinZoom,
                color = switch(
                    input = feature["color_id"].asNumber(),
                    case(0, const(androidx.compose.ui.graphics.Color.Blue)),
                    case(1, const(androidx.compose.ui.graphics.Color.Magenta)),
                    case(2, const(androidx.compose.ui.graphics.Color.Yellow)),
                    case(3, const(androidx.compose.ui.graphics.Color.Cyan)),
                    case(4, const(androidx.compose.ui.graphics.Color.Green)),
                    case(5, const(androidx.compose.ui.graphics.Color.Red)),
                    case(6, const(androidx.compose.ui.graphics.Color(0xFFFFA500))), // Orange
                    fallback = const(androidx.compose.ui.graphics.Color.Black)
                ),
                opacity = const(sectorsOpacity)
            )
        }
    }
}
