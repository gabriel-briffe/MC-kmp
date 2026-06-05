package org.mountaincircles.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mountaincircles.app.widget.glance.GlanceWaveForecastWorker
import org.mountaincircles.app.widget.glance.GlanceWaveTomorrowWorker
import org.mountaincircles.app.widget.glance.WaveTodayGlanceWidget
import org.mountaincircles.app.widget.glance.WaveTodayRepo
import org.mountaincircles.app.widget.glance.WaveTomorrowGlanceWidget
import org.mountaincircles.app.widget.glance.WaveTomorrowRepo
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position
import kotlin.math.roundToInt
import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

// Platform-specific location name fetching function
actual suspend fun fetchLocationName(latitude: Double, longitude: Double): String {
    return try {
        val downloadManager = org.mountaincircles.app.network.createDownloadManager()
        val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&zoom=10&addressdetails=1"

        Logger.log("WIDGET", LogLevel.INFO, "Fetching location name from: $url")

        val headers = mapOf("User-Agent" to "MountainCircles-MeteogramWidget/1.0")
        val result = downloadManager.downloadText(url, headers)

        if (result.isSuccess) {
            val response = result.getOrThrow()
            Logger.log("WIDGET", LogLevel.DEBUG, "Nominatim response: $response")

            // Extract municipality from address details, fallback to name
            val municipalityRegex = "\"municipality\":\"([^\"]+)\"".toRegex()
            val municipalityMatch = municipalityRegex.find(response)

            val locationName = if (municipalityMatch != null) {
                municipalityMatch.groupValues[1]
            } else {
                // Fallback to name if municipality not found
                val nameRegex = "\"name\":\"([^\"]+)\"".toRegex()
                val nameMatch = nameRegex.find(response)
                nameMatch?.groupValues?.get(1) ?: "Unknown"
            }

            Logger.log("WIDGET", LogLevel.INFO, "Location name: $locationName")
            return locationName
        } else {
            Logger.log("WIDGET", LogLevel.ERROR, "Nominatim API request failed: ${result.exceptionOrNull()?.message}")
            return "Unknown"
        }

    } catch (e: Exception) {
        Logger.log("WIDGET", LogLevel.ERROR, "Exception fetching location name: ${e.message}", e)
        return "Unknown"
    }
}

/**
 * Metadata for widget tile configuration
 */
@Serializable
data class WidgetTileMetadata(
    val tiles: List<TileCoordinate>
)

@Serializable
data class TileCoordinate(
    val zoom: Int,
    val x: Int,
    val y: Int
)

data class ViewportBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

data class AssemblyResult(
    val bitmap: android.graphics.Bitmap,
    val successfulTiles: List<TileCoordinate>
)

/**
 * Android implementation of tile capture for widget background
 */
actual suspend fun captureCurrentMapView(context: Any?) {
    try {
        val androidContext = context as android.content.Context

        // Get current camera position from global state
        val globalState = org.mountaincircles.app.state.getGlobalState()
        val cameraState = globalState.currentCameraState.value

        Logger.log("WIDGET", LogLevel.DEBUG, "Camera state: $cameraState")

        // Extract position from camera state
        if (cameraState == null) {
            Logger.log("WIDGET", LogLevel.ERROR, "No camera state available - cannot capture map view")
            throw IllegalStateException("Camera state not available - cannot capture map view")
        }

        Logger.log("WIDGET", LogLevel.INFO, "Using current camera position")
        val position = cameraState.position

        // Calculate viewport bounds on UI thread (required by MapLibre)
        val viewportBounds = withContext(Dispatchers.Main) {
            try {
                calculateViewportBounds(cameraState)
            } catch (e: Exception) {
                Logger.log("WIDGET", LogLevel.ERROR, "Failed to calculate viewport bounds: ${e.message}", e)
                null
            }
        }

        // Do tile processing on IO thread
        withContext(Dispatchers.IO) {
            Logger.log("WIDGET", LogLevel.INFO, "Starting tile capture for widget background")
            if (viewportBounds == null) {
                Logger.log("WIDGET", LogLevel.WARN, "Could not calculate viewport bounds, using center-based fallback")
                // Fallback to simple center-based approach
                val zoom = minOf(position.zoom.roundToInt(), 7)
                val tileX = longitudeToTileX(position.target.longitude, zoom)
                val tileY = latitudeToTileY(position.target.latitude, zoom)
                val highResZoom = zoom + 1
                val tiles = listOf(
                    TileCoordinate(highResZoom, tileX * 2, tileY * 2),
                    TileCoordinate(highResZoom, tileX * 2 + 1, tileY * 2),
                    TileCoordinate(highResZoom, tileX * 2, tileY * 2 + 1),
                    TileCoordinate(highResZoom, tileX * 2 + 1, tileY * 2 + 1)
                )

                // Fetch all tiles directly from OpenTopoMap online
                Logger.log("WIDGET", LogLevel.INFO, "Fetching ${tiles.size} tiles directly from OpenTopoMap")
                val assembledResult = fetchTilesOnlineSync(androidContext, tiles)

                val finalBitmap = assembledResult?.bitmap ?: android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888).apply {
                    val canvas = android.graphics.Canvas(this)
                    val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
                    canvas.drawRect(0f, 0f, 512f, 512f, paint)
                }

                // Save bitmap to persistent storage
                val widgetDir = File(androidContext.filesDir, "widget_images")
                widgetDir.mkdirs()
                val imageFile = File(widgetDir, "widget_map_snapshot.png")
                FileOutputStream(imageFile).use { out ->
                    finalBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                finalBitmap.recycle()

                // Store metadata - use the tiles we calculated even if none were found
                storeWidgetTileMetadata(androidContext, tiles)

            } else {
                Logger.log("WIDGET", LogLevel.INFO, "Viewport bounds: N=${viewportBounds.north}, S=${viewportBounds.south}, E=${viewportBounds.east}, W=${viewportBounds.west}")

                // Find all subtiles that intersect the viewport
                val visibleSubtiles = findVisibleSubtiles(viewportBounds, cameraState!!)
                Logger.log("WIDGET", LogLevel.INFO, "Found ${visibleSubtiles.size} visible subtiles for assembly")

                // Fetch all tiles directly from OpenTopoMap online
                Logger.log("WIDGET", LogLevel.INFO, "Fetching ${visibleSubtiles.size} tiles directly from OpenTopoMap")
                val assembledResult = fetchTilesOnlineSync(androidContext, visibleSubtiles)

                // Create final bitmap (assembled tiles or black fallback)
                val finalBitmap = assembledResult?.bitmap ?: android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888).apply {
                    val canvas = android.graphics.Canvas(this)
                    val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
                    canvas.drawRect(0f, 0f, 512f, 512f, paint)
                }

                // Save to persistent storage where widget can access it
                val widgetDir = File(androidContext.filesDir, "widget_images")
                widgetDir.mkdirs()
                val imageFile = File(widgetDir, "widget_map_snapshot.png")

                FileOutputStream(imageFile).use { out ->
                    finalBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }

                finalBitmap.recycle()

                Logger.log("WIDGET", LogLevel.INFO, "Tiles captured and saved to: ${imageFile.absolutePath}")

                // Store tile metadata for wave forecasts - use all visible subtiles even if none were found
                // This allows the wave worker to still try to get wave data for these coordinates
                val tilesForMetadata = if (visibleSubtiles.isNotEmpty()) visibleSubtiles else {
                    // If no subtiles found, still create some based on center position for wave worker
                    val zoom = minOf(position.zoom.roundToInt() + 1, 8) // subtile zoom
                    val centerTileX = longitudeToTileX(position.target.longitude, zoom)
                    val centerTileY = latitudeToTileY(position.target.latitude, zoom)
                    listOf(
                        TileCoordinate(zoom, centerTileX, centerTileY),
                        TileCoordinate(zoom, centerTileX + 1, centerTileY),
                        TileCoordinate(zoom, centerTileX, centerTileY + 1),
                        TileCoordinate(zoom, centerTileX + 1, centerTileY + 1)
                    )
                }
                storeWidgetTileMetadata(androidContext, tilesForMetadata)

                // Delete old overlay files so widgets show new terrain immediately
                // Wave workers will re-composite with new terrain when they run
                val todayOverlay = File(widgetDir, "widget_today_overlay.png")
                val tomorrowOverlay = File(widgetDir, "widget_tomorrow_overlay.png")
                if (todayOverlay.exists()) {
                    todayOverlay.delete()
                    Logger.log("WIDGET", LogLevel.DEBUG, "Deleted old today overlay")
                }
                if (tomorrowOverlay.exists()) {
                    tomorrowOverlay.delete()
                    Logger.log("WIDGET", LogLevel.DEBUG, "Deleted old tomorrow overlay")
                }

                // Reload image data in repos (will now use terrain-only) and update widgets
                WaveTodayRepo.loadData(androidContext)
                WaveTomorrowRepo.loadData(androidContext)
                WaveTodayGlanceWidget().updateAll(androidContext)
                WaveTomorrowGlanceWidget().updateAll(androidContext)

                // Trigger wave workers to download fresh data and composite with new terrain
                GlanceWaveForecastWorker.runImmediateForceRefresh(androidContext)
                GlanceWaveTomorrowWorker.runImmediateForceRefresh(androidContext)

                Logger.log("WIDGET", LogLevel.INFO, "Wave widgets refreshed and workers triggered")
            }
        }
    } catch (e: Exception) {
        Logger.log("WIDGET", LogLevel.ERROR, "Failed to capture tile: ${e.message}", e)
        throw e
    }
}


/**
 * Convert longitude to tile X coordinate
 */
private fun longitudeToTileX(longitude: Double, zoom: Int): Int {
    val x = (longitude + 180.0) / 360.0 * (1 shl zoom)
    return x.toInt()
}

/**
 * Convert latitude to tile Y coordinate (TMS scheme)
 */
private fun latitudeToTileY(latitude: Double, zoom: Int): Int {
    val latRad = Math.toRadians(latitude)
    val y = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom)
    return y.toInt()
}

/**
 * Extract a tile from MBTiles SQLite database
 */
/**
 * Store widget tile metadata for wave forecast access
 */
private fun storeWidgetTileMetadata(context: Context, tiles: List<TileCoordinate>) {
    try {
        val metadata = WidgetTileMetadata(tiles)
        val jsonString = Json.encodeToString(metadata)

        val prefs = context.getSharedPreferences("widget_tile_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("widget_tile_metadata", jsonString).apply()

        Logger.log("WIDGET", LogLevel.INFO, "Stored widget tile metadata: ${tiles.size} tiles")
    } catch (e: Exception) {
        Logger.log("WIDGET", LogLevel.ERROR, "Failed to store widget tile metadata: ${e.message}", e)
    }
}


private fun calculateViewportBounds(cameraState: org.maplibre.compose.camera.CameraState): ViewportBounds? {
    return try {
        val projection = cameraState.projection
        if (projection == null) {
            Logger.log("WIDGET", LogLevel.WARN, "Camera projection not available for viewport calculation")
            return null
        }

        val visibleRegion = projection.queryVisibleRegion()
        val lngs = listOf(
            visibleRegion.farLeft.longitude,
            visibleRegion.farRight.longitude,
            visibleRegion.nearLeft.longitude,
            visibleRegion.nearRight.longitude
        )
        val lats = listOf(
            visibleRegion.farLeft.latitude,
            visibleRegion.farRight.latitude,
            visibleRegion.nearLeft.latitude,
            visibleRegion.nearRight.latitude
        )

        val west = lngs.minOrNull() ?: visibleRegion.farLeft.longitude
        val east = lngs.maxOrNull() ?: visibleRegion.farRight.longitude
        val south = lats.minOrNull() ?: visibleRegion.nearLeft.latitude
        val north = lats.maxOrNull() ?: visibleRegion.farLeft.latitude

        ViewportBounds(north, south, east, west)
    } catch (e: Exception) {
        Logger.log("WIDGET", LogLevel.ERROR, "Failed to calculate viewport bounds: ${e.message}", e)
        null
    }
}

private fun findVisibleSubtiles(bounds: ViewportBounds, cameraState: org.maplibre.compose.camera.CameraState): List<TileCoordinate> {
    try {
        // Use current zoom level, capped at 7 for tile availability
        val rawZoom = cameraState.position.zoom.roundToInt()
        val baseZoom = minOf(rawZoom, 7)
        val subtileZoom = baseZoom + 1

        Logger.log("WIDGET", LogLevel.DEBUG, "Finding visible subtiles: baseZoom=$baseZoom, subtileZoom=$subtileZoom")

        // Calculate tile bounds at base zoom level
        // Use floor/ceil to ensure we cover the entire viewport
        val minTileX = Math.floor(longitudeToTileX(bounds.west, baseZoom).toDouble()).toInt()
        val maxTileX = Math.ceil(longitudeToTileX(bounds.east, baseZoom).toDouble()).toInt()
        val minTileY = Math.floor(latitudeToTileY(bounds.north, baseZoom).toDouble()).toInt() // higher latitude = lower Y
        val maxTileY = Math.ceil(latitudeToTileY(bounds.south, baseZoom).toDouble()).toInt()

        Logger.log("WIDGET", LogLevel.DEBUG, "Tile bounds at zoom $baseZoom: x=$minTileX..$maxTileX, y=$minTileY..$maxTileY")

        val visibleSubtiles = mutableListOf<TileCoordinate>()

        // For each tile at base zoom that intersects viewport
        for (tileX in minTileX..maxTileX) {
            for (tileY in minTileY..maxTileY) {
                // Check if this base tile intersects viewport
                val tileBounds = getTileBounds(tileX, tileY, baseZoom)
                Logger.log("WIDGET", LogLevel.DEBUG, "Checking base tile ($tileX,$tileY) bounds: N=${tileBounds.north}, S=${tileBounds.south}, E=${tileBounds.east}, W=${tileBounds.west}")
                val intersects = tileIntersectsViewport(tileBounds, bounds)
                Logger.log("WIDGET", LogLevel.DEBUG, "Base tile ($tileX,$tileY) intersects viewport: $intersects")

                if (intersects) {
                    Logger.log("WIDGET", LogLevel.DEBUG, "Base tile ($tileX,$tileY) intersects viewport - generating subtiles")

                    // Generate the 4 subtiles for this base tile
                    val subtileX = tileX * 2
                    val subtileY = tileY * 2

                    for (dx in 0..1) {
                        for (dy in 0..1) {
                            val subTileX = subtileX + dx
                            val subTileY = subtileY + dy

                            // Check if this subtile intersects viewport
                            val subtileBounds = getTileBounds(subTileX, subTileY, subtileZoom)
                            val subtileIntersects = tileIntersectsViewport(subtileBounds, bounds)
                            Logger.log("WIDGET", LogLevel.DEBUG, "Subtile ($subTileX,$subTileY) bounds: N=${subtileBounds.north}, S=${subtileBounds.south}, E=${subtileBounds.east}, W=${subtileBounds.west}")
                            Logger.log("WIDGET", LogLevel.DEBUG, "Subtile ($subTileX,$subTileY) intersects: $subtileIntersects")

                            if (subtileIntersects) {
                                visibleSubtiles.add(TileCoordinate(subtileZoom, subTileX, subTileY))
                                Logger.log("WIDGET", LogLevel.DEBUG, "Added visible subtile ($subTileX,$subTileY) at zoom $subtileZoom")
                            }
                        }
                    }
                }
            }
        }

        Logger.log("WIDGET", LogLevel.INFO, "Found ${visibleSubtiles.size} visible subtiles")
        return visibleSubtiles

    } catch (e: Exception) {
        Logger.log("WIDGET", LogLevel.ERROR, "Failed to find visible subtiles: ${e.message}", e)
        return emptyList()
    }
}

private fun getTileBounds(tileX: Int, tileY: Int, zoom: Int): ViewportBounds {
    // Convert tile coordinates to lat/lng bounds
    val n = tileYToLatitude(tileY, zoom)
    val s = tileYToLatitude(tileY + 1, zoom)
    val w = tileXToLongitude(tileX, zoom)
    val e = tileXToLongitude(tileX + 1, zoom)

    return ViewportBounds(n, s, e, w)
}

private fun tileIntersectsViewport(tileBounds: ViewportBounds, viewportBounds: ViewportBounds): Boolean {
    // Check if tile bounds intersect with viewport bounds
    return !(tileBounds.west > viewportBounds.east ||
             tileBounds.east < viewportBounds.west ||
             tileBounds.south > viewportBounds.north ||
             tileBounds.north < viewportBounds.south)
}

private fun tileXToLongitude(tileX: Int, zoom: Int): Double {
    return (tileX.toDouble() / (1 shl zoom)) * 360.0 - 180.0
}

private fun tileYToLatitude(tileY: Int, zoom: Int): Double {
    val n = Math.PI - (2.0 * Math.PI * tileY) / Math.pow(2.0, zoom.toDouble())
    return Math.toDegrees(Math.atan(Math.sinh(n)))
}



private fun fetchTilesOnlineSync(context: android.content.Context, tiles: List<TileCoordinate>): AssemblyResult? {
        try {
            if (tiles.isEmpty()) {
                Logger.log("WIDGET", LogLevel.DEBUG, "No tiles to fetch online")
                return null
            }

            Logger.log("WIDGET", LogLevel.DEBUG, "Fetching ${tiles.size} tiles online")

            // Find bounds of all tiles to determine canvas size
            val minX = tiles.minOf { it.x }
            val maxX = tiles.maxOf { it.x }
            val minY = tiles.minOf { it.y }
            val maxY = tiles.maxOf { it.y }

            val tileSize = 256
            val width = (maxX - minX + 1) * tileSize
            val height = (maxY - minY + 1) * tileSize

            Logger.log("WIDGET", LogLevel.DEBUG, "Online fetch canvas size: ${width}x${height} for tiles x=$minX..$maxX, y=$minY..$maxY")

            // Create canvas
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint()

            val successfulTiles = mutableListOf<TileCoordinate>()

            // Fetch each tile online
            for (tile in tiles) {
                val pixelX = (tile.x - minX) * tileSize
                val pixelY = (tile.y - minY) * tileSize

                // Try to fetch the tile online
                val tileBitmap = fetchOnlineTile(context, tile.zoom, tile.x, tile.y)

                if (tileBitmap != null) {
                    canvas.drawBitmap(tileBitmap, pixelX.toFloat(), pixelY.toFloat(), paint)
                    // Add 50% opacity black overlay
                    val overlayPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(128, 0, 0, 0) // 50% opacity black
                    }
                    canvas.drawRect(pixelX.toFloat(), pixelY.toFloat(), pixelX.toFloat() + tileSize, pixelY.toFloat() + tileSize, overlayPaint)
                    tileBitmap.recycle()
                    successfulTiles.add(tile)
                    Logger.log("WIDGET", LogLevel.DEBUG, "Fetched and placed online tile (${tile.x},${tile.y}) at ($pixelX,$pixelY)")
                } else {
                    // Create black placeholder for failed fetches
                    val blackBitmap = android.graphics.Bitmap.createBitmap(tileSize, tileSize, android.graphics.Bitmap.Config.ARGB_8888)
                    val blackCanvas = android.graphics.Canvas(blackBitmap)
                    val blackPaint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
                    blackCanvas.drawRect(0f, 0f, tileSize.toFloat(), tileSize.toFloat(), blackPaint)

                    canvas.drawBitmap(blackBitmap, pixelX.toFloat(), pixelY.toFloat(), paint)
                    // Add 50% opacity black overlay (already black, but consistent)
                    val overlayPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(128, 0, 0, 0) // 50% opacity black
                    }
                    canvas.drawRect(pixelX.toFloat(), pixelY.toFloat(), pixelX.toFloat() + tileSize, pixelY.toFloat() + tileSize, overlayPaint)
                    blackBitmap.recycle()
                    Logger.log("WIDGET", LogLevel.DEBUG, "Online tile (${tile.x},${tile.y}) failed, using black placeholder")
                }
            }

            Logger.log("WIDGET", LogLevel.INFO, "Fetched ${successfulTiles.size}/${tiles.size} tiles online successfully")
            return AssemblyResult(bitmap, successfulTiles)

        } catch (e: Exception) {
            Logger.log("WIDGET", LogLevel.ERROR, "Failed to fetch tiles online: ${e.message}", e)
            return null
        }
}

private fun fetchOnlineTile(context: android.content.Context, zoom: Int, x: Int, y: Int): android.graphics.Bitmap? {
        try {
            // Use OpenTopoMap - free terrain tiles, no API key required
            val directUrl = "https://tile.opentopomap.org/$zoom/$x/$y.png"
            val proxyUrl = "https://edl-proxy.gabriel-briffe.workers.dev/?url=$directUrl"
            Logger.log("WIDGET", LogLevel.DEBUG, "Fetching online tile: $proxyUrl")

            val connection = java.net.URL(proxyUrl).openConnection() as java.net.HttpURLConnection
            // Set proper headers for tile providers
            connection.setRequestProperty("User-Agent", "MountainCircles-Map/1.0 (gabriel@briffe.fr)")
            connection.setRequestProperty("Referer", "https://mountaincircles.app")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            Logger.log("WIDGET", LogLevel.DEBUG, "Online tile response: $responseCode for $proxyUrl")

            if (responseCode == 200) {
                val inputStream = connection.inputStream
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                connection.disconnect()

                if (bitmap != null) {
                    Logger.log("WIDGET", LogLevel.DEBUG, "Successfully decoded online tile: ${bitmap.width}x${bitmap.height}")
                } else {
                    Logger.log("WIDGET", LogLevel.WARN, "Failed to decode online tile bitmap")
                }

                return bitmap
            } else {
                Logger.log("WIDGET", LogLevel.WARN, "Online tile fetch failed with response code: $responseCode")
                connection.disconnect()
                return null
            }

        } catch (e: Exception) {
            Logger.log("WIDGET", LogLevel.ERROR, "Exception fetching online tile: ${e.message}", e)
            return null
        }
}

/**
 * Capture the center point of the map for Windy meteogram widget
 */
actual suspend fun captureWindyMeteogramPoint(context: Any?) {
    try {
        val androidContext = context as android.content.Context

        // Get current camera position from global state
        val globalState = org.mountaincircles.app.state.getGlobalState()
        val cameraState = globalState.currentCameraState.value

        Logger.log("WIDGET", LogLevel.DEBUG, "Camera state: $cameraState")

        // Extract position from camera state
        if (cameraState == null) {
            Logger.log("WIDGET", LogLevel.ERROR, "No camera state available - cannot capture meteogram point")
            throw IllegalStateException("Camera state not available - cannot capture meteogram point")
        }

        Logger.log("WIDGET", LogLevel.INFO, "Using current camera center position")
        val position = cameraState.position
        val latitude = position.target.latitude
        val longitude = position.target.longitude

        // Fetch location name from OSM Nominatim
        val locationName = fetchLocationName(latitude, longitude)

        // Check if SkySight should be included
        val prefs = androidContext.getSharedPreferences("windy_widget_prefs", Context.MODE_PRIVATE)
        val includeSkySight = prefs.getBoolean("include_skysight_in_meteogram", false)

        // Get SkySight credentials if needed
        var skysightEmail: String? = null
        var skysightPassword: String? = null
        var skysightRegion: String? = null

        if (includeSkySight) {
            // Get SkySight state from global state
            val globalState = org.mountaincircles.app.state.getGlobalState()
            val skySightModule = globalState.moduleManager.getModule("skysight") as? org.mountaincircles.app.modules.skysight.SkysightModule
            val skySightState = skySightModule?.skysightState?.value

            if (skySightState != null && skySightState.email.isNotEmpty() && skySightState.password.isNotEmpty()) {
                skysightEmail = skySightState.email
                skysightPassword = skySightState.password
                skysightRegion = skySightState.selectedRegion.takeIf { it.isNotEmpty() } ?: "EUROPE"
                Logger.log("WIDGET", LogLevel.INFO, "Including SkySight credentials in meteogram metadata for region: $skysightRegion")
            } else {
                Logger.log("WIDGET", LogLevel.WARN, "SkySight inclusion requested but no valid credentials found")
            }
        }

        // Store Windy meteogram metadata with location name and optional SkySight credentials
        val windyMetadata = WindyMeteogramMetadata(
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            skysightEmail = skysightEmail,
            skysightPassword = skysightPassword,
            skysightRegion = skysightRegion
        )

        val jsonString = Json.encodeToString(windyMetadata)
        prefs.edit().putString("windy_meteogram_metadata", jsonString).apply()

        Logger.log("WIDGET", LogLevel.INFO, "Windy meteogram point captured: ${windyMetadata.latitude}, ${windyMetadata.longitude}, location: $locationName")

        // For now, just log that we captured the point
        Logger.log("WIDGET", LogLevel.INFO, "Windy meteogram widget data saved - widget provider not yet implemented")

    } catch (e: Exception) {
        Logger.log("WIDGET", LogLevel.ERROR, "Failed to capture Windy meteogram point: ${e.message}", e)
        throw e
    }
}


/**
 * Metadata for Windy meteogram widget configuration
 */
@Serializable
@JsonIgnoreUnknownKeys
data class WindyMeteogramMetadata(
    val latitude: Double,
    val longitude: Double,
    val locationName: String? = null,
    val skysightEmail: String? = null,
    val skysightPassword: String? = null,
    val skysightRegion: String? = null,
    val thermalWindow: Int? = 100, // Thermal data fetch radius in km (default 100km)
    val waveWindow: Int? = 200, // Wave data fetch radius in km (default 200km)
    val pfdtotMaxByDay: Map<Int, Float>? = null, // Day offset -> pfdtot max value
    val hwcritMaxByDay: Map<Int, Float>? = null, // Day offset -> hwcrit max value at 14:00
    val wstarBsratioMaxByDay: Map<Int, Float>? = null, // Day offset -> wstar_bsratio max value at 14:00
    val w4000MaxByDayAndHour: Map<String, Float>? = null, // "dayOffset_hour" -> w_4000 max value at specific hour
    val pfdtotColorStops: List<org.mountaincircles.app.modules.skysight.logic.data.LayerColorStop>? = null,
    val hwcritColorStops: List<org.mountaincircles.app.modules.skysight.logic.data.LayerColorStop>? = null,
    val wstarBsratioColorStops: List<org.mountaincircles.app.modules.skysight.logic.data.LayerColorStop>? = null,
    val w4000ColorStops: List<org.mountaincircles.app.modules.skysight.logic.data.LayerColorStop>? = null
)

/**
 * Handle storing/removing SkySight credentials in widget metadata based on checkbox state
 */
actual suspend fun handleSkySightCredentialsForWidget(context: Any?, includeSkySight: Boolean, skySightState: org.mountaincircles.app.modules.skysight.logic.data.SkysightState?) {
    try {
        val androidContext = context as android.content.Context
        val prefs = androidContext.getSharedPreferences("windy_widget_prefs", android.content.Context.MODE_PRIVATE)

        // Store the SkySight inclusion preference in shared preferences
        prefs.edit()
            .putBoolean("include_skysight_in_meteogram", includeSkySight)
            .apply()

        Logger.log("WIDGET", LogLevel.INFO, "📊 Updated SkySight inclusion preference: ${if (includeSkySight) "ENABLED" else "DISABLED"}")

        // Immediately update the widget metadata with or without SkySight credentials
        val metadataJson = prefs.getString("windy_meteogram_metadata", null)
        if (metadataJson != null) {
            try {
                var metadata = Json.decodeFromString<WindyMeteogramMetadata>(metadataJson)

                if (includeSkySight && skySightState != null) {
                    // Add SkySight credentials to metadata
                    val emailPresent = skySightState.email.isNotEmpty()
                    val passwordPresent = skySightState.password.isNotEmpty()

                    if (emailPresent && passwordPresent) {
                        metadata = metadata.copy(
                            skysightEmail = skySightState.email,
                            skysightPassword = skySightState.password,
                            skysightRegion = skySightState.selectedRegion.takeIf { it.isNotEmpty() } ?: "EUROPE"
                        )
                        Logger.log("WIDGET", LogLevel.INFO, "✅ SkySight credentials added to widget metadata")
                        Logger.log("WIDGET", LogLevel.DEBUG, "📧 Email: ${skySightState.email.take(3)}..., Region: ${metadata.skysightRegion}")
                    } else {
                        Logger.log("WIDGET", LogLevel.WARN, "⚠️ SkySight enabled but credentials incomplete - Email: $emailPresent, Password: $passwordPresent")
                    }
                } else {
                    // Remove SkySight credentials from metadata
                    metadata = metadata.copy(
                        skysightEmail = null,
                        skysightPassword = null,
                        skysightRegion = null,
                        pfdtotMaxByDay = null,
                        hwcritMaxByDay = null,
                        wstarBsratioMaxByDay = null,
                        w4000MaxByDayAndHour = null,
                        pfdtotColorStops = null,
                        hwcritColorStops = null,
                        wstarBsratioColorStops = null,
                        w4000ColorStops = null
                    )
                    Logger.log("WIDGET", LogLevel.INFO, "🗑️ SkySight credentials and data removed from widget metadata")
                    Logger.log("WIDGET", LogLevel.DEBUG, "📊 Metadata after removal - Email: ${metadata.skysightEmail}, Password: ${metadata.skysightPassword}, Region: ${metadata.skysightRegion}")
                }

                // Save updated metadata
                val updatedJson = Json.encodeToString(metadata)
                prefs.edit()
                    .putString("windy_meteogram_metadata", updatedJson)
                    .apply()

                Logger.log("WIDGET", LogLevel.INFO, "💾 Widget metadata updated with SkySight ${if (includeSkySight) "credentials" else "removal"}")

            } catch (e: Exception) {
                Logger.log("WIDGET", LogLevel.ERROR, "❌ Failed to update widget metadata: ${e.message}", e)
            }
        } else {
            Logger.log("WIDGET", LogLevel.DEBUG, "📄 No existing widget metadata to update")
        }

        // Log what credentials are available
        if (skySightState != null) {
            val emailPresent = skySightState.email.isNotEmpty()
            val passwordPresent = skySightState.password.isNotEmpty()
            val isLoggedIn = skySightState.isLoggedIn

            Logger.log("WIDGET", LogLevel.DEBUG, "📊 SkySight state - Email present: $emailPresent, Password present: $passwordPresent, Logged in: $isLoggedIn")
        } else {
            Logger.log("WIDGET", LogLevel.DEBUG, "📊 SkySight state not available")
        }
    } catch (e: Exception) {
        Logger.log("WIDGET", LogLevel.ERROR, "❌ Failed to handle SkySight credentials for widget: ${e.message}", e)
    }
}

/**
 * Check if SkySight credentials exist in the widget metadata
 */
actual suspend fun checkSkySightCredentialsInWidgetMetadata(context: Any?): Boolean {
    try {
        val androidContext = context as android.content.Context
        val prefs = androidContext.getSharedPreferences("windy_widget_prefs", android.content.Context.MODE_PRIVATE)

        val metadataJson = prefs.getString("windy_meteogram_metadata", null)
        if (metadataJson != null) {
            val metadata = Json.decodeFromString<WindyMeteogramMetadata>(metadataJson)
            val hasCredentials = !metadata.skysightEmail.isNullOrBlank() &&
                               !metadata.skysightPassword.isNullOrBlank() &&
                               !metadata.skysightRegion.isNullOrBlank()

            Logger.log("WIDGET", LogLevel.DEBUG, "📊 Checked metadata - SkySight credentials ${if (hasCredentials) "present" else "not present"}")
            return hasCredentials
        } else {
            Logger.log("WIDGET", LogLevel.DEBUG, "📊 No widget metadata found")
            return false
        }
    } catch (e: Exception) {
        Logger.log("WIDGET", LogLevel.ERROR, "❌ Failed to check SkySight credentials in metadata: ${e.message}", e)
        return false
    }
}

/**
 * Get SkySight window values from widget metadata
 */
actual suspend fun getSkySightWindowValues(context: Any?): Pair<Int, Int> {
    try {
        val androidContext = context as android.content.Context
        val prefs = androidContext.getSharedPreferences("windy_widget_prefs", android.content.Context.MODE_PRIVATE)

        val metadataJson = prefs.getString("windy_meteogram_metadata", null)
        if (metadataJson != null) {
            val metadata = Json.decodeFromString<WindyMeteogramMetadata>(metadataJson)
            val thermalWindow = metadata.thermalWindow ?: 100
            val waveWindow = metadata.waveWindow ?: 200

            Logger.log("WIDGET", LogLevel.DEBUG, "📏 Retrieved window values - Thermal: ${thermalWindow}km, Wave: ${waveWindow}km")
            return Pair(thermalWindow, waveWindow)
        } else {
            Logger.log("WIDGET", LogLevel.DEBUG, "📏 No widget metadata found, using defaults")
            return Pair(100, 200) // Default values
        }
    } catch (e: Exception) {
        Logger.log("WIDGET", LogLevel.ERROR, "❌ Failed to get SkySight window values: ${e.message}", e)
        return Pair(100, 200) // Default values on error
    }
}

/**
 * Save SkySight window values to widget metadata
 */
actual suspend fun saveSkySightWindowValues(context: Any?, thermalWindow: Int, waveWindow: Int) {
    try {
        val androidContext = context as android.content.Context
        val prefs = androidContext.getSharedPreferences("windy_widget_prefs", android.content.Context.MODE_PRIVATE)

        val metadataJson = prefs.getString("windy_meteogram_metadata", null)
        if (metadataJson != null) {
            var metadata = Json.decodeFromString<WindyMeteogramMetadata>(metadataJson)
            metadata = metadata.copy(
                thermalWindow = thermalWindow,
                waveWindow = waveWindow
            )

            val updatedJson = Json.encodeToString(metadata)
            prefs.edit()
                .putString("windy_meteogram_metadata", updatedJson)
                .apply()

            Logger.log("WIDGET", LogLevel.INFO, "💾 Updated SkySight window values - Thermal: ${thermalWindow}km, Wave: ${waveWindow}km")
        } else {
            Logger.log("WIDGET", LogLevel.DEBUG, "📏 No widget metadata found to update window values")
        }
    } catch (e: Exception) {
        Logger.log("WIDGET", LogLevel.ERROR, "❌ Failed to save SkySight window values: ${e.message}", e)
    }
}

/**
 * Get location info from widget metadata
 */
actual suspend fun getWidgetLocationInfo(context: Any?): Pair<String?, Pair<Double, Double>?> {
    try {
        val androidContext = context as android.content.Context
        val prefs = androidContext.getSharedPreferences("windy_widget_prefs", android.content.Context.MODE_PRIVATE)

        val metadataJson = prefs.getString("windy_meteogram_metadata", null)
        if (metadataJson != null) {
            val metadata = Json.decodeFromString<WindyMeteogramMetadata>(metadataJson)
            val locationName = metadata.locationName
            val coords = Pair(metadata.latitude, metadata.longitude)

            Logger.log("WIDGET", LogLevel.DEBUG, "📍 Retrieved location info - Name: $locationName, Coords: ${coords.first}, ${coords.second}")
            return Pair(locationName, coords)
        } else {
            Logger.log("WIDGET", LogLevel.DEBUG, "📍 No widget metadata found for location info")
            return Pair(null, null)
        }
    } catch (e: Exception) {
        Logger.log("WIDGET", LogLevel.ERROR, "❌ Failed to get widget location info: ${e.message}", e)
        return Pair(null, null)
    }
}
