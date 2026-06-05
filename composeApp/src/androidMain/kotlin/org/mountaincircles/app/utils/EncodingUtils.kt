package org.mountaincircles.app.utils

/**
 * Android implementation of encoding utilities
 */

/**
 * Encode byte array to Base64 string
 */
actual fun encodeBase64(data: ByteArray): String {
    return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
}

/**
 * Decode Base64 string to byte array
 */
actual fun decodeBase64(data: String): ByteArray {
    return android.util.Base64.decode(data, android.util.Base64.DEFAULT)
}
