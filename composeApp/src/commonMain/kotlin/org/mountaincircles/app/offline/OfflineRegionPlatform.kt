package org.mountaincircles.app.offline

import androidx.compose.runtime.Composable
import org.mountaincircles.app.state.GlobalState

/** Whether offline region download UI is available on this platform. */
expect val isOfflineRegionDownloadSupported: Boolean

/** Map-attached offline pack controller (must run inside [org.maplibre.compose.map.MaplibreMap]). */
@Composable
expect fun OfflineRegionMapContent(globalState: GlobalState)

/** Full-screen drag overlay and download progress bar over the map. */
@Composable
expect fun OfflineRegionUi(globalState: GlobalState)
