package org.mountaincircles.app.utils

/**
 * Cross-platform string formatting utilities
 */

/**
 * Format a string with variable arguments (cross-platform)
 */
expect fun String.format(vararg args: Any?): String

/**
 * Format file size to human readable string
 */
expect fun formatFileSize(bytes: Long): String

/**
 * Format number with specified decimal places
 */
expect fun formatNumber(value: Double, decimals: Int): String
