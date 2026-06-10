package org.mountaincircles.app.offline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.maplibre.spatialk.geojson.Position
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.GeoBounds
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.state.OfflineRegionConfig
import org.mountaincircles.app.state.OfflineSelectionPhase
import kotlin.math.abs

actual val isOfflineRegionDownloadSupported: Boolean = true

@Composable
actual fun OfflineRegionUi(globalState: GlobalState) {
    val phase by globalState.offlineSelectionPhase.collectAsState()
    val downloadState by globalState.offlineDownloadState.collectAsState()
    val cameraState by globalState.currentCameraState.collectAsState()

    if (phase == OfflineSelectionPhase.Idle && downloadState.isComplete && downloadState.statusMessage.isNotEmpty()) {
        // Brief completion toast handled via bottom bar during transition; no persistent UI
        return
    }

    when (phase) {
        OfflineSelectionPhase.Drawing -> {
            OfflineRegionDrawingOverlay(
                cameraState = cameraState,
                onRegionSelected = { bounds ->
                    Logger.log("OFFLINE", LogLevel.INFO, "Region selected: $bounds")
                    globalState.setOfflinePreviewBounds(bounds)
                },
                onCancel = { globalState.cancelOfflineRegionSelection() },
            )
        }
        OfflineSelectionPhase.Preview -> {
            OfflineRegionConfirmBar(
                onSave = { globalState.confirmOfflineRegionDownload() },
                onRedraw = { globalState.redrawOfflineRegion() },
                onCancel = { globalState.cancelOfflineRegionSelection() },
            )
        }
        OfflineSelectionPhase.Downloading -> {
            Box(modifier = Modifier.fillMaxSize()) {
                OfflineDownloadDebugPanel(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.52f),
                )
                OfflineRegionProgressBar(
                    statusMessage = downloadState.statusMessage,
                    completed = downloadState.completedResources,
                    required = downloadState.requiredResources,
                    error = downloadState.error,
                    onDismiss = { globalState.cancelOfflineRegionSelection() },
                )
            }
        }
        OfflineSelectionPhase.Idle -> Unit
    }
}

@Composable
private fun OfflineRegionDrawingOverlay(
    cameraState: org.maplibre.compose.camera.CameraState?,
    onRegionSelected: (GeoBounds) -> Unit,
    onCancel: () -> Unit,
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    val minDragPx = with(LocalDensity.current) { 24.dp.toPx() }
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cameraState) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStart = offset
                            dragCurrent = offset
                        },
                        onDrag = { change, _ ->
                            dragCurrent = change.position
                        },
                        onDragEnd = {
                            val start = dragStart
                            val end = dragCurrent
                            dragStart = null
                            dragCurrent = null

                            if (start == null || end == null) return@detectDragGestures
                            if (abs(end.x - start.x) < minDragPx || abs(end.y - start.y) < minDragPx) return@detectDragGestures

                            val projection = cameraState?.projection ?: run {
                                Logger.log("OFFLINE", LogLevel.WARN, "Camera projection unavailable during region draw")
                                return@detectDragGestures
                            }

                            val posStart = projection.positionFromScreenLocation(start.toDpOffset(density))
                            val posEnd = projection.positionFromScreenLocation(end.toDpOffset(density))
                            val bounds = geoBoundsFromPositions(posStart, posEnd)
                            onRegionSelected(bounds)
                        },
                        onDragCancel = {
                            dragStart = null
                            dragCurrent = null
                        },
                    )
                },
        )

        val rect = dragRect(dragStart, dragCurrent)
        if (rect != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = Color(0x402196F3),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
                drawRect(
                    color = Color(0xFF2196F3),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 3f),
                )
            }
        }

        OfflineRegionHintBar(
            message = "Drag on the map to select an area (zoom 0–${OfflineRegionConfig.MAX_ZOOM})",
            showCancel = true,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun OfflineRegionConfirmBar(
    onSave: () -> Unit,
    onRedraw: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color(0xE6000000), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Store this region offline?",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "OSM + Mapterhorn tiles, zoom 0–${OfflineRegionConfig.MAX_ZOOM}. Other areas still stream when online.",
                color = Color.LightGray,
                fontSize = 12.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                OutlinedButton(onClick = onRedraw, modifier = Modifier.weight(1f)) {
                    Text("Redraw")
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun OfflineRegionProgressBar(
    statusMessage: String,
    completed: Long,
    required: Long,
    error: String?,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color(0xE6000000), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = error ?: statusMessage,
                color = if (error != null) Color(0xFFFF8A80) else Color.White,
                fontSize = 14.sp,
            )
            if (error == null && required > 0) {
                LinearProgressIndicator(
                    progress = { completed.toFloat() / required.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF2196F3),
                )
            }
            if (error != null) {
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun OfflineRegionHintBar(
    message: String,
    showCancel: Boolean,
    onCancel: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            if (showCancel) {
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun Offset.toDpOffset(density: androidx.compose.ui.unit.Density): DpOffset =
    with(density) { DpOffset(x.toDp(), y.toDp()) }

private fun dragRect(start: Offset?, current: Offset?): Rect? {
    if (start == null || current == null) return null
    return Rect(
        left = minOf(start.x, current.x),
        top = minOf(start.y, current.y),
        right = maxOf(start.x, current.x),
        bottom = maxOf(start.y, current.y),
    )
}

private fun geoBoundsFromPositions(a: Position, b: Position): GeoBounds {
    val west = minOf(a.longitude, b.longitude)
    val east = maxOf(a.longitude, b.longitude)
    val south = minOf(a.latitude, b.latitude)
    val north = maxOf(a.latitude, b.latitude)
    return GeoBounds(west = west, south = south, east = east, north = north)
}
