package org.mountaincircles.app.ui.modules

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.ui.components.rememberResourceImage

/**
 * Generic helper for providing tutorial image full-width content
 */
fun createTutorialImageContent(imagePath: String, contentDescription: String): @Composable () -> Unit {
    return {
        Spacer(modifier = Modifier.height(16.dp))
        val tutorialImage = rememberResourceImage(imagePath)
        tutorialImage?.let { imageBitmap ->
            Image(
                bitmap = imageBitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

/**
 * Generic extension for modules that have tutorial images
 */
@Composable
fun ModuleBase.provideTutorialImageContent(imagePath: String, contentDescription: String): List<@Composable () -> Unit> {
    return listOf(createTutorialImageContent(imagePath, contentDescription))
}
