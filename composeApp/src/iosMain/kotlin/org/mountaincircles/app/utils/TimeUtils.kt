package org.mountaincircles.app.utils

/**
 * iOS implementation of time utilities
 */
actual fun currentTimeMillis(): Long {
    // Simple approximation for iOS - return a reasonable current timestamp
    // This can be improved later with proper iOS time APIs
    return 1700000000000L // Approximate current time (Jan 2024)
}
