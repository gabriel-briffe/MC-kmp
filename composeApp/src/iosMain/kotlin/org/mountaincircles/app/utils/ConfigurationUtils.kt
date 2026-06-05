package org.mountaincircles.app.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS implementation of configuration utilities
 */

/**
 * Cross-platform screen configuration (iOS implementation)
 * Note: iOS provides default values since LocalConfiguration is Android-specific
 */
actual object ScreenConfiguration {
    actual val screenWidthDp: Int = 360 // Default iPhone width
    actual val screenHeightDp: Int = 780 // Default iPhone height
}

/**
 * Get screen width in Dp (iOS implementation)
 */
internal actual fun getScreenWidthDpInternal(): Int = ScreenConfiguration.screenWidthDp

/**
 * Get screen height in Dp (iOS implementation)
 */
internal actual fun getScreenHeightDpInternal(): Int = ScreenConfiguration.screenHeightDp

/**
 * Get screen width in Dp (iOS implementation)
 */
actual fun getScreenWidthDp(): Dp = getScreenWidthDpInternal().dp

/**
 * Get screen height in Dp (iOS implementation)
 */
actual fun getScreenHeightDp(): Dp = getScreenHeightDpInternal().dp
