package org.mountaincircles.app.network

/**
 * iOS implementation - uses Ktor with Darwin engine
 * for optimal iOS networking performance
 */
actual fun createDownloadManager(): DownloadManager = UnifiedDownloadManagerIOS()
