package org.mountaincircles.app.offline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflineManagerException
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.rememberOfflineManager
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.GeoBounds
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.state.OfflineDownloadUiState
import org.mountaincircles.app.state.OfflineRegionConfig
import org.mountaincircles.app.state.OfflineSelectionPhase

@Composable
actual fun OfflineRegionMapContent(globalState: GlobalState) {
    val previewBounds by globalState.offlinePreviewBounds.collectAsState()
    val downloadRequest by globalState.offlineDownloadRequest.collectAsState()

    OfflineRegionPreviewLayer(previewBounds)

    if (downloadRequest != null) {
        OfflineRegionDownloadController(
            globalState = globalState,
            bounds = downloadRequest!!,
        )
    }
}

@Composable
private fun OfflineRegionPreviewLayer(bounds: GeoBounds?) {
    if (bounds == null) return

    val geoJson = remember(bounds) { boundsToGeoJsonFeature(bounds) }
    val source = rememberGeoJsonSource(data = GeoJsonData.JsonString(geoJson))

    FillLayer(
        id = "offline-region-preview-fill",
        source = source,
        color = const(Color(0x402196F3)),
        opacity = const(0.5f),
    )
    LineLayer(
        id = "offline-region-preview-outline",
        source = source,
        color = const(Color(0xFF2196F3)),
        width = const(2.dp),
        opacity = const(1f),
    )
}

@Composable
private fun OfflineRegionDownloadController(
    globalState: GlobalState,
    bounds: GeoBounds,
) {
    val offlineManager = rememberOfflineManager()

    LaunchedEffect(bounds) {
        OfflineDownloadHttpTracker.beginSession()
        try {
            ensureBasemapStyleFile()
            val styleUrl = getBasemapStyleUrl()
            val polygon = bounds.toPolygon()
            val definition = OfflinePackDefinition.Shape(
                styleUrl = styleUrl,
                shape = polygon,
                minZoom = OfflineRegionConfig.MIN_ZOOM,
                maxZoom = OfflineRegionConfig.MAX_ZOOM,
            )

            Logger.log("OFFLINE", LogLevel.INFO, "Creating offline pack for $bounds (z${OfflineRegionConfig.MIN_ZOOM}–${OfflineRegionConfig.MAX_ZOOM})")

            val pack = offlineManager.create(definition, byteArrayOf())
            offlineManager.resume(pack)

            snapshotFlow { pack.downloadProgress }.collect { progress ->
                when (progress) {
                    is DownloadProgress.Healthy -> {
                        val pct = if (progress.requiredResourceCount > 0) {
                            (progress.completedResourceCount * 100 / progress.requiredResourceCount).toInt()
                        } else {
                            0
                        }
                        globalState.updateOfflineDownloadState(
                            OfflineDownloadUiState(
                                completedResources = progress.completedResourceCount,
                                requiredResources = progress.requiredResourceCount,
                                statusMessage = when (progress.status) {
                                    DownloadStatus.Downloading -> "Downloading… $pct%"
                                    DownloadStatus.Complete -> "Download complete"
                                    DownloadStatus.Paused -> "Paused"
                                },
                                isComplete = progress.status == DownloadStatus.Complete,
                            )
                        )
                        if (progress.status == DownloadStatus.Complete) {
                            Logger.log("OFFLINE", LogLevel.INFO, "Offline pack download complete")
                            globalState.completeOfflineRegionDownload()
                        }
                    }
                    is DownloadProgress.Error -> {
                        Logger.log("OFFLINE", LogLevel.ERROR, "Offline download error: ${progress.reason} - ${progress.message}")
                        globalState.updateOfflineDownloadState(
                            OfflineDownloadUiState(
                                error = progress.message ?: progress.reason,
                                statusMessage = "Download failed",
                            )
                        )
                    }
                    is DownloadProgress.TileLimitExceeded -> {
                        Logger.log("OFFLINE", LogLevel.ERROR, "Tile limit exceeded: ${progress.limit}")
                        globalState.updateOfflineDownloadState(
                            OfflineDownloadUiState(
                                error = "Tile limit exceeded (${progress.limit}). Try a smaller area.",
                                statusMessage = "Download failed",
                            )
                        )
                    }
                    is DownloadProgress.Unknown -> {
                        globalState.updateOfflineDownloadState(
                            OfflineDownloadUiState(statusMessage = "Downloading…")
                        )
                    }
                }
            }
        } catch (e: OfflineManagerException) {
            Logger.log("OFFLINE", LogLevel.ERROR, "OfflineManager error: ${e.message}", e)
            globalState.updateOfflineDownloadState(
                OfflineDownloadUiState(error = e.message ?: "Offline download failed", statusMessage = "Download failed")
            )
        } catch (e: Exception) {
            Logger.log("OFFLINE", LogLevel.ERROR, "Offline download failed: ${e.message}", e)
            globalState.updateOfflineDownloadState(
                OfflineDownloadUiState(error = e.message ?: "Offline download failed", statusMessage = "Download failed")
            )
        } finally {
            OfflineDownloadHttpTracker.endSession()
        }
    }
}

private fun boundsToGeoJsonFeature(bounds: GeoBounds): String = """
{
    "type": "Feature",
    "properties": {},
    "geometry": {
        "type": "Polygon",
        "coordinates": [[
            [${bounds.west}, ${bounds.south}],
            [${bounds.east}, ${bounds.south}],
            [${bounds.east}, ${bounds.north}],
            [${bounds.west}, ${bounds.north}],
            [${bounds.west}, ${bounds.south}]
        ]]
    }
}
""".trimIndent()
