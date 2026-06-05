package org.mountaincircles.app.utils

import kotlin.math.log10
import kotlin.math.pow

/**
 * Android implementation of string formatting utilities
 */

/**
 * Format a string with variable arguments (Android implementation)
 */
actual fun String.format(vararg args: Any?): String {
    return java.lang.String.format(this, *args)
}

/**
 * Format file size to human readable string (Android implementation)
 */
actual fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "%.1f KB".format(bytes / 1024.0)
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}

/**
 * Format number with specified decimal places (Android implementation)
 */
actual fun formatNumber(value: Double, decimals: Int): String {
    val formatString = "%.${decimals}f"
    return formatString.format(value)
}
