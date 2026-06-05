package org.mountaincircles.app.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import mountaincircles.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Generic image loader for compose resources
 * Loads and remembers images from the compose resources "files" directory
 */
@Composable
@OptIn(ExperimentalResourceApi::class)
fun rememberResourceImage(resourcePath: String): ImageBitmap? {
    var imageBitmap by remember(resourcePath) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(resourcePath) { mutableStateOf(true) }
    var loadError by remember(resourcePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(resourcePath) {
        try {
            isLoading = true
            loadError = null

            // Load image bytes from compose resources
            val imageBytes = Res.readBytes(resourcePath)

            // Convert bytes to ImageBitmap (platform-specific implementation)
            imageBitmap = bytesToImageBitmap(imageBytes)
        } catch (e: Exception) {
            loadError = e.message ?: "Unknown error"
            imageBitmap = null
        } finally {
            isLoading = false
        }
    }

    return imageBitmap
}

// Platform-specific function to convert bytes to ImageBitmap
expect fun bytesToImageBitmap(bytes: ByteArray): ImageBitmap?
