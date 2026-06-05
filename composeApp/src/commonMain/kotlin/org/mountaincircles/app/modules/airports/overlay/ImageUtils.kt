package org.mountaincircles.app.modules.airports.overlay

/**
 * Platform-specific image loading for airport pictures
 */
expect suspend fun loadAirportPicture(filePath: String): androidx.compose.ui.graphics.ImageBitmap?

/**
 * Convert relative airport picture path to absolute file system path
 */
expect fun getAirportPictureAbsolutePath(relativePath: String): String
