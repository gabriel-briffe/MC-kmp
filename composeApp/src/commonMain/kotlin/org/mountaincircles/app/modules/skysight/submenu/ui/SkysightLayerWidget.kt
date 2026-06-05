package org.mountaincircles.app.modules.skysight.submenu.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.controllers.SkysightDataControllerV2
import org.mountaincircles.app.modules.skysight.logic.data.SkysightLayer
import org.mountaincircles.app.ui.components.CheckboxListWidget
import org.mountaincircles.app.ui.components.GenericSidebarWidget
import java.time.LocalDate

/**
 * Skysight Layer Widget for sidebar
 * Allows users to select which forecast layer to display (only one at a time)
 */
@Composable
fun SkysightLayerWidget(
    skysightModule: SkysightModule,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val availableLayers by skysightModule.availableLayers.collectAsState()
    val selectedLayerId by skysightModule.selectedLayerId.collectAsState()
    val isLoggedIn by skysightModule.isLoggedIn.collectAsState()
    val satelliteEnabled by skysightModule.satelliteEnabled.collectAsState()
    val localRainEnabled by skysightModule.localRainEnabled.collectAsState()
    val currentTime by skysightModule.currentTime.collectAsState()

    // Only show widget if user is logged in
    if (!isLoggedIn) {
        return
    }

    GenericSidebarWidget(
        title = "SkySight",
        modifier = modifier
    ) {
        // Satellite and local rain checkboxes (satellite will be rewired to RealTime pipeline)
        CheckboxListWidget(
            hasData = true,
            availableItems = setOf("satellite", "local_rain"),
            visibleItems = buildSet {
                if (satelliteEnabled) add("satellite")
                if (localRainEnabled) add("local_rain")
            },
            getDisplayName = { layerId: String ->
                when (layerId) {
                    "satellite" -> "Satellite Imagery"
                    "local_rain" -> "Local Weather Radar"
                    else -> layerId
                }
            },
            getColor = { layerId: String ->
                when (layerId) {
                    "satellite" -> Color(0xFF4CAF50) // Green for satellite
                    "local_rain" -> Color(0xFFFF9800) // Orange for local rain
                    else -> Color(0xFF2196F3) // Blue for other layers
                }
            },
            onCheckedChange = { layerId: String, isChecked: Boolean ->
                scope.launch {
                    when (layerId) {
                        "satellite" -> {
                            skysightModule.setSatelliteEnabled(isChecked)
                            // When enabling satellite, disable any selected forecast layer and update last selected
                            if (isChecked) {
                                skysightModule.updateSelectedForecastLayer("")
                                skysightModule.updateState { it.copy(lastSelectedLayer = "satellite", isVisible = true) }
                                // Navigate to current realtime when enabling satellite
                                skysightModule.navigateToNowRealtime(forceRealTimeCheck = true)
                                // Open submenu and close sidebar when selecting realtime layer
                                org.mountaincircles.app.state.getGlobalState().navigationState.openSubmenu("skysight")
                                // Only close sidebar if rain is ALSO already enabled (both realtime layers active)
                                if (localRainEnabled) {
                                    org.mountaincircles.app.state.getGlobalState().navigationState.closeSidebar()
                                }
                            } else if (!skysightModule.state.value.localRainEnabled) {
                                // If disabling satellite and no rain enabled, clear last selected
                                skysightModule.updateState { it.copy(lastSelectedLayer = "") }
                            }
                        }
                        "local_rain" -> {
                            skysightModule.setLocalRainEnabled(isChecked)
                            // When enabling local rain, disable any selected forecast layer and update last selected
                            if (isChecked) {
                                skysightModule.updateSelectedForecastLayer("")
                                skysightModule.updateState { it.copy(lastSelectedLayer = if (skysightModule.state.value.satelliteEnabled) "satellite+rain" else "rain", isVisible = true) }
                                // Navigate to current realtime when enabling rain
                                skysightModule.navigateToNowRealtime(forceRealTimeCheck = true)
                                // Open submenu and close sidebar when selecting realtime layer
                                org.mountaincircles.app.state.getGlobalState().navigationState.openSubmenu("skysight")
                                // Only close sidebar if satellite is ALSO already enabled (both realtime layers active)
                                if (satelliteEnabled) {
                                    org.mountaincircles.app.state.getGlobalState().navigationState.closeSidebar()
                                }
                            } else if (!skysightModule.state.value.satelliteEnabled) {
                                // If disabling rain and no satellite enabled, clear last selected
                                skysightModule.updateState { it.copy(lastSelectedLayer = "") }
                            }
                        }
                    }
                }
            },
            emptyDataMessage = "",
            noItemsMessage = ""
        )

        // Separator line
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Forecast Skysight layers
        CheckboxListWidget(
            hasData = availableLayers.isNotEmpty(),
            availableItems = availableLayers.map { it.id }.toSet(),
            visibleItems = if (selectedLayerId.isNotEmpty()) setOf(selectedLayerId) else emptySet(),
            getDisplayName = { layerId: String ->
                availableLayers.find { it.id == layerId }?.name ?: layerId
            },
            getColor = { layerId: String ->
                Color(0xFF2196F3) // Blue for other layers
            },
            onCheckedChange = { layerId: String, isChecked: Boolean ->
                Logger.log("SKYSIGHT_LAYER", LogLevel.INFO, "Layer selection: $layerId, isChecked: $isChecked")

                scope.launch {
                    if (isChecked) {
                        // Selecting a forecast layer disables satellite and local rain
                        skysightModule.setSatelliteEnabled(false)
                        skysightModule.setLocalRainEnabled(false)
                        // Update last selected layer and set submenu mode
                        val isWaveLayer = layerId.startsWith("w_")
                        val submenuMode = if (isWaveLayer) "wave" else "forecast"
                        skysightModule.updateState {
                            it.copy(
                                lastSelectedLayer = "forecast:$layerId",
                                submenuMode = submenuMode,
                                isVisible = true
                            )
                        }

                        // Only one layer can be selected at a time
                        // Selecting a new layer replaces any current selection
                        skysightModule.updateSelectedForecastLayer(layerId)

                        // If current time is not on a 30-minute boundary, navigate to now to round it
                        // (Must be called AFTER updateSelectedForecastLayer so selectedLayerId is set)
                        // val currentMinutes = currentTime.minute
                        // if (currentMinutes % 30 != 0) {
                        //     skysightModule.navigateToNowForecast()
                        //     Logger.log("SKYSIGHT_LAYER", LogLevel.INFO, "Auto-navigated to now (forecast) when selecting forecast layer '$layerId' (current minutes: $currentMinutes)")
                        // }
                        // Data fetching will be handled by the reactive pipeline in MapContainer.kt
                    } else {
                        // If unchecking the currently selected layer, clear selection
                        if (selectedLayerId == layerId) {
                            skysightModule.updateSelectedForecastLayer("")
                        }
                    }
                }
            },
            emptyDataMessage = "No region selected",
            noItemsMessage = "No layers available for selected region"
        )
    }
}