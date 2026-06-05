package org.mountaincircles.app.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Android implementation of configuration utilities
 */

/**
 * Cross-platform screen configuration (Android implementation)
 */
actual object ScreenConfiguration {
    actual val screenWidthDp: Int = 360 // Default Android width
    actual val screenHeightDp: Int = 640 // Default Android height
}

/**
 * Get screen width in Dp (Android implementation)
 */
internal actual fun getScreenWidthDpInternal(): Int = ScreenConfiguration.screenWidthDp

/**
 * Get screen height in Dp (Android implementation)
 */
internal actual fun getScreenHeightDpInternal(): Int = ScreenConfiguration.screenHeightDp

/**
 * Get screen width in Dp (Android implementation)
 */
actual fun getScreenWidthDp(): Dp = getScreenWidthDpInternal().dp

/**
 * Get screen height in Dp (Android implementation)
 */
actual fun getScreenHeightDp(): Dp = getScreenHeightDpInternal().dp