package org.mountaincircles.app.modules.skysight.overlay.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.text.font.FontWeight
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.data.getUnitString
import org.mountaincircles.app.modules.skysight.logic.data.getUnitString
import org.mountaincircles.app.modules.skysight.logic.data.getUnitString
import org.mountaincircles.app.modules.skysight.logic.data.getUnitString
import org.mountaincircles.app.modules.skysight.logic.data.getUnitString
import org.mountaincircles.app.modules.skysight.logic.data.getUnitString
import org.mountaincircles.app.ui.overlay.OverlayProvider
import org.mountaincircles.app.ui.overlay.OverlayPosition
import org.mountaincircles.app.state.GlobalState
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone

/**
 * Skysight module parameter overlay provider
 * Shows layer information when bitmap is visible
 */
class SkysightParameterOverlay : OverlayProvider {
    override val moduleId = "skysight"
    override val priority = 10  // Low priority, can be covered by higher priority overlays
    override val position = OverlayPosition.BOTTOM_CENTER

    @Composable
    override fun OverlayContent(module: ModuleBase, globalState: GlobalState) {
        val skysightModule = module as SkysightModule

        // Collect required state
        val layerData by skysightModule.layerDataFlow.collectAsState()
        val isDownloading by skysightModule.isDownloading.collectAsState()
        val cancelledBatchImport by skysightModule.cancelledBatchImport.collectAsState()
        val satelliteEnabled by skysightModule.satelliteEnabled.collectAsState()
        val localRainEnabled by skysightModule.localRainEnabled.collectAsState()
        val forecastMinZoom by skysightModule.forecastMinZoom.collectAsState()

        // Get current zoom level
        val currentCameraState by globalState.currentCameraState.collectAsState()
        val currentZoom = currentCameraState?.position?.zoom ?: 0.0

        // Show overlay when downloading OR when bitmap is visible OR when showing stopped message
        val shouldShow = isDownloading || cancelledBatchImport || layerData.isVisible

        if (shouldShow) {
            Logger.log("SKYSIGHT_OVERLAY", LogLevel.DEBUG, "Showing Skysight parameter overlay")

            // Show downloading message, stopped message, layer info, or "select a layer"
            val displayText = if (cancelledBatchImport) {
                "batch import stopped"
            } else if (isDownloading) {
                "downloading..."
            } else {
                // Determine what layers are active
                val hasForecast = layerData.selectedLayerId.isNotEmpty()
                val hasSatellite = satelliteEnabled
                val hasRain = localRainEnabled

                when {
                    hasForecast -> {
                        // Check if zoomed below minimum zoom level for forecast layers
                        if (currentZoom < forecastMinZoom) {
                            "zoom in"
                        } else {
                            // Show forecast layer name when forecast is selected (takes priority over realtime)
                            val selectedLayer = skysightModule.availableLayers.value.find { it.id == layerData.selectedLayerId }
                            if (selectedLayer != null) {
                                val unitString = selectedLayer.getUnitString()
                                "${selectedLayer.name} ($unitString)"
                            } else {
                                layerData.selectedLayerId
                            }
                        }
                    }
                    hasSatellite && hasRain -> "satellite + rain"
                    hasSatellite -> "satellite"
                    hasRain -> "rain"
                    else -> "select a layer"
                }
            }

            // Reactively check if circles module is showing its overlay to adjust positioning
            val circlesModule = globalState.moduleManager.getModule("circles") as? org.mountaincircles.app.modules.circles.CirclesModule

            // Collect circles state reactively
            val circlesVisibility by circlesModule?.circlesVisibility?.collectAsState() ?: remember { mutableStateOf(false) }
            val sectorsOpacity by circlesModule?.sectorsOpacity?.collectAsState() ?: remember { mutableStateOf(0.0f) }
            val activeConfig by circlesModule?.activeConfig?.collectAsState() ?: remember { mutableStateOf(null) }

            val circlesOverlayVisible = activeConfig != null && (circlesVisibility || sectorsOpacity > 0.0f)

            SkysightParameterBox(
                text = displayText,
                isCirclesOverlayVisible = circlesOverlayVisible
            )
        }
    }
}

/**
 * Parameter box component that displays Skysight layer information
 */
@Composable
private fun SkysightParameterBox(
    text: String,
    isCirclesOverlayVisible: Boolean = false
) {
    Logger.log("SKYSIGHT_OVERLAY", LogLevel.INFO, "Parameter overlay text: '$text'")

    // Adjust padding based on whether circles overlay is visible
    // When circles is visible, position just above it (close enough to touch)
    val bottomPadding = if (isCirclesOverlayVisible) 45.dp else 8.dp

    ParameterOverlay(
        text = text,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = bottomPadding)
    )
}