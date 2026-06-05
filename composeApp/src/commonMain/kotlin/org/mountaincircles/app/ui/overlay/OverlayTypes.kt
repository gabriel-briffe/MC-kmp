package org.mountaincircles.app.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.state.GlobalState

/**
 * Standard positions for map overlays
 */
enum class OverlayPosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
    LEFT_CENTER, RIGHT_CENTER;

    fun toAlignment(): Alignment {
        return when (this) {
            TOP_LEFT -> Alignment.TopStart
            TOP_CENTER -> Alignment.TopCenter
            TOP_RIGHT -> Alignment.TopEnd
            BOTTOM_LEFT -> Alignment.BottomStart
            BOTTOM_CENTER -> Alignment.BottomCenter
            BOTTOM_RIGHT -> Alignment.BottomEnd
            LEFT_CENTER -> Alignment.CenterStart
            RIGHT_CENTER -> Alignment.CenterEnd
        }
    }


}

/**
 * Interface for modules to provide map overlays
 */
interface OverlayProvider {
    val moduleId: String
    val priority: Int  // Higher priority overlays appear on top
    val position: OverlayPosition

    @Composable
    fun OverlayContent(module: ModuleBase, globalState: GlobalState)
}
