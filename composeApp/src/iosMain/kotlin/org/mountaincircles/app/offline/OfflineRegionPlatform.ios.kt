package org.mountaincircles.app.offline

import androidx.compose.runtime.Composable
import org.mountaincircles.app.state.GlobalState

actual val isOfflineRegionDownloadSupported: Boolean = false

@Composable
actual fun OfflineRegionMapContent(globalState: GlobalState) = Unit

@Composable
actual fun OfflineRegionUi(globalState: GlobalState) = Unit
