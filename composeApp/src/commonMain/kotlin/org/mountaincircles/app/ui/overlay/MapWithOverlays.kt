package org.mountaincircles.app.ui.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.ui.MainMapComposable

/**
 * Wrapper composable that adds map overlays to the main map view
 * Overlays render behind the main map and its UI elements (including sidebar)
 * This provides a safe way to integrate overlays without modifying the core MainMapComposable
 */
@Composable
fun MapWithOverlays(
    globalState: GlobalState,
    locationProvider: org.maplibre.compose.location.LocationProvider?,
    locationState: org.maplibre.compose.location.UserLocationState?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Main map composable with integrated overlay container
        // Overlays are now handled directly within MainMapComposable
        MainMapComposable(globalState, locationProvider, locationState)
    }
}
