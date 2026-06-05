package org.mountaincircles.app.utils

import androidx.compose.ui.graphics.Color

/**
 * Cross-platform color utilities
 */

/**
 * Convert Compose Color to Android color int (ARGB format)
 */
expect fun Color.toAndroidColorInt(): Int

/**
 * Create Android color int from ARGB components
 */
expect fun argbColor(alpha: Int, red: Int, green: Int, blue: Int): Int
