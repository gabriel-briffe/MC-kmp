package org.mountaincircles.app.offline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
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
import org.mountaincircles.app.ui.map.BasemapStyle

private const val CREATE_PACK_TIMEOUT_MS = 90_000L

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
            val styleUrl = BasemapStyle.SHARED_STYLE_URL
            OfflineDownloadHttpTracker.logInfo("style: $styleUrl")

            val definition = OfflinePackDefinition.TilePyramid(
                styleUrl = styleUrl,
                bounds = bounds.toBoundingBox(),
                minZoom = OfflineRegionConfig.MIN_ZOOM,
                maxZoom = OfflineRegionConfig.MAX_ZOOM,
            )

            Logger.log("OFFLINE", LogLevel.INFO, "Creating offline pack for $bounds (z${OfflineRegionConfig.MIN_ZOOM}–${OfflineRegionConfig.MAX_ZOOM})")
            OfflineDownloadHttpTracker.logInfo(
                "region z${OfflineRegionConfig.MIN_ZOOM}–${OfflineRegionConfig.MAX_ZOOM} " +
                    "[${bounds.west},${bounds.south} → ${bounds.east},${bounds.north}]",
            )
            OfflineDownloadHttpTracker.logInfo("creating offline pack…")

            val pack = withTimeout(CREATE_PACK_TIMEOUT_MS) {
                offlineManager.create(definition, byteArrayOf())
            }
            OfflineDownloadHttpTracker.logInfo("pack created, starting download")
            OfflineDownloadHttpTracker.enableHttpTracing()

            offlineManager.resume(pack)

            var lastLoggedProgress: Pair<Long, Long>? = null
            snapshotFlow { pack.downloadProgress }.collect { progress ->
                when (progress) {
                    is DownloadProgress.Healthy -> {
                        val pct = if (progress.requiredResourceCount > 0) {
                            (progress.completedResourceCount * 100 / progress.requiredResourceCount).toInt()
                        } else {
                            0
                        }
                        val progressKey = progress.completedResourceCount to progress.requiredResourceCount
                        if (lastLoggedProgress != progressKey) {
                            lastLoggedProgress = progressKey
                            if (progress.requiredResourceCount == 0L && progress.completedResourceCount == 0L) {
                                OfflineDownloadHttpTracker.logInfo("waiting for tile list (required=0 so far)…")
                            } else {
                                OfflineDownloadHttpTracker.logInfo(
                                    "progress ${progress.completedResourceCount}/${progress.requiredResourceCount} ($pct%) " +
                                        "tiles=${progress.completedTileCount} precise=${progress.isRequiredResourceCountPrecise}",
                                )
                            }
                        }
                        globalState.updateOfflineDownloadState(
                            OfflineDownloadUiState(
                                completedResources = progress.completedResourceCount,
                                requiredResources = progress.requiredResourceCount,
                                statusMessage = when (progress.status) {
                                    DownloadStatus.Downloading -> if (progress.requiredResourceCount > 0) {
                                        "Downloading… $pct%"
                                    } else {
                                        "Preparing tiles…"
                                    }
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
                        OfflineDownloadHttpTracker.logInfo("error: ${progress.reason} — ${progress.message}")
                        globalState.updateOfflineDownloadState(
                            OfflineDownloadUiState(
                                error = progress.message ?: progress.reason,
                                statusMessage = "Download failed",
                            )
                        )
                    }
                    is DownloadProgress.TileLimitExceeded -> {
                        Logger.log("OFFLINE", LogLevel.ERROR, "Tile limit exceeded: ${progress.limit}")
                        OfflineDownloadHttpTracker.logInfo("tile limit exceeded: ${progress.limit}")
                        globalState.updateOfflineDownloadState(
                            OfflineDownloadUiState(
                                error = "Tile limit exceeded (${progress.limit}). Try a smaller area.",
                                statusMessage = "Download failed",
                            )
                        )
                    }
                    is DownloadProgress.Unknown -> {
                        OfflineDownloadHttpTracker.logInfo("status unknown")
                        globalState.updateOfflineDownloadState(
                            OfflineDownloadUiState(statusMessage = "Preparing download…")
                        )
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            val message = "Timed out creating offline pack (style or region). Check style URL in trace."
            Logger.log("OFFLINE", LogLevel.ERROR, message, e)
            OfflineDownloadHttpTracker.logInfo(message)
            globalState.updateOfflineDownloadState(
                OfflineDownloadUiState(error = message, statusMessage = "Download failed")
            )
        } catch (e: OfflineManagerException) {
            Logger.log("OFFLINE", LogLevel.ERROR, "OfflineManager error: ${e.message}", e)
            OfflineDownloadHttpTracker.logInfo("offline manager: ${e.message}")
            globalState.updateOfflineDownloadState(
                OfflineDownloadUiState(error = e.message ?: "Offline download failed", statusMessage = "Download failed")
            )
        } catch (e: Exception) {
            Logger.log("OFFLINE", LogLevel.ERROR, "Offline download failed: ${e.message}", e)
            OfflineDownloadHttpTracker.logInfo("failed: ${e.message}")
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
            [${bounds.west}, ${bounds.north}],
            [${bounds.east}, ${bounds.north}],
            [${bounds.east}, ${bounds.south}],
            [${bounds.west}, ${bounds.south}]
        ]]
    }
}
""".trimIndent()
