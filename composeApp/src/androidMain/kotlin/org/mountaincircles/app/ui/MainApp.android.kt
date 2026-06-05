package org.mountaincircles.app.ui

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import org.mountaincircles.app.state.GlobalState

/**
 * Android implementation of screen dimensions initialization
 */
@Composable
actual fun ScreenDimensionsInitializer(globalState: GlobalState) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay

            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)

            // Convert pixels to millimeters
            val mmPerInch = 25.4
            val screenWidthMm = metrics.widthPixels.toDouble() / metrics.xdpi * mmPerInch
            val screenHeightMm = metrics.heightPixels.toDouble() / metrics.ydpi * mmPerInch

            globalState.updateScreenDimensions(screenWidthMm, screenHeightMm)

        } catch (e: Exception) {
            // Fallback to default dimensions if measurement fails
            globalState.updateScreenDimensions(65.0, 115.0) // Typical phone dimensions
        }
    }
}
