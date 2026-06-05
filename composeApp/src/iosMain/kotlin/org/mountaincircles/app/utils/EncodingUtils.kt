package org.mountaincircles.app.utils

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSDataBase64EncodingOptions
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create

/**
 * iOS implementation of encoding utilities
 */

/**
 * Encode byte array to Base64 string
 */
@OptIn(ExperimentalForeignApi::class)
actual fun encodeBase64(data: ByteArray): String {
    return data.usePinned { pinned ->
        val nsData = NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
        nsData?.base64EncodedStringWithOptions(0UL) ?: ""
    }
}

/**
 * Decode Base64 string to byte array
 */
actual fun decodeBase64(data: String): ByteArray {
    // TODO: Implement Base64 decoding for iOS
    // For now, return empty array as placeholder
    return ByteArray(0)
}
