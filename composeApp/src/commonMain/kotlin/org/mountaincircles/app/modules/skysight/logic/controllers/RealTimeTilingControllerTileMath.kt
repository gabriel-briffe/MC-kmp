package org.mountaincircles.app.modules.skysight.logic.controllers

import kotlin.math.max
import kotlin.math.min
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.GlobalState

/**
 * Tile math for RealTimeTilingController (viewport → tile coordinates and bounds).
 * Extracted for clearer separation of concerns.
 */
object RealTimeTilingControllerTileMath {

    /**
     * Calculate visible tiles from viewport bounds for current zoom level only.
     * Returns list of (zoom, x, y) tuples for the current zoom level.
     */
    fun calculateVisibleTiles(viewportBounds: List<Double>, globalState: GlobalState): List<Triple<Int, Int, Int>> {
        val cameraState = globalState.currentCameraState.value
        val mapZoomLevel = cameraState?.position?.zoom ?: 5.0
        val currentZoom = min(max(mapZoomLevel.toInt(), 3), 8)
        Logger.log("SKYSIGHT_REALTIME_TILING_ZZZ", LogLevel.INFO, "Using actual map zoom level $mapZoomLevel (clamped to $currentZoom) for tile requests")
        val cameraLongitude = cameraState?.position?.target?.longitude
        val tiles = calculateTilesForZoomLevel(viewportBounds, currentZoom, cameraLongitude)
        Logger.log("REALTIME_TILING", LogLevel.INFO, "Calculated ${tiles.size} tiles for zoom $currentZoom")
        return tiles
    }

    /**
     * Calculate tiles for a specific zoom level
     */
    fun calculateTilesForZoomLevel(viewportBounds: List<Double>, zoom: Int, cameraLongitude: Double? = null): List<Triple<Int, Int, Int>> {
        val (west, south, east, north) = viewportBounds
        val tiles = mutableListOf<Triple<Int, Int, Int>>()
        val minTileY = max(0, latToTile(north, zoom))
        val maxTileY = min((1 shl zoom) - 1, latToTile(south, zoom))
        val isDateLineCrossing = cameraLongitude != null && (cameraLongitude < west || cameraLongitude > east)

        if (isDateLineCrossing) {
            Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Camera outside viewport detected: cameraLng=$cameraLongitude, viewport=[$west,$east], using wraparound calculation")
            val maxTileX = (1 shl zoom) - 1
            val westTile = lonToTile(west, zoom)
            for (x in 0..westTile) {
                for (y in minTileY..maxTileY) tiles.add(Triple(zoom, x, y))
            }
            val eastTile = lonToTile(east, zoom)
            for (x in eastTile..maxTileX) {
                for (y in minTileY..maxTileY) tiles.add(Triple(zoom, x, y))
            }
            Logger.log("REALTIME_TILING", LogLevel.DEBUG, "Wraparound tiles: 0..$westTile and $eastTile..$maxTileX (covers $west° to $east° across date line)")
        } else {
            val minTileX = max(0, lonToTile(west, zoom))
            val maxTileX = min((1 shl zoom) - 1, lonToTile(east, zoom))
            for (x in minTileX..maxTileX) {
                for (y in minTileY..maxTileY) tiles.add(Triple(zoom, x, y))
            }
        }
        return tiles
    }

    /**
     * LEGACY: Calculate visible tiles from viewport bounds (single zoom level)
     */
    fun calculateVisibleTilesFromBounds(viewportBounds: List<Double>, globalState: GlobalState): List<Triple<Int, Int, Int>> {
        return calculateVisibleTiles(viewportBounds, globalState)
    }

    fun lonToTile(lon: Double, zoom: Int): Int {
        return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    fun latToTile(lat: Double, zoom: Int): Int {
        val latRad = lat * kotlin.math.PI / 180.0
        return ((1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / kotlin.math.PI) / 2.0 * (1 shl zoom)).toInt()
    }

    fun latLngToTile(lat: Double, lng: Double, zoom: Int): Pair<Int, Int> {
        val latRad = lat * kotlin.math.PI / 180.0
        val tileX = ((lng + 180.0) / 360.0 * (1 shl zoom)).toInt()
        val tileY = ((1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / kotlin.math.PI) / 2.0 * (1 shl zoom)).toInt()
        return Pair(tileX, tileY)
    }

    /**
     * Calculate geographic bounds for a Web Mercator tile.
     * Returns [west, south, east, north] in degrees.
     */
    fun calculateTileBounds(zoom: Int, x: Int, y: Int): List<Double> {
        val n = kotlin.math.PI - 2.0 * kotlin.math.PI * y / (1 shl zoom)
        val west = x.toDouble() / (1 shl zoom) * 360.0 - 180.0
        val east = (x + 1).toDouble() / (1 shl zoom) * 360.0 - 180.0
        val north = 180.0 / kotlin.math.PI * kotlin.math.atan(kotlin.math.sinh(n))
        val south = 180.0 / kotlin.math.PI * kotlin.math.atan(kotlin.math.sinh(kotlin.math.PI - 2.0 * kotlin.math.PI * (y + 1) / (1 shl zoom)))
        return listOf(west, south, east, north)
    }
}
