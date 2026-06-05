package org.mountaincircles.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.expressions.dsl.*
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.MarkerStyle
import org.mountaincircles.app.state.globalState
import org.mountaincircles.app.ui.map.MapClickEvent
import org.mountaincircles.app.ui.AppIcons

/**
 * Core marker layer - shared service for displaying markers from any module
 */
@Composable
fun CoreMarkerLayer() {
    val globalState = globalState()

    val showMarker by globalState.showMarker.collectAsState()
    val markerPosition by globalState.markerPosition.collectAsState()
    val markerStyle by globalState.markerStyle.collectAsState()

    Logger.log("CORE_MARKER", LogLevel.DEBUG,
        "CoreMarkerLayer: show=$showMarker, pos=${markerPosition?.let { "(${it.latitude}, ${it.longitude})" } ?: "null"}, style=$markerStyle")

    // Early return if marker shouldn't be shown
    if (!showMarker || markerPosition == null) {
        return
    }

    // Safe access to markerPosition (already checked for null above)
    val position = markerPosition!!

    // Render marker based on style
    when (markerStyle) {
        MarkerStyle.AIRSPACE -> AirspaceStyleMarker(position)
        MarkerStyle.POI -> PoiStyleMarker(position)
        MarkerStyle.SEARCH -> SearchStyleMarker(position)
        else -> DefaultStyleMarker(position)
    }
}

/**
 * Default marker styling
 */
@Composable
private fun DefaultStyleMarker(position: MapClickEvent) {
    Logger.log("CORE_MARKER", LogLevel.DEBUG, "Rendering DEFAULT marker at (${position.latitude}, ${position.longitude})")

    // Create GeoJSON for marker position
    val markerGeoJson = remember(position) {
        """
        {
            "type": "Feature",
            "properties": {},
            "geometry": {
                "type": "Point",
                "coordinates": [${position.longitude}, ${position.latitude}]
            }
        }
        """.trimIndent()
    }

    // Create GeoJSON source
    val markerSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(markerGeoJson)
    )

    // Render marker as SymbolLayer
    SymbolLayer(
        id = "core_marker_default",
        source = markerSource,
        iconImage = image(AppIcons.Marker()),
        iconSize = const(1.0f),
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true)
    )
}

/**
 * Airspace-specific marker styling
 */
@Composable
private fun AirspaceStyleMarker(position: MapClickEvent) {
    Logger.log("CORE_MARKER", LogLevel.DEBUG, "Rendering AIRSPACE marker at (${position.latitude}, ${position.longitude})")

    // Create GeoJSON for marker position
    val markerGeoJson = remember(position) {
        """
        {
            "type": "Feature",
            "properties": {},
            "geometry": {
                "type": "Point",
                "coordinates": [${position.longitude}, ${position.latitude}]
            }
        }
        """.trimIndent()
    }

    // Create GeoJSON source
    val markerSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(markerGeoJson)
    )

    // Render airspace marker as SymbolLayer with existing marker icon
    SymbolLayer(
        id = "core_marker_airspace",
        source = markerSource,
        iconImage = image(AppIcons.Marker()),
        iconSize = const(1.0f),
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true)
    )
}

/**
 * Point of interest marker styling
 */
@Composable
private fun PoiStyleMarker(position: MapClickEvent) {
    Logger.log("CORE_MARKER", LogLevel.DEBUG, "Rendering POI marker at (${position.latitude}, ${position.longitude})")

    // Create GeoJSON for marker position
    val markerGeoJson = remember(position) {
        """
        {
            "type": "Feature",
            "properties": {},
            "geometry": {
                "type": "Point",
                "coordinates": [${position.longitude}, ${position.latitude}]
            }
        }
        """.trimIndent()
    }

    // Create GeoJSON source
    val markerSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(markerGeoJson)
    )

    // Render POI marker as SymbolLayer with existing marker icon
    SymbolLayer(
        id = "core_marker_poi",
        source = markerSource,
        iconImage = image(AppIcons.Marker()),
        iconSize = const(1.0f),
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true)
    )
}

/**
 * Search result marker styling
 */
@Composable
private fun SearchStyleMarker(position: MapClickEvent) {
    Logger.log("CORE_MARKER", LogLevel.DEBUG, "Rendering SEARCH marker at (${position.latitude}, ${position.longitude})")

    // Create GeoJSON for marker position
    val markerGeoJson = remember(position) {
        """
        {
            "type": "Feature",
            "properties": {},
            "geometry": {
                "type": "Point",
                "coordinates": [${position.longitude}, ${position.latitude}]
            }
        }
        """.trimIndent()
    }

    // Create GeoJSON source
    val markerSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(markerGeoJson)
    )

    // Render search marker as SymbolLayer with existing marker icon
    SymbolLayer(
        id = "core_marker_search",
        source = markerSource,
        iconImage = image(AppIcons.Marker()),
        iconSize = const(1.0f),
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true)
    )
}
