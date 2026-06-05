package org.mountaincircles.app.utils

/**
 * iOS implementation of string formatting utilities
 */

/**
 * Format a string with variable arguments (iOS implementation)
 */
actual fun String.format(vararg args: Any?): String {
    // Simple implementation for iOS - replace %s, %d, %f patterns
    var result = this
    args.forEachIndexed { index, arg ->
        val placeholder = when (arg) {
            is Double -> "%.${if (arg % 1.0 == 0.0) "0" else "1"}f"
            else -> "%s"
        }
        result = result.replaceFirst(placeholder, arg.toString())
    }
    return result
}

/**
 * Format file size to human readable string (iOS implementation)
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
 * Format number with specified decimal places (iOS implementation)
 */
actual fun formatNumber(value: Double, decimals: Int): String {
    // Simple decimal formatting for iOS
    var factor = 1.0
    for (i in 0 until decimals) {
        factor *= 10.0
    }
    val rounded = kotlin.math.round(value * factor) / factor
    return rounded.toString()
}
