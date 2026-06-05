package org.mountaincircles.app.modules.skysight.logic.ui

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CardDefaults
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.skysight.SkysightModule
import org.mountaincircles.app.modules.skysight.logic.controllers.RealTimeTilingController
import org.mountaincircles.app.modules.skysight.logic.SkysightUtils
import org.mountaincircles.app.modules.wave.logic.data.FontSettingsData
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.ui.components.GenericSubmenu
import org.mountaincircles.app.ui.components.SubmenuStyles
import kotlinx.datetime.*
import org.mountaincircles.app.state.getGlobalState
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours

/**
 * Format LocalDate to short format like Wave's formatDateToShort (DD/MM)
 */
private fun formatDateToShort(date: LocalDate): String {
    return "${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthNumber.toString().padStart(2, '0')}"
}

/**
 * Check if a layer ID represents a w_* (wind) layer
 */
private fun isWindLayer(layerId: String): Boolean {
    return layerId.startsWith("w_")
}

/**
 * Extract the level number from a w_* layer ID (e.g., "w_1000" -> 1000)
 */
private fun extractWindLevel(layerId: String): Int? {
    if (!isWindLayer(layerId)) return null
    return layerId.substringAfter("w_").toIntOrNull()
}

/**
 * Get the available wind levels (1000, 2000, 3000, ..., 7000)
 */
private fun getAvailableWindLevels(): List<Int> {
    return listOf(1000, 2000, 3000, 4000, 5000, 6000, 7000)
}

/**
 * Navigate to the next/previous wind level
 */
private fun navigateWindLevel(currentLayerId: String, direction: Int): String? {
    val currentLevel = extractWindLevel(currentLayerId) ?: return null
    val availableLevels = getAvailableWindLevels()
    val currentIndex = availableLevels.indexOf(currentLevel)

    if (currentIndex == -1) return null

    val newIndex = currentIndex + direction
    if (newIndex < 0 || newIndex >= availableLevels.size) return null

    return "w_${availableLevels[newIndex]}"
}

/**
 * Check if we can navigate up (higher level) or down (lower level) from current wind layer
 */
private fun canNavigateWindLevel(currentLayerId: String, direction: Int): Boolean {
    return navigateWindLevel(currentLayerId, direction) != null
}

/**
 * Navigate time by the specified number of minutes (typically ±30 for forecast, ±10 for realtime)
 */
internal suspend fun navigateTime(
    minutesDelta: Int,
    currentTime: org.mountaincircles.app.modules.skysight.logic.data.TimePair,
    selectedDate: LocalDate,
    selectedLayerId: String,
    module: SkysightModule,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onTimeChanged: (org.mountaincircles.app.modules.skysight.logic.data.TimePair) -> Unit
) {
    var newMinute = currentTime.minute + minutesDelta
    var newHour = currentTime.hour
    var newDate = selectedDate

    // Handle minute overflow/underflow
    while (newMinute >= 60) {
        newMinute -= 60
        newHour += 1
    }
    while (newMinute < 0) {
        newMinute += 60
        newHour -= 1
    }

    // Handle hour overflow/underflow with date changes
    while (newHour >= 24) {
        newHour -= 24
        newDate = newDate.plus(DatePeriod(days = 1))
        Logger.log("SKYSIGHT_SUBMENU", LogLevel.DEBUG, "Date advanced by 1 day: $newDate")
    }
    while (newHour < 0) {
        newHour += 24
        newDate = newDate.minus(DatePeriod(days = 1))
        Logger.log("SKYSIGHT_SUBMENU", LogLevel.DEBUG, "Date went back by 1 day: $newDate")
    }

    val newTime = org.mountaincircles.app.modules.skysight.logic.data.TimePair(newHour, newMinute)

    // For real-time layers (satellite/rain), prevent setting time in the future
    val isRealTimeMode = module.state.value.satelliteEnabled || module.state.value.localRainEnabled
    if (isRealTimeMode) {
        // For realtime mode, work directly with timestamps
        val currentTimestamp = module.realTimeTimestamp.value
        val requestedTimestamp = currentTimestamp.plus(minutesDelta, kotlinx.datetime.DateTimeUnit.MINUTE, kotlinx.datetime.TimeZone.UTC)

        // Debug log: show navigation direction and times
        val direction = if (minutesDelta > 0) "FORWARD" else "BACKWARD"
        Logger.log("SKYSIGHT_SUBMENU", LogLevel.DEBUG, "Realtime navigation: $direction ${minutesDelta}min, current=${currentTimestamp}, requested=${requestedTimestamp}")

        if (minutesDelta > 0) {
            // For forward navigation, prevent going beyond floored 10 minutes ago
            val maxAllowedTimestamp = SkysightUtils.getFlooredTenMinutesAgoTimestamp()

            // Debug log: show requested vs allowed time comparison
            Logger.log("SKYSIGHT_SUBMENU", LogLevel.DEBUG, "Forward navigation check: requested=${requestedTimestamp}, max_allowed=${maxAllowedTimestamp}, blocked=${requestedTimestamp > maxAllowedTimestamp}")

            if (requestedTimestamp > maxAllowedTimestamp) {
                val currentRealtimeDateTime = currentTimestamp.toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
                val requestedDateTime = requestedTimestamp.toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
                Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Real-time mode: Preventing navigation beyond floored 10 minutes ago. " +
                    "Current: ${formatDateToShort(currentRealtimeDateTime.date)} ${currentRealtimeDateTime.hour.toString().padStart(2, '0')}:${currentRealtimeDateTime.minute.toString().padStart(2, '0')}, " +
                    "Requested: ${formatDateToShort(requestedDateTime.date)} ${requestedDateTime.hour.toString().padStart(2, '0')}:${requestedDateTime.minute.toString().padStart(2, '0')}, " +
                    "Max allowed: ${formatDateToShort(maxAllowedTimestamp.toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date)} ${maxAllowedTimestamp.toLocalDateTime(kotlinx.datetime.TimeZone.UTC).hour.toString().padStart(2, '0')}:${maxAllowedTimestamp.toLocalDateTime(kotlinx.datetime.TimeZone.UTC).minute.toString().padStart(2, '0')}")
                return
            }
        }

        // Update the realtime timestamp directly
        module.updateRealtimeTimestamp(requestedTimestamp)
        val requestedDateTime = requestedTimestamp.toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
        Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Realtime timestamp updated to ${requestedDateTime.hour.toString().padStart(2, '0')}:${requestedDateTime.minute.toString().padStart(2, '0')} on ${formatDateToShort(requestedDateTime.date)}")
        return // Early return for realtime mode
    }

    // Only forecast mode reaches here (realtime handled above with early return)
    onTimeChanged(newTime)
    module.updateCurrentTime(newTime)

    // Update date in module if it changed
    if (newDate != selectedDate) {
        module.updateSelectedDate(newDate)
        Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Forecast date updated to ${formatDateToShort(newDate)}")
    }

    Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Time navigated to ${newHour.toString().padStart(2, '0')}:${newMinute.toString().padStart(2, '0')} on ${formatDateToShort(newDate)}")
}

/**
 * Check if the file for the current time is downloaded, if not download it
 */

/**
 * Skysight submenu that appears below the top menu
 * Clone of Wave submenu layout with date selection functionality
 */
@Composable
fun SkysightSubmenuComposable(
    module: SkysightModule,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showDateDialog by remember { mutableStateOf(false) }
    var isPlayingAnimation by remember { mutableStateOf(false) }
    val selectedLayerId by module.selectedLayerId.collectAsState()
    val moduleSelectedDate by module.selectedDate.collectAsState()
    val currentTime by module.currentTime.collectAsState()
    val satelliteEnabled by module.satelliteEnabled.collectAsState()
    val localRainEnabled by module.localRainEnabled.collectAsState()
    val submenuMode by module.submenuMode.collectAsState()

    // Use appropriate date based on submenu mode
    val appropriateDate = if (submenuMode == "realtime") {
        module.realTimeTimestamp.collectAsState().value.toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
    } else {
        moduleSelectedDate ?: kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    }
    var selectedDate by remember(appropriateDate) {
        mutableStateOf(appropriateDate)
    }
    val isVisible by module.isVisible.collectAsState()
    val isLabelsVisible by module.isLabelsVisible.collectAsState()

    // Show forecast/wave controls when in forecast or wave mode
    val showForecastControls = submenuMode == "forecast" || submenuMode == "wave"

    // _ZZZ Debug submenu state
    Logger.log("SKYSIGHT_SUBMENU_ZZZ", LogLevel.INFO, "Submenu render - isVisible:$isVisible, submenuMode:'$submenuMode', satelliteEnabled:$satelliteEnabled, localRainEnabled:$localRainEnabled, showForecastControls:$showForecastControls")

    // Mock font settings like Wave module (would come from settings in real implementation)
    val fontSettingsData by remember { mutableStateOf(FontSettingsData(13.0f, 10.0f)) }

    GenericSubmenu(
        modifier = modifier,
        spacing = Arrangement.SpaceEvenly  // Even distribution like Wave submenu
    ) {
        // 1. Unified visibility toggle
        Card(
            modifier = Modifier.clickable {
                Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Unified visibility toggle clicked")
                coroutineScope.launch {
                    module.toggleVisibility()
                }
            },
            colors = CardDefaults.cardColors(containerColor = SubmenuStyles.CardColors.visibilityToggle),
            shape = RoundedCornerShape(SubmenuStyles.Dimensions.cornerRadius)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Icon(
                    painter = if (isVisible) AppIcons.Visibility() else AppIcons.VisibilityOff(),
                    contentDescription = if (isVisible) "Hide Skysight layers" else "Show Skysight layers",
                    tint = if (isVisible) SubmenuStyles.Colors.iconEnabled else SubmenuStyles.Colors.iconDisabled,
                    modifier = Modifier.size(SubmenuStyles.Dimensions.iconInnerSize)
                )
                // No text below icon
            }
        }


        // 2. Previous time button
        IconButton(
            onClick = {
                Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Previous time clicked")
                // Navigate to previous interval (30min for forecast/wave, 10min for realtime)
                val stepMinutes = if (submenuMode == "realtime") -10 else -30
                coroutineScope.launch {
                    // Use navigateTime for both forecast and realtime layers (it handles both modes)
                    navigateTime(stepMinutes, currentTime, selectedDate, selectedLayerId, module, coroutineScope) { _ ->
                        // Module state is already updated by navigateTime, UI will update via collectAsState
                    }
                }
            },
            modifier = Modifier.size(SubmenuStyles.Dimensions.iconSize)
        ) {
            Text(
                text = "◀",
                style = SubmenuStyles.Typography.navArrow(fontSettingsData.mainLabelFontSize).copy(
                    color = SubmenuStyles.Colors.iconEnabled
                )
            )
        }

        // 3. Time display
        Card(
            modifier = Modifier.clickable {
                Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Navigate to now clicked")
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
                    text = if (submenuMode == "realtime") {
                        // For realtime layers, show time from timestamp
                        val realtimeDateTime = module.realTimeTimestamp.collectAsState().value.toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
                        "${realtimeDateTime.hour.toString().padStart(2, '0')}:${realtimeDateTime.minute.toString().padStart(2, '0')}z"
                    } else {
                        // For forecast/wave layers, show time from TimePair with zulu indicator
                        "${currentTime.display}z"
                    },
                    style = SubmenuStyles.Typography.mainLabel(fontSettingsData.mainLabelFontSize)
                )
                // No date below time
            }
        }

        // 4. Next time button
        IconButton(
            onClick = {
                Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Next time clicked")
                // Navigate to next interval (30min for forecast/wave, 10min for realtime)
                val stepMinutes = if (submenuMode == "realtime") 10 else 30
                coroutineScope.launch {
                    // Use navigateTime for both forecast and realtime layers (it handles both modes)
                    navigateTime(stepMinutes, currentTime, selectedDate, selectedLayerId, module, coroutineScope) { _ ->
                        // Module state is already updated by navigateTime, UI will update via collectAsState
                    }
                }
            },
            modifier = Modifier.size(SubmenuStyles.Dimensions.iconSize)
        ) {
            Text(
                text = "▶",
                style = SubmenuStyles.Typography.navArrow(fontSettingsData.mainLabelFontSize).copy(
                    color = SubmenuStyles.Colors.iconEnabled
                )
            )
        }

        // 4.5. Wave level selector (show when in wave mode)
        if (submenuMode == "wave") {
            val currentLevel = extractWindLevel(selectedLayerId) ?: 1000

            // Up level button (higher altitude)
            IconButton(
                onClick = {
                    Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Wind level up clicked")
                    coroutineScope.launch {
                        val newLayerId = navigateWindLevel(selectedLayerId, 1)
                        if (newLayerId != null) {
                            module.updateSelectedForecastLayer(newLayerId)
                        }
                    }
                },
                enabled = canNavigateWindLevel(selectedLayerId, 1),
                modifier = Modifier.size(SubmenuStyles.Dimensions.iconSize)
            ) {
                Text(
                    text = "▲",
                    style = SubmenuStyles.Typography.navArrow(fontSettingsData.mainLabelFontSize).copy(
                        color = if (canNavigateWindLevel(selectedLayerId, 1)) SubmenuStyles.Colors.iconEnabled else SubmenuStyles.Colors.iconDisabled
                    )
                )
            }

            // Wind level display
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
                        text = "${currentLevel}m",
                        style = SubmenuStyles.Typography.mainLabel(fontSettingsData.mainLabelFontSize)
                    )
                }
            }

            // Down level button (lower altitude)
            IconButton(
                onClick = {
                    Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Wind level down clicked")
                    coroutineScope.launch {
                        val newLayerId = navigateWindLevel(selectedLayerId, -1)
                        if (newLayerId != null) {
                            module.updateSelectedForecastLayer(newLayerId)
                        }
                    }
                },
                enabled = canNavigateWindLevel(selectedLayerId, -1),
                modifier = Modifier.size(SubmenuStyles.Dimensions.iconSize)
            ) {
                Text(
                    text = "▼",
                    style = SubmenuStyles.Typography.navArrow(fontSettingsData.mainLabelFontSize).copy(
                        color = if (canNavigateWindLevel(selectedLayerId, -1)) SubmenuStyles.Colors.iconEnabled else SubmenuStyles.Colors.iconDisabled
                    )
                )
            }
        }

        // 5. Date selector (only show when forecast controls are active)
        if (showForecastControls) {
            Card(
            modifier = Modifier.clickable {
                Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Date selection clicked")
                showDateDialog = true
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
                    text = "Date",
                    style = SubmenuStyles.Typography.mainLabel(fontSettingsData.mainLabelFontSize)
                )
                Text(
                    text = formatDateToShort(selectedDate),
                    style = SubmenuStyles.Typography.subLabel(fontSettingsData.subLabelFontSize).copy(
                        color = SubmenuStyles.Colors.textPrimary
                    )
                )
            }
        }
        }
        // 6. Labels visibility toggle (only show when forecast controls are active)
        if (showForecastControls) {
            Card(
            modifier = Modifier.clickable {
                Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Labels visibility toggle clicked")
                coroutineScope.launch {
                    module.toggleLabelsVisibility()
                }
            },
            colors = CardDefaults.cardColors(containerColor = SubmenuStyles.CardColors.altitudeDisplay),
            shape = RoundedCornerShape(SubmenuStyles.Dimensions.cornerRadius)
        ) {
            Column(
                modifier = Modifier.padding(SubmenuStyles.Dimensions.compactPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SubmenuStyles.Spacing.none)
            ) {
                Text(
                    text = if (isLabelsVisible) "ON" else "OFF",
                    style = SubmenuStyles.Typography.mainLabel(fontSettingsData.mainLabelFontSize).copy(
                        color = if (isLabelsVisible) SubmenuStyles.Colors.iconEnabled else SubmenuStyles.Colors.iconDisabled
                    )
                )
                Text(
                    text = "Labels",
                    style = SubmenuStyles.Typography.subLabel(fontSettingsData.subLabelFontSize)
                )
            }
        }
        }

        // 6. Play 1H button (show when in realtime mode)
        if (submenuMode == "realtime") {
            TextButton(
                onClick = {
                    Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Play 1H clicked - animating last hour of realtime data")
                    coroutineScope.launch {
                        isPlayingAnimation = true
                        try {
                            // Calculate 1 hour ago from current realtime timestamp floored to 10-minute interval
                            val currentTimestamp = module.realTimeTimestamp.value
                            val oneHourAgo = currentTimestamp.minus(1, kotlinx.datetime.DateTimeUnit.HOUR, kotlinx.datetime.TimeZone.UTC)

                            // Floor to 10-minute interval
                            val oneHourAgoDateTime = oneHourAgo.toLocalDateTime(kotlinx.datetime.TimeZone.UTC)
                            val flooredMinute = (oneHourAgoDateTime.minute / 10) * 10
                            val startDateTime = kotlinx.datetime.LocalDateTime(
                                year = oneHourAgoDateTime.year,
                                monthNumber = oneHourAgoDateTime.monthNumber,
                                dayOfMonth = oneHourAgoDateTime.dayOfMonth,
                                hour = oneHourAgoDateTime.hour,
                                minute = flooredMinute,
                                second = 0,
                                nanosecond = 0
                            )
                            val startTimestamp = startDateTime.toInstant(kotlinx.datetime.TimeZone.UTC)

                            Logger.log("SKYSIGHT_SUBMENU", LogLevel.DEBUG, "Play 1H: Calculated start timestamp=${startTimestamp}")
                            Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Play 1H: Starting from ${startDateTime.hour.toString().padStart(2, '0')}:${startDateTime.minute.toString().padStart(2, '0')} on ${formatDateToShort(startDateTime.date)}")

                            // Set initial timestamp to 1 hour ago floored
                            module.updateRealtimeTimestamp(startTimestamp)
                            Logger.log("SKYSIGHT_SUBMENU", LogLevel.DEBUG, "Play 1H: Updated realtime timestamp to start time for animation")

                            // For realtime mode, we don't need to call navigateTime as we've directly updated the timestamp
                            // The pipeline will automatically update when the timestamp changes

                            // Wait 1 second
                            kotlinx.coroutines.delay(1000)

                            // Simulate clicking chevron right (next) 4 times - advances by 10 minutes for realtime
                            // This gives us: 60min ago -> 50min ago -> 40min ago -> 30min ago -> 20min ago
                            repeat(6) { step ->
                                Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Play 1H step ${step + 1}: advancing by 10 minutes")
                                // For realtime layers, advance timestamp by 10 minutes
                                val currentTimestamp = module.realTimeTimestamp.value
                                val newTimestamp = currentTimestamp.plus(10, kotlinx.datetime.DateTimeUnit.MINUTE, kotlinx.datetime.TimeZone.UTC)
                                module.updateRealtimeTimestamp(newTimestamp)

                                // Wait 1 second before next step
                                kotlinx.coroutines.delay(1000)
                            }
                        } finally {
                            isPlayingAnimation = false
                        }
                    }
                },
                enabled = !isPlayingAnimation
            ) {
                Text(
                    text = "Play 1H",
                    style = SubmenuStyles.Typography.mainLabel(fontSettingsData.mainLabelFontSize)
                )
            }
        }

        // 7. Refresh button (show when in realtime mode)
        if (submenuMode == "realtime") {
            IconButton(
                onClick = {
                    Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Real-time refresh clicked - completely clearing all files and state entries related to current time and selected realtime layers")
                    coroutineScope.launch {
                        try {
                            // Get current time for comprehensive cleanup (convert Instant to LocalDateTime like the controller does)
                            val currentTime = module.realTimeTimestamp.value
                            val tileTime = currentTime.toLocalDateTime(kotlinx.datetime.TimeZone.UTC)

                            // Format timestamp part like generateTileKey does: YYYYMMDD_HHMM
                            val timestampPart = "${tileTime.year}${tileTime.monthNumber.toString().padStart(2, '0')}${tileTime.dayOfMonth.toString().padStart(2, '0')}_${tileTime.hour.toString().padStart(2, '0')}${tileTime.minute.toString().padStart(2, '0')}"

                            Logger.log("SKYSIGHT_SUBMENU", LogLevel.DEBUG, "Refresh checking for timestamp: $timestampPart")
                            Logger.log("SKYSIGHT_SUBMENU", LogLevel.DEBUG, "All active tiles in state: ${module.state.value.activeTileLayers.keys.joinToString(", ")}")

                            // Get all active tiles for enabled layer types at current time
                            val tilesToClean = mutableListOf<String>()
                            if (satelliteEnabled) {
                                val satelliteTiles = module.state.value.activeTileLayers.keys.filter { it.startsWith("satellite_") }
                                Logger.log("SKYSIGHT_SUBMENU", LogLevel.DEBUG, "Satellite enabled, found ${satelliteTiles.size} satellite tiles: ${satelliteTiles.joinToString(", ")}")
                                tilesToClean.addAll(satelliteTiles.filter { it.contains(timestampPart) })
                            }
                            if (localRainEnabled) {
                                val rainTiles = module.state.value.activeTileLayers.keys.filter { it.startsWith("rain_") }
                                Logger.log("SKYSIGHT_SUBMENU", LogLevel.DEBUG, "Rain enabled, found ${rainTiles.size} rain tiles: ${rainTiles.joinToString(", ")}")
                                tilesToClean.addAll(rainTiles.filter { it.contains(timestampPart) })
                            }

                            Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Found ${tilesToClean.size} tiles to clean for current time: ${tilesToClean.joinToString(", ")}")

                            // Delete all corresponding files
                            val fileManager = org.mountaincircles.app.io.getGlobalFileManager()
                            val dataDir = fileManager.getAppDataDirectory()
                            val skysightDir = "$dataDir/${org.mountaincircles.app.modules.skysight.logic.SkysightConstants.SKYSIGHT_DIR}"

                            var deletedFilesCount = 0
                            for (tileKey in tilesToClean) {
                                val parts = tileKey.split("_")
                                if (parts.size >= 4) {
                                    val layerType = parts[0]
                                    val filename = "$tileKey.jpg"
                                    val filePath = "$skysightDir/$layerType/$filename"
                                    if (fileManager.delete(filePath)) {
                                        deletedFilesCount++
                                        Logger.log("SKYSIGHT_SUBMENU", LogLevel.DEBUG, "Deleted file: $filename")
                                    }
                                }
                            }
                            Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Deleted $deletedFilesCount files")

                            // Clear all corresponding state entries
                            module.updateState { currentState ->
                                val updatedActiveTiles = currentState.activeTileLayers.toMutableMap()
                                tilesToClean.forEach { tileKey ->
                                    updatedActiveTiles.remove(tileKey)
                                }
                                currentState.copy(activeTileLayers = updatedActiveTiles)
                            }
                            Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Cleared ${tilesToClean.size} state entries")

                            // Clear download status entries for cleaned tiles
                            tilesToClean.forEach { tileKey ->
                                org.mountaincircles.app.modules.skysight.logic.controllers.RealTimeTilingController.removeDownloadStatus(module, tileKey)
                            }
                            Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Cleared download statuses for ${tilesToClean.size} tiles")

                            // Unregister all corresponding layers
                            for (tileKey in tilesToClean) {
                                val layerId = "skysight_$tileKey"
                                try {
                                    if (org.mountaincircles.app.ui.map.LayerRegistrationHelper.layerManager.isLayerRegistered(layerId)) {
                                        org.mountaincircles.app.ui.map.LayerRegistrationHelper.unregisterLayer(layerId)
                                        Logger.log("SKYSIGHT_SUBMENU", LogLevel.DEBUG, "Unregistered layer: $layerId")
                                    }
                                } catch (e: Exception) {
                                    Logger.log("SKYSIGHT_SUBMENU", LogLevel.ERROR, "Failed to unregister layer $layerId: ${e.message}")
                                }
                            }

                            // Trigger complete reload from clean state
                            Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Triggering complete reload from clean state")
                            val reloadResult = RealTimeTilingController.handleTileUpdate(
                                module = module,
                                globalState = org.mountaincircles.app.state.getGlobalState(),
                                isNavigation = true
                            )
                            if (reloadResult.isFailure) {
                                Logger.log("SKYSIGHT_SUBMENU", LogLevel.ERROR, "Failed to reload tiles: ${reloadResult.exceptionOrNull()?.message}")
                            }

                        } catch (e: Exception) {
                            Logger.log("SKYSIGHT_SUBMENU", LogLevel.ERROR, "Exception during complete refresh: ${e.message}")
                        }
                    }
                },
                modifier = Modifier.size(SubmenuStyles.Dimensions.iconSize)
            ) {
                Text(
                    text = "↻",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SubmenuStyles.Colors.iconEnabled
                    ),
                    modifier = Modifier.size(SubmenuStyles.Dimensions.iconInnerSize)
                )
            }
        }
    }

    // Date selection dialog
    if (showDateDialog) {
        DateSelectionDialog(
            module = module,
            selectedDate = selectedDate,
            selectedLayerId = selectedLayerId,
            onDateSelected = { newDate ->
                Logger.log("SKYSIGHT_SUBMENU", LogLevel.INFO, "Date selected: $newDate")
                selectedDate = newDate // Update the reactive state
                coroutineScope.launch {
                    module.updateSelectedDate(newDate)
                }
                showDateDialog = false
                // Note: The unified pipeline in MapContainer will handle URL fetching and data loading automatically
            },
            onDismiss = {
                showDateDialog = false
            },
            fontSettingsData = fontSettingsData
        )
    }
}

/**
 * Date selection dialog for choosing forecast date
 * Matches Wave forecast dialog layout exactly
 */
@Composable
private fun DateSelectionDialog(
    module: SkysightModule,
    selectedDate: LocalDate,
    selectedLayerId: String,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    fontSettingsData: FontSettingsData
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Forecast Date",
                color = SubmenuStyles.Colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn {
                items(6) { i ->  // Today + next 5 days
                    val today = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
                    val date = today.plus(DatePeriod(days = i))
                    val dateString = date.toString()
                    val isSelected = date == selectedDate
                    val hasData = selectedLayerId.isNotEmpty() && module.hasLayerDataUrls(selectedLayerId, dateString)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                onDateSelected(date)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) SubmenuStyles.Colors.primary else SubmenuStyles.Colors.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatDateToShort(date),
                                    style = SubmenuStyles.Typography.mainLabel(fontSettingsData.mainLabelFontSize)
                                )
                                Text(
                                    text = date.toString(), // Full date like Wave
                                    style = SubmenuStyles.Typography.subLabel(fontSettingsData.subLabelFontSize)
                                )
                            }
                            when {
                                isSelected -> {
                                    Icon(
                                        painter = AppIcons.Check(),
                                        contentDescription = "Selected",
                                        tint = Color.Green,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                hasData -> {
                                    Icon(
                                        painter = AppIcons.Check(),
                                        contentDescription = "Data Available",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
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
                    color = SubmenuStyles.Colors.textSecondary
                )
            }
        },
        containerColor = SubmenuStyles.Colors.background
    )
}