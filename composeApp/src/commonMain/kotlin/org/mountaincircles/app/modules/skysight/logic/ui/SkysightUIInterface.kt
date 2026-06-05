package org.mountaincircles.app.modules.skysight.logic.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.ui.AppIcons

/**
 * Get the button icon for the Skysight module
 */
@Composable
fun SkysightModule.getSkysightButtonIcon(): Painter {
    return AppIcons.Skysight() // Use custom skysight icon
}
