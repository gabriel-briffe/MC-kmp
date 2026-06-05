package org.mountaincircles.app.modules.circles.overlay.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.text.font.FontWeight
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.modules.circles.logic.data.PackConfig
import org.mountaincircles.app.modules.circles.logic.data.PackMetadata
import org.mountaincircles.app.ui.overlay.OverlayProvider
import org.mountaincircles.app.ui.overlay.OverlayPosition
import org.mountaincircles.app.modules.circles.overlay.ui.ParameterOverlay
import org.mountaincircles.app.state.GlobalState

/**
 * Circles module parameter overlay provider
 * Shows parameter information when there's an active config AND (circles are visible OR sectors have opacity > 0)
 * Optimized to only render when there's actually something to display
 */
class CirclesParameterOverlay : OverlayProvider {
    override val moduleId = "circles"
    override val priority = 10  // Low priority, can be covered by higher priority overlays
    override val position = OverlayPosition.BOTTOM_CENTER

    @Composable
    override fun OverlayContent(module: ModuleBase, globalState: GlobalState) {
        val circlesModule = module as CirclesModule

        // ✅ Optimized: Collect state flow first, then derive only needed properties
        val circlesState by circlesModule.circlesState.collectAsState()

        // Only listen to the 3 properties we actually need
        val circlesVisibility by remember { derivedStateOf { circlesState.circlesVisibility } }
        val sectorsOpacity by remember { derivedStateOf { circlesState.sectorsOpacity } }
        val activeConfig by remember(circlesState) {
            derivedStateOf { circlesState.activeConfig }
        }

        // Only render if there's something to show
        val hasActiveConfig = activeConfig != null
        val shouldShow = hasActiveConfig && (circlesVisibility || sectorsOpacity > 0.0f)

        // Debug: Always log overlay state
        Logger.log("CIRCLES_OVERLAY", LogLevel.DEBUG,
            "🎯 OverlayContent called - config: ${activeConfig?.packId}, visibility: $circlesVisibility, opacity: $sectorsOpacity, shouldShow: $shouldShow")

        if (shouldShow) {
            Logger.log("CIRCLES_OVERLAY", LogLevel.DEBUG,
                "🎯 Showing circles parameter overlay")
            CirclesParameterBox(activeConfig!!)
        }
    }
}

/**
 * Parameter box component that displays circle configuration information
 */
@Composable
private fun CirclesParameterBox(activeConfig: PackConfig?) {
    Logger.log("CIRCLES_OVERLAY", LogLevel.DEBUG, "   Building parameter box with config: ${activeConfig?.let { "${it.packId}/${it.configId}" } ?: "null"}")

    val parameters = activeConfig?.let { config ->
        Logger.log("CIRCLES_OVERLAY", LogLevel.DEBUG, "   Config metadata: ${config.metadata}")
        Logger.log("CIRCLES_OVERLAY", LogLevel.DEBUG, "   Config prefix: ${config.metadata?.prefix}")

        // Parse prefix like "10-100-250" into readable format
        config.metadata?.prefix?.let { prefix ->
            val parsed = parsePrefixParameters(prefix)
            Logger.log("CIRCLES_OVERLAY", LogLevel.DEBUG, "   Parsed parameters: '$prefix' -> '$parsed'")
            parsed
        } ?: config.configId
    } ?: "No active configuration"

    Logger.log("CIRCLES_OVERLAY", LogLevel.INFO, "🎯 Parameter overlay text: '$parameters'")

    ParameterOverlay(
        text = parameters,
        fontWeight = FontWeight.Bold
    )
}

/**
 * Parse prefix format like "10-100-250" into readable parameters
 */
private fun parsePrefixParameters(prefix: String): String {
    Logger.log("CIRCLES_OVERLAY", LogLevel.DEBUG, "     Parsing prefix: '$prefix'")

    val parts = prefix.split("-")
    Logger.log("CIRCLES_OVERLAY", LogLevel.DEBUG, "     Split into ${parts.size} parts: ${parts.joinToString(", ")}")

    return when (parts.size) {
        3 -> {
            val result = "L/D ${parts[0]}-ground ${parts[1]}-circuit ${parts[2]}"
            Logger.log("CIRCLES_OVERLAY", LogLevel.DEBUG, "     Parsed as 3-part format: '$result'")
            result
        }
        else -> {
            Logger.log("CIRCLES_OVERLAY", LogLevel.DEBUG, "     Using raw prefix (unexpected format)")
            prefix // Fallback to raw prefix
        }
    }
}
