package org.mountaincircles.app.modules.livetracking.import.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.modules.livetracking.LiveTrackingModule
import org.mountaincircles.app.ui.modules.ScrollableComposableProvider
import org.mountaincircles.app.modules.livetracking.logic.data.FriendEntry
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.utils.currentTimeMillis

/**
 * Provider for LiveTracking module sheet UI
 */
class LiveTrackingSheetProvider : ScrollableComposableProvider {
    override val moduleId: String = "livetracking"
    override val supportsLazyScrolling: Boolean = true

    override fun canProvide(module: ModuleBase): Boolean = module is LiveTrackingModule

    @Composable
    override fun provideComposable(module: ModuleBase, onScrollToTop: (() -> Unit)?) {
        if (module is LiveTrackingModule) {
            val state by module.liveTrackingState.collectAsState()
            val friendList = state.friendlist
            val density = LocalDensity.current
            val configuration = LocalConfiguration.current
            val screenWidthDp = configuration.screenWidthDp.toFloat()
            val deleteThreshold = -screenWidthDp / 2 // Half screen width drag to delete

            // State for editing
            var editingDeviceId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            var editingText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

            // State for swipe to delete
            var swipingCard by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            var swipeOffset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

            // Lazy list state for autoscroll
            val lazyListState = rememberLazyListState()

            // Autoscroll to editing friend
            LaunchedEffect(editingDeviceId) {
                editingDeviceId?.let { deviceId ->
                    val index = friendList.indexOfFirst { it.deviceId == deviceId }
                    if (index >= 0) {
                        lazyListState.animateScrollToItem(index, scrollOffset = 0)
                    }
                }
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .imePadding()
            ) {
                // Shortcut descriptive text
                item {
                    Text(
                        text = "Shortcut: long click on glider top menu icon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                // Centered descriptive text
                item {
                    Text(
                        text = "long click to rename friend, click on target icon to zoom to friend",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

                if (friendList.isEmpty()) {
                    item {
                        Text(
                            text = "No friends added yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    items(friendList) { friend ->
                        val isSwipingThisCard = swipingCard == friend.deviceId // dp

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .offset(x = if (isSwipingThisCard) swipeOffset.dp else 0.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        // Start editing this friend
                                        editingDeviceId = friend.deviceId
                                        editingText = friend.customName
                                    }
                                )
                                .pointerInput(friend.deviceId) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            swipingCard = friend.deviceId
                                            swipeOffset = 0f
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            // Convert pixels to dp for consistent movement across densities
                                            val dragAmountDp = with(density) { dragAmount.toDp().value }
                                            swipeOffset = (swipeOffset + dragAmountDp).coerceAtMost(0f)
                                        },
                                        onDragEnd = {
                                            if (swipeOffset < deleteThreshold) {
                                                // Delete the friend
                                                module.removeFromFriendlist(friend.deviceId)
                                            }
                                            // Reset swipe state
                                            swipingCard = null
                                            swipeOffset = 0f
                                        },
                                        onDragCancel = {
                                            swipingCard = null
                                            swipeOffset = 0f
                                        }
                                    )
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSwipingThisCard && swipeOffset < deleteThreshold)
                                    Color.Red.copy(alpha = 0.3f) else CardDefaults.cardColors().containerColor
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 16.dp)
                                ) {
                                    // Update trigger for live time display in friend cards
                                    var updateTrigger by remember { mutableStateOf(0L) }

                                    LaunchedEffect(Unit) {
                                        while (true) {
                                            kotlinx.coroutines.delay(1000) // Update every second
                                            updateTrigger = currentTimeMillis()
                                        }
                                    }
                                    if (editingDeviceId == friend.deviceId) {
                                        // Show text field when editing
                                        androidx.compose.material3.TextField(
                                            value = editingText,
                                            onValueChange = { editingText = it },
                                            singleLine = true,
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                            ),
                                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                                onDone = {
                                                    // Save the new name
                                                    module.updateFriendName(friend.deviceId, editingText)
                                                    editingDeviceId = null
                                                    editingText = ""
                                                }
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        // Show text when not editing
                                    Text(
                                        text = friend.customName,
                                        fontSize = 20.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    Text(
                                        text = if (friend.registrationShort.isNotEmpty() && friend.registration.isNotEmpty()) {
                                            "${friend.registrationShort} - ${friend.registration}"
                                        } else {
                                            friend.deviceId // Fallback to deviceId if registration info is missing
                                        },
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )

                                    // Show real-time aircraft data if available
                                    val aircraftFeature = state.aircraftFeatures[friend.deviceId]
                                    if (aircraftFeature != null) {
                                        val properties = aircraftFeature.properties
                                        val altitude = when (val prop = properties["altitude"]) {
                                            is kotlinx.serialization.json.JsonPrimitive -> if (prop.isString) prop.content else prop.toString()
                                            else -> prop?.toString()
                                        }?.takeIf { it.isNotEmpty() }

                                        // Calculate live time ago (recomposes every second)
                                        val timeAgo = remember(updateTrigger) {
                                            when (val prop = properties["lastTime"]) {
                                                is kotlinx.serialization.json.JsonPrimitive -> if (prop.isString) prop.content else prop.toString()
                                                else -> prop?.toString()
                                            }?.let { lastTime ->
                                                try {
                                                    val parts = lastTime.split(":")
                                                    if (parts.size == 3) {
                                                        val hours = parts[0].toInt()
                                                        val minutes = parts[1].toInt()
                                                        val seconds = parts[2].toInt()

                                                        // Calculate total seconds since midnight for this time
                                                        val lastTimeSeconds = hours * 3600 + minutes * 60 + seconds

                                                        // Get current time in UTC
                                                        val now = java.time.Instant.now()
                                                        val utcZonedDateTime = java.time.ZonedDateTime.ofInstant(now, java.time.ZoneOffset.UTC)
                                                        val currentHour = utcZonedDateTime.hour
                                                        val currentMinute = utcZonedDateTime.minute
                                                        val currentSecond = utcZonedDateTime.second

                                                        // Calculate current total seconds since midnight
                                                        val currentTimeSeconds = currentHour * 3600 + currentMinute * 60 + currentSecond

                                                        // Calculate difference
                                                        val diffSeconds = currentTimeSeconds - lastTimeSeconds
                                                        val absDiffSeconds = kotlin.math.abs(diffSeconds)

                                                        // Format as human readable
                                                        when {
                                                            absDiffSeconds < 60 -> "${absDiffSeconds}s ago"
                                                            absDiffSeconds < 3600 -> "${absDiffSeconds / 60}m ${absDiffSeconds % 60}s ago"
                                                            else -> "${absDiffSeconds / 3600}h ${(absDiffSeconds % 3600) / 60}m ago"
                                                        }
                                                    } else null
                                                } catch (e: Exception) {
                                                    null
                                                }
                                            }
                                        }

                                        val groundSpeed = when (val prop = properties["groundSpeed"]) {
                                            is kotlinx.serialization.json.JsonPrimitive -> if (prop.isString) prop.content else prop.toString()
                                            else -> prop?.toString()
                                        }?.takeIf { it.isNotEmpty() }

                                        val verticalSpeed = when (val prop = properties["verticalSpeed"]) {
                                            is kotlinx.serialization.json.JsonPrimitive -> if (prop.isString) prop.content else prop.toString()
                                            else -> prop?.toString()
                                        }?.takeIf { it.isNotEmpty() }

                                        if (altitude != null || timeAgo != null || groundSpeed != null || verticalSpeed != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (altitude != null) {
                                                    Text(
                                                        text = "${altitude}m",
                                                        fontSize = 16.sp,
                                                        color = Color.Cyan,
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                                    )
                                                }
                                                if (groundSpeed != null) {
                                                    Text(
                                                        text = "${groundSpeed}km/h",
                                                        fontSize = 16.sp,
                                                        color = Color.Cyan,
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                                    )
                                                }
                                                if (verticalSpeed != null) {
                                                    Text(
                                                        text = "${verticalSpeed}m/s",
                                                        fontSize = 16.sp,
                                                        color = Color.Cyan,
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                                    )
                                                }
                                                if (timeAgo != null) {
                                                    Text(
                                                        text = timeAgo,
                                                        fontSize = 16.sp,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    }
                                }

                                // Show spot icon button if friend is currently visible on map
                                val isVisibleOnMap = state.aircraftFeatures.containsKey(friend.deviceId)
                                Logger.log("SPOT_ICON_VISIBILITY", LogLevel.DEBUG, "Friend ${friend.deviceId}: aircraftFeatures contains deviceId=$isVisibleOnMap")
                                if (isVisibleOnMap) {
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            Logger.log("FRIEND_SPOT_CLICK", LogLevel.INFO, "Spot clicked for friend ${friend.deviceId}")
                                            // Move camera to friend's location
                                            val coords = module.getAircraftCoordinates(friend.deviceId)
                                            Logger.log("FRIEND_SPOT_CLICK", LogLevel.INFO, "Coordinates for ${friend.deviceId}: $coords")
                                            coords?.let { (longitude, latitude) ->
                                                Logger.log("FRIEND_SPOT_CLICK", LogLevel.INFO, "Moving camera to $longitude, $latitude")
                                                module.moveCameraToAircraft(longitude, latitude)
                                                // Close the import sheet immediately after moving camera
                                                org.mountaincircles.app.state.getGlobalState().navigationState.closeImportSheet()
                                                Logger.log("FRIEND_SPOT_CLICK", LogLevel.INFO, "Import sheet closed after zooming to friend")
                                            } ?: Logger.log("FRIEND_SPOT_CLICK", LogLevel.WARN, "No coordinates found for ${friend.deviceId}")
                                        },
                                        modifier = Modifier
                                            .size(72.dp)
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        androidx.compose.material3.Icon(
                                            painter = AppIcons.Spot(),
                                            contentDescription = "Zoom to friend",
                                            tint = androidx.compose.ui.graphics.Color.Cyan,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun provideScrollTrigger(onTrigger: () -> Unit) {
        // LiveTracking handles scrolling internally with LazyColumn
    }

    @Composable
    override fun provideFullWidthContent(module: ModuleBase): List<@Composable () -> Unit> {
        return emptyList()
    }
}