package org.mountaincircles.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.mountaincircles.app.state.GlobalState

/**
 * iOS implementation of screen dimensions initialization
 */
@Composable
actual fun ScreenDimensionsInitializer(globalState: GlobalState) {
    LaunchedEffect(Unit) {
        // Default iOS screen dimensions (can be improved with actual iOS API)
        globalState.updateScreenDimensions(65.0, 115.0) // Typical iOS device dimensions
    }
}
