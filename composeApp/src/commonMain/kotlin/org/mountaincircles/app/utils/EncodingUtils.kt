package org.mountaincircles.app.utils

/**
 * Cross-platform encoding utilities
 */

/**
 * Encode byte array to Base64 string
 */
expect fun encodeBase64(data: ByteArray): String

/**
 * Decode Base64 string to byte array
 */
expect fun decodeBase64(data: String): ByteArray
