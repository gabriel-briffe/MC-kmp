package org.mountaincircles.app.modules.circles.logic.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.mountaincircles.app.ui.AppIcons

/**
 * Circles Module UI Interface
 * Icon and other UI-related methods (module actions moved to CirclesModuleActions.kt for Phase E).
 */
@Composable
fun getCirclesIcon(): Painter {
    return AppIcons.Target()
}