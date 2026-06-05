package org.mountaincircles.app.network

/**
 * Centralized HTTP configuration for all network operations
 * Eliminates duplicate timeout and buffer settings across modules
 */
object HttpConfig {
    // Connection timeouts
    const val CONNECT_TIMEOUT_MS = 30000L
    const val READ_TIMEOUT_MS = 60000L

    // Buffer sizes
    const val BUFFER_SIZE_BYTES = 64 * 1024  // 64KB buffer for downloads
    const val SMALL_BUFFER_SIZE_BYTES = 8 * 1024  // 8KB buffer for smaller operations

    // Progress reporting
    const val PROGRESS_REPORT_INTERVAL_MS = 250L  // Report progress every 250ms



    // HTTP headers
    const val USER_AGENT = "MountainCircles-App/1.0"

    // Retry configuration
    const val MAX_RETRY_ATTEMPTS = 2
    const val RETRY_DELAY_MS = 1000L

    // Content validation
    const val MIN_CONTENT_LENGTH = 0L
}
