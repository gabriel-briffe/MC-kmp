package org.mountaincircles.app.utils

import androidx.compose.ui.graphics.Color

/**
 * Android implementation of color utilities
 */

/**
 * Convert Compose Color to Android color int (ARGB format)
 */
actual fun Color.toAndroidColorInt(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}

/**
 * Create Android color int from ARGB components
 */
actual fun argbColor(alpha: Int, red: Int, green: Int, blue: Int): Int {
    return android.graphics.Color.argb(alpha, red, green, blue)
}
