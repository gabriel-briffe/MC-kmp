package org.mountaincircles.app.utils

import androidx.compose.ui.graphics.Color

/**
 * iOS implementation of color utilities
 */

/**
 * Convert Compose Color to Android color int (ARGB format)
 * On iOS, we simulate the Android color int format
 */
actual fun Color.toAndroidColorInt(): Int {
    val alpha = (this.alpha * 255).toInt()
    val red = (this.red * 255).toInt()
    val green = (this.green * 255).toInt()
    val blue = (this.blue * 255).toInt()

    // Simulate Android ARGB format: 0xAARRGGBB
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

/**
 * Create Android color int from ARGB components
 * On iOS, we simulate the Android color int format
 */
actual fun argbColor(alpha: Int, red: Int, green: Int, blue: Int): Int {
    // Simulate Android ARGB format: 0xAARRGGBB
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}
