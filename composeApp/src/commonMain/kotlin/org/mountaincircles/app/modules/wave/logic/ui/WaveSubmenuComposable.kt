package org.mountaincircles.app.modules.wave.logic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.wave.AltitudeConverter
import org.mountaincircles.app.modules.wave.WaveModule
import org.mountaincircles.app.modules.wave.logic.data.WaveState
import org.mountaincircles.app.modules.wave.logic.data.RasterData
import org.mountaincircles.app.modules.wave.logic.data.NavigationData
import org.mountaincircles.app.modules.wave.logic.data.WaveSelection
import org.mountaincircles.app.modules.wave.logic.data.TimeAndPressureDisplayData
import org.mountaincircles.app.modules.wave.logic.data.LayerVisibilityData
import org.mountaincircles.app.modules.wave.logic.data.FontSettingsData
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.ui.components.GenericSubmenu
import org.mountaincircles.app.ui.components.SubmenuStyles

/**
 * Format date from YYYY-MM-DD to DD/MM
 */
private fun formatDateToShort(dateString: String): String {
    return try {
        if (dateString.length >= 10 && dateString.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            val parts = dateString.split("-")
            "${parts[2]}/${parts[1]}"
        } else {
            "N/A"
        }
    } catch (e: Exception) {
        "N/A"
    }
}

/**
 * Wave submenu that appears below the top menu
 * 
 * Provides controls for:
 * - Wave visibility toggle
 * - Time navigation (prev/next hour)
 * - Altitude navigation (pressure levels)
 */
@Composable
fun WaveSubmenuComposable(
    module: WaveModule,
    globalState: org.mountaincircles.app.state.GlobalState,
    modifier: Modifier = Modifier
) {
    // ✅ SELECTIVE FLOWS: Optimal reactive performance
    val navigationData by module.navigationFlow.collectAsState(
        initial = NavigationData(
            canPrevHour = false,
            canNextHour = false,
            canPressureUp = false,
            canPressureDown = false
        )
    )
    val timeAndPressureData by module.timeAndPressureDisplayFlow.collectAsState(
        initial = TimeAndPressureDisplayData(12, "", "", 500)
    )
    val layerData by module.layerDataFlow.collectAsState(
        initial = RasterData(false, 0.75f, WaveSelection("", "", 12, 500), 0, false)
    )
    val fontSettingsData by module.fontSettingsFlow.collectAsState(
        initial = FontSettingsData(13.0f, 10.0f)
    )
    val windLayerVisible by module.windLayerVisibilityFlow.collectAsState(initial = false)
    val moduleState by module.combinedStateFlow.collectAsState() // Keep for entries in dialog
    val coroutineScope = rememberCoroutineScope()
    var showForecastDialog by remember { mutableStateOf(false) }

    
    GenericSubmenu(
        modifier = modifier,
        spacing = Arrangement.SpaceEvenly  // Even distribution for all wave controls
    ) {
        // 1. Visibility toggle button
        Card(
            modifier = Modifier.clickable {
                Logger.log("WAVE_UI", LogLevel.INFO, "Wave visibility toggle clicked")
                coroutineScope.launch {
                    module.toggleVisibility()
                }
            },
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(SubmenuStyles.Dimensions.cornerRadius)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SubmenuStyles.Spacing.none)
            ) {
                Icon(
                    painter = if (layerData.isVisible) AppIcons.Visibility() else AppIcons.VisibilityOff(),
                    contentDescription = if (layerData.isVisible) "Hide Waves" else "Show Waves",
                    tint = if (layerData.isVisible) SubmenuStyles.Colors.iconEnabled else SubmenuStyles.Colors.iconDisabled,
                    modifier = Modifier.size(SubmenuStyles.Dimensions.iconInnerSize)
                )
                Text(
                    text = "wave",
                    style = SubmenuStyles.Typography.subLabel(fontSettingsData.subLabelFontSize).copy(
                        color = if (layerData.isVisible) SubmenuStyles.Colors.iconEnabled else SubmenuStyles.Colors.iconDisabled
                    )
                )
            }
        }
        
        // 2. Previous hour button
        IconButton(
            onClick = {
                Logger.log("WAVE_UI", LogLevel.INFO, "Previous hour clicked")
                coroutineScope.launch {
                    module.stepHour(-1)
                }
            },
            enabled = navigationData.canPrevHour,
            modifier = Modifier.size(SubmenuStyles.Dimensions.iconSize)
        ) {
            Text(
                text = "◀",
                style = SubmenuStyles.Typography.navArrow(fontSettingsData.mainLabelFontSize).copy(
                    color = if (navigationData.canPrevHour) SubmenuStyles.Colors.iconEnabled else SubmenuStyles.Colors.iconDisabled
                )
            )
        }
            
            // 3. Time button with forecast date and now action
            Card(
                modifier = Modifier
                    .clickable {
                        Logger.log("WAVE_UI", LogLevel.INFO, "Navigate to now clicked")
                        coroutineScope.launch {
                            module.navigateToNow()
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = SubmenuStyles.CardColors.timeButton),
                shape = RoundedCornerShape(SubmenuStyles.Dimensions.cornerRadius)
            ) {
                Column(
                    modifier = Modifier.padding(SubmenuStyles.Dimensions.buttonPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SubmenuStyles.Spacing.none)
                ) {
                    Text(
                        text = "${timeAndPressureData.hour.toString().padStart(2, '0')}:00z",
                        style = SubmenuStyles.Typography.mainLabel(fontSettingsData.mainLabelFontSize)
                    )
                    Text(
                        text = formatDateToShort(timeAndPressureData.targetDate.takeIf { it.isNotBlank() } ?: ""),
                        style = SubmenuStyles.Typography.subLabel(fontSettingsData.subLabelFontSize)
                    )
                }
            }
            
            // 4. Next hour button
            IconButton(
                onClick = {
                    Logger.log("WAVE_UI", LogLevel.INFO, "Next hour clicked")
                    coroutineScope.launch {
                        module.stepHour(1)
                    }
                },
                enabled = navigationData.canNextHour,
                modifier = Modifier.size(SubmenuStyles.Dimensions.iconSize)
            ) {
                Text(
                    text = "▶",
                    style = SubmenuStyles.Typography.navArrow(fontSettingsData.mainLabelFontSize).copy(
                        color = if (navigationData.canNextHour) SubmenuStyles.Colors.iconEnabled else SubmenuStyles.Colors.iconDisabled
                    )
                )
            }
            
            // 5. Up pressure button (higher altitude)
            IconButton(
                onClick = {
                    Logger.log("WAVE_UI", LogLevel.INFO, "Pressure up (higher altitude) clicked")
                    coroutineScope.launch {
                        module.stepPressure(-1) // Lower pressure number = higher altitude
                    }
                },
                enabled = navigationData.canPressureDown,
                modifier = Modifier.size(SubmenuStyles.Dimensions.iconSize)
            ) {
                Text(
                    text = "▲",
                    style = SubmenuStyles.Typography.navArrow(fontSettingsData.mainLabelFontSize).copy(
                        color = if (navigationData.canPressureDown) SubmenuStyles.Colors.iconEnabled else SubmenuStyles.Colors.iconDisabled
                    )
                )
            }
            
            // 6. Altitude/isobar display button (no action)
            Card(
                colors = CardDefaults.cardColors(containerColor = SubmenuStyles.CardColors.altitudeDisplay),
                shape = RoundedCornerShape(SubmenuStyles.Dimensions.cornerRadius)
            ) {
                Column(
                    modifier = Modifier.padding(SubmenuStyles.Dimensions.compactPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SubmenuStyles.Spacing.none)
                ) {
                    Text(
                        text = AltitudeConverter.formatAltitude(timeAndPressureData.pressure),
                        style = SubmenuStyles.Typography.mainLabel(fontSettingsData.mainLabelFontSize)
                    )
                    Text(
                        text = AltitudeConverter.formatPressure(timeAndPressureData.pressure),
                        style = SubmenuStyles.Typography.subLabel(fontSettingsData.subLabelFontSize)
                    )
                }
            }
            
            // 7. Down pressure button (lower altitude)
            IconButton(
                onClick = {
                    Logger.log("WAVE_UI", LogLevel.INFO, "Pressure down (lower altitude) clicked")
                    coroutineScope.launch {
                        module.stepPressure(1) // Higher pressure number = lower altitude
                    }
                },
                enabled = navigationData.canPressureUp,
                modifier = Modifier.size(SubmenuStyles.Dimensions.iconSize)
            ) {
                Text(
                    text = "▼",
                    style = SubmenuStyles.Typography.navArrow(fontSettingsData.mainLabelFontSize).copy(
                        color = if (navigationData.canPressureUp) SubmenuStyles.Colors.iconEnabled else SubmenuStyles.Colors.iconDisabled
                    )
                )
            }
            
            // 8. Forecast selection button
            Card(
                modifier = Modifier
                    .clickable {
                        Logger.log("WAVE_UI", LogLevel.INFO, "Forecast selection clicked")
                        showForecastDialog = true
                    },
                colors = CardDefaults.cardColors(containerColor = SubmenuStyles.CardColors.forecastButton),
                shape = RoundedCornerShape(SubmenuStyles.Dimensions.cornerRadius)
            ) {
                Column(
                    modifier = Modifier.padding(SubmenuStyles.Dimensions.compactPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SubmenuStyles.Spacing.none)
                ) {
                    Text(
                        text = "FC",
                        style = SubmenuStyles.Typography.mainLabel(fontSettingsData.mainLabelFontSize)
                    )
                    Text(
                        text = formatDateToShort(timeAndPressureData.forecastDate.takeIf { it.isNotBlank() } ?: ""),
                        style = SubmenuStyles.Typography.subLabel(fontSettingsData.subLabelFontSize)
                            .copy(color = SubmenuStyles.Colors.textPrimary)
                    )
                }
            }

            // 9. Wind vectors visibility toggle
            Card(
                modifier = Modifier.clickable {
                    Logger.log("WAVE_UI", LogLevel.INFO, "Wind layer visibility toggle clicked")
                    coroutineScope.launch {
                        module.toggleWindLayerVisibility()
                    }
                },
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(SubmenuStyles.Dimensions.cornerRadius)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SubmenuStyles.Spacing.none)
                ) {
                    Icon(
                        painter = AppIcons.Visibility(), // Wind barb icon
                        contentDescription = if (windLayerVisible) "Hide Wind Vectors" else "Show Wind Vectors",
                        tint = if (windLayerVisible) SubmenuStyles.Colors.iconEnabled else SubmenuStyles.Colors.iconDisabled,
                        modifier = Modifier.size(SubmenuStyles.Dimensions.iconInnerSize)
                    )
                    Text(
                        text = "wind",
                        style = SubmenuStyles.Typography.subLabel(fontSettingsData.subLabelFontSize).copy(
                            color = if (windLayerVisible) SubmenuStyles.Colors.iconEnabled else SubmenuStyles.Colors.iconDisabled
                        )
                    )
                }
            }
        }
    
    // Forecast Selection Dialog
    if (showForecastDialog) {
        ForecastSelectionDialog(
            module = module,
            timeAndPressureData = timeAndPressureData,
            layerData = layerData,
            fontSettingsData = fontSettingsData,
            onDismiss = { showForecastDialog = false },
                            onForecastSelected = { forecastDate ->
                Logger.log("WAVE_UI", LogLevel.INFO, "Forecast selected: $forecastDate")
                coroutineScope.launch {
                    module.setForecastDate(forecastDate)
                    Logger.log("WAVE_UI", LogLevel.INFO, "Forecast date set, waiting for state update...")
                }
                showForecastDialog = false
            }
        )
    }
}

/**
 * Dialog for selecting forecast date from available wave files
 */
@Composable
private fun ForecastSelectionDialog(
    module: WaveModule,
    timeAndPressureData: TimeAndPressureDisplayData,
    layerData: RasterData,
    fontSettingsData: FontSettingsData,
    onDismiss: () -> Unit,
    onForecastSelected: (String) -> Unit
) {
    // Use module state for dialog since it needs full entries list
    val moduleState by module.combinedStateFlow.collectAsState()

    // Extract unique forecast dates from available entries
    val availableForecastDates = moduleState.entries
        .map { it.forecastDate }
        .distinct()
        .filter { it.isNotBlank() }
        .sorted()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Forecast Date",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (availableForecastDates.isEmpty()) {
                Text(
                    text = "No forecast data available. Please import wave forecasts first.",
                    color = Color.Gray
                )
            } else {
                LazyColumn {
                    items(availableForecastDates) { forecastDate ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onForecastSelected(forecastDate)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (forecastDate == timeAndPressureData.forecastDate)
                                    Color.Blue.copy(alpha = 0.5f)
                                else
                                    Color.Gray.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = formatDateToShort(forecastDate),
                                        style = SubmenuStyles.Typography.mainLabel(fontSettingsData.mainLabelFontSize)
                                    )
                                    Text(
                                        text = forecastDate,
                                        style = SubmenuStyles.Typography.subLabel(fontSettingsData.subLabelFontSize)
                                    )
                                }
                                if (forecastDate == timeAndPressureData.forecastDate) {
                                    Icon(
                                        painter = AppIcons.Check(),
                                        contentDescription = "Selected",
                                        tint = Color.Green,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = Color.White
                )
            }
        },
        containerColor = Color.Black.copy(alpha = 0.9f),
        textContentColor = Color.White
    )
}
