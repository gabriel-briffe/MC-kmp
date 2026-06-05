package org.mountaincircles.app.modules.livetracking.overlay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.livetracking.LiveTrackingModule
import org.mountaincircles.app.modules.livetracking.logic.data.AircraftFeatureData
import org.mountaincircles.app.ui.overlay.SwipeablePopup
import org.mountaincircles.app.ui.overlay.SwipeablePopupConfig
import org.mountaincircles.app.ui.overlay.SwipeablePopupContent
import org.mountaincircles.app.ui.components.StandardButton
import org.mountaincircles.app.ui.overlay.OverlayProvider
import org.mountaincircles.app.ui.overlay.OverlayPosition
import org.mountaincircles.app.ui.AppIcons
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.mountaincircles.app.utils.currentTimeMillis
import org.mountaincircles.app.state.PopupId
import org.mountaincircles.app.state.GlobalState

/**
 * LiveTracking module popup overlay provider
 * Shows aircraft information when aircraft popup is triggered by clicking
 * Popup can be dismissed by swiping left on the popup content
 */
class LiveTrackingPopupOverlay : OverlayProvider {
    override val moduleId = "livetracking"
    override val priority = 14  // Lower priority than airports (15) so airports appear above
    override val position = OverlayPosition.BOTTOM_CENTER

    @Composable
    override fun OverlayContent(module: ModuleBase, globalState: GlobalState) {
        Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "🎯 LiveTrackingPopupOverlay.OverlayContent called")

        val liveTrackingModule = remember(module) { module as LiveTrackingModule }

        // ✅ Observe centralized popup state (exclusive like submenus)
        val activePopup by globalState.navigationState.popupVisible.collectAsState()

        // Check if this livetracking popup should be shown
        val showPopup = activePopup?.moduleId == moduleId && activePopup?.dataId != null

        Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "   activePopup: ${activePopup?.moduleId}:${activePopup?.dataId}")
        Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "   showPopup: $showPopup")

        if (showPopup) {
            val deviceId = activePopup?.dataId!!
            Logger.log("LIVETRACKING_POPUP", LogLevel.INFO, "🎯 Showing livetracking popup overlay for deviceId: $deviceId")

            LiveTrackingPopupContent(
                liveTrackingModule = liveTrackingModule,
                deviceId = deviceId,
                globalState = globalState,
                onDismiss = {
                    Logger.log("LIVETRACKING_POPUP", LogLevel.INFO, "🔴 OVERLAY onDismiss called")
                    globalState.navigationState.closePopup()
                    liveTrackingModule.hideAircraftPopup() // Also update module state
                    Logger.log("LIVETRACKING_POPUP", LogLevel.INFO, "✅ OVERLAY onDismiss completed - aircraft popup closed")
                }
            )
        } else {
            Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "🎯 LiveTracking popup not shown - not active popup")
        }
    }

    /**
     * LiveTracking popup content component
     * Displays aircraft information in a simple card with swipe-to-dismiss
     * Content is reactive to GeoJSON updates
     */
    @Composable
    private fun LiveTrackingPopupContent(
        liveTrackingModule: LiveTrackingModule,
        deviceId: String,
        globalState: GlobalState,
        onDismiss: () -> Unit
    ) {
        Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "   Building livetracking popup for deviceId: $deviceId")

        // Get current aircraft features data - this makes the popup reactive
        val aircraftFeatures by liveTrackingModule.aircraftFeaturesFlow.collectAsState()

        // Get friendlist state to make popup reactive to friendlist changes
        val friendlistDeviceIds by remember {
            liveTrackingModule.liveTrackingState.map { it.friendlist.map { friend -> friend.deviceId }.toSet() }.distinctUntilChanged()
        }.collectAsState(emptySet())

        // Find current aircraft data by deviceId
        val currentAircraftData = remember(aircraftFeatures, deviceId, friendlistDeviceIds) {
            Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "Looking for aircraft with deviceId: '$deviceId'")
            try {
                // Look up feature directly from the aircraftFeatures map
                val feature = aircraftFeatures[deviceId]
                if (feature != null) {
                    Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "Found matching feature!")

                    // Convert Feature properties to Map<String, String>
                    val propertyMap = feature.properties.entries.associate { (key, value) ->
                        key to when (value) {
                            is kotlinx.serialization.json.JsonPrimitive -> {
                                if (value.isString) value.content else value.toString()
                            }
                            else -> value.toString()
                        }
                    }
                    Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "Created aircraft data with properties: $propertyMap")
                    AircraftFeatureData(
                        deviceId = deviceId,
                        properties = propertyMap
                    )
                } else {
                    Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "No feature found for deviceId: '$deviceId'")
                    null
                }
            } catch (e: Exception) {
                Logger.log("LIVETRACKING_POPUP", LogLevel.ERROR, "Failed to extract aircraft data for popup: ${e.message}")
                null
            }
        }

        // Use generic swipeable popup component
        val config = SwipeablePopupConfig(
            heightRatio = 0.5f,
            swipeThresholdDp = 120,
            containerColor = androidx.compose.ui.graphics.Color.Black,
            containerAlpha = 0.9f,
            logTag = "LIVETRACKING_POPUP"
        )

        // Auto-close popup if aircraft data is no longer available
        if (currentAircraftData == null) {
            Logger.log("LIVETRACKING_POPUP", LogLevel.INFO, "Aircraft data no longer available, auto-closing popup")
            globalState.navigationState.closePopup()
            return
        }

        SwipeablePopup(config = config, onDismiss = onDismiss) {
            // Content composable - isolated from swipe state changes
            PopupCardContent(currentAircraftData, liveTrackingModule, friendlistDeviceIds)
        }
    }


    @Composable
    private fun PopupCardContent(
        aircraft: AircraftFeatureData,
        liveTrackingModule: LiveTrackingModule,
        friendlistDeviceIds: Set<String>
    ) {
        SwipeablePopupContent {

            // Main content area with aircraft information

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Aircraft header: Registration Short and Full Registration (if available)
                    val registrationShort = aircraft.properties["registrationShort"]
                    val registration = aircraft.properties["registration"]
                    val isOnFriendlist = friendlistDeviceIds.contains(aircraft.deviceId)

                    if (registrationShort != null || registration != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Registration Short (ICAO code equivalent) - if available
                            registrationShort?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = androidx.compose.ui.graphics.Color.White
                                )

                                // Heart emoji if on friendlist
                                if (isOnFriendlist) {
                                    Text(
                                        text = " ❤️",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = androidx.compose.ui.graphics.Color.Red
                                    )
                                }

                                // Small padding between short and full registration
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // Full Registration (name equivalent) - if available, takes remaining space
                            registration?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Display aircraft properties in specific order with user-friendly names
                    displayAircraftProperty(aircraft, "aircraftType", "Type")
                    displayAircraftProperty(aircraft, "altitude", "Altitude", "${aircraft.properties["altitude"]} m")
                    displayAircraftProperty(aircraft, "groundSpeed", "Ground speed", "${aircraft.properties["groundSpeed"]} km/h")
                    displayVerticalSpeedProperty(aircraft)
                    displayAircraftProperty(aircraft, "track", "Track", "${aircraft.properties["track"]} °")
                    displayLastPositionProperty(aircraft)
                    displayAircraftProperty(aircraft, "receiverName", "Receiver Name")

                    // Friendlist add/remove button
                    Spacer(modifier = Modifier.height(16.dp))
                    val scope = rememberCoroutineScope()
                    StandardButton(
                        text = if (isOnFriendlist) "Remove from Friendlist" else "Add to Friendlist",
                        enabled = true,
                        icon = if (isOnFriendlist) AppIcons.Close() else AppIcons.Check(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            Logger.log("LIVETRACKING_FRIENDLIST", LogLevel.INFO, "${if (isOnFriendlist) "Removing" else "Adding"} aircraft ${aircraft.deviceId} ${if (isOnFriendlist) "from" else "to"} friendlist")
                            scope.launch {
                                if (isOnFriendlist) {
                                    liveTrackingModule.removeFromFriendlist(aircraft.deviceId)
                                } else {
                                    liveTrackingModule.addToFriendlist(aircraft.deviceId)
                                }
                            }
                        }
                    )

                    // Bottom padding for phones with rounded corners (2 empty lines)
                    Spacer(modifier = Modifier.height(32.dp))
                }
        }
    }
}

/**
 * Row for displaying aircraft information (label left-aligned, value right-aligned)
 */
@Composable
private fun InfoRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Cyan) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Display a simple aircraft property
 */
@Composable
private fun displayAircraftProperty(aircraft: org.mountaincircles.app.modules.livetracking.logic.data.AircraftFeatureData, propertyKey: String, displayName: String, customValue: String? = null) {
    val value = customValue ?: aircraft.properties[propertyKey]
    if (!value.isNullOrEmpty()) {
        InfoRow(displayName, value)
    }
}

/**
 * Display vertical speed with color coding (green for positive, red for negative)
 */
@Composable
private fun displayVerticalSpeedProperty(aircraft: org.mountaincircles.app.modules.livetracking.logic.data.AircraftFeatureData) {
    val vzValue = aircraft.properties["verticalSpeed"]?.toFloatOrNull()
    if (vzValue != null) {
        val color = when {
            vzValue > 0 -> androidx.compose.ui.graphics.Color.Green
            vzValue < 0 -> androidx.compose.ui.graphics.Color.Red
            else -> androidx.compose.ui.graphics.Color.Cyan
        }
        InfoRow("Vz", "${vzValue} m/s", color)
    }
}

/**
 * Display last position time as time ago
 */
@Composable
private fun displayLastPositionProperty(aircraft: org.mountaincircles.app.modules.livetracking.logic.data.AircraftFeatureData) {
    Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "🎯 displayLastPositionProperty recomposing")

    // Update every second for live time display
    var updateTrigger by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000) // Update every second
            updateTrigger = currentTimeMillis()
        }
    }

    val lastTime = aircraft.properties["lastTime"]
    Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "   lastTime value: '$lastTime'")

    if (!lastTime.isNullOrEmpty()) {
        Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "   lastTime is not empty, calculating time ago")
        // Parse HH:MM:SS format and calculate time ago (recomposes every second)
        val timeAgoText = remember(updateTrigger) {
            try {
            val parts = lastTime.split(":")
            if (parts.size == 3) {
                val hours = parts[0].toInt()
                val minutes = parts[1].toInt()
                val seconds = parts[2].toInt()

                // Calculate total seconds since midnight for this time
                val lastTimeSeconds = hours * 3600 + minutes * 60 + seconds

                // Get current time in UTC
                val now = Clock.System.now()
                val currentTimeSeconds = now.toEpochMilliseconds() / 1000 % 86400 // seconds since midnight UTC

                // Calculate difference
                val diffSeconds = (currentTimeSeconds - lastTimeSeconds + 86400) % 86400 // handle wraparound

                when {
                    diffSeconds < 60 -> "${diffSeconds}s ago"
                    diffSeconds < 3600 -> {
                        val mins = diffSeconds / 60
                        val secs = diffSeconds % 60
                        if (secs > 0) "${mins}mn ${secs}s ago" else "${mins}mn ago"
                    }
                    else -> {
                        val hours = diffSeconds / 3600
                        val mins = (diffSeconds % 3600) / 60
                        if (mins > 0) "${hours}h ${mins}mn ago" else "${hours}h ago"
                    }
                }
            } else {
                lastTime // fallback if format is unexpected
            }
        } catch (e: Exception) {
            // Fallback to showing raw time
            lastTime
        }
        }

        Logger.log("LIVETRACKING_POPUP", LogLevel.DEBUG, "   displaying InfoRow with timeAgoText: '$timeAgoText'")
        InfoRow("Last position", timeAgoText)
    }
}