package org.mountaincircles.app.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import platform.UIKit.UIImage
import androidx.compose.ui.graphics.toComposeImageBitmap

/**
 * iOS implementation of bytes to ImageBitmap conversion
 */
actual fun bytesToImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        val nsData = bytes.toNSData()
        val uiImage = UIImage(data = nsData)
        uiImage?.toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}

// Extension to convert ByteArray to NSData
private fun ByteArray.toNSData(): platform.Foundation.NSData {
    return platform.Foundation.NSData.dataWithBytes(this@toNSData, length = this@toNSData.size.toULong())
}
