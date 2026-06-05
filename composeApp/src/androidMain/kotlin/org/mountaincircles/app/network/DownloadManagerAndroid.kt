package org.mountaincircles.app.network

/**
 * Android implementation - delegates to the unified implementation
 * which uses HttpURLConnection for optimal Android compatibility
 */
actual fun createDownloadManager(): DownloadManager = UnifiedDownloadManagerAndroid()
