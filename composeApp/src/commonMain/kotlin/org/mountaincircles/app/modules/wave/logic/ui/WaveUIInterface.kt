package org.mountaincircles.app.modules.wave.logic.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.mountaincircles.app.ui.AppIcons

/**
 * Wave Module UI Interface
 * Icon and other UI-related methods (module actions moved to WaveModuleActions.kt for Phase E).
 */
@Composable
fun getWaveIcon(): Painter {
    return AppIcons.Wave()
}
