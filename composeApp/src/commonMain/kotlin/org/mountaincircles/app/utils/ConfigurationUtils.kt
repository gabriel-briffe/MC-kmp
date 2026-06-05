package org.mountaincircles.app.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cross-platform configuration utilities
 */

/**
 * Cross-platform screen configuration
 */
expect object ScreenConfiguration {
    val screenWidthDp: Int
    val screenHeightDp: Int
}

/**
 * Get screen width in Dp (internal function)
 */
internal expect fun getScreenWidthDpInternal(): Int

/**
 * Get screen height in Dp (internal function)
 */
internal expect fun getScreenHeightDpInternal(): Int

/**
 * Get screen width in Dp
 */
expect fun getScreenWidthDp(): Dp

/**
 * Get screen height in Dp
 */
expect fun getScreenHeightDp(): Dp
