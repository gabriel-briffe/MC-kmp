package org.mountaincircles.app.ui.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Container component for map overlays with proper positioning
 */
@Composable
fun MapOverlayContainer(
    position: OverlayPosition,
    priority: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = position.toAlignment()
    ) {
        content()
    }
}
