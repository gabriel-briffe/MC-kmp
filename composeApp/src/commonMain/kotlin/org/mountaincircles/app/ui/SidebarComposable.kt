package org.mountaincircles.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleUIState
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.state.NavigationState
import org.mountaincircles.app.ui.components.CollapsibleSidebarWidget
import org.mountaincircles.app.ui.components.LocalForcedExpansion
import org.mountaincircles.app.ui.components.SubmenuTheme
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.location.UserLocationState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SidebarComposable(
    navigationState: NavigationState,
    globalState: GlobalState,
    locationProvider: LocationProvider?,
    locationState: UserLocationState?,
    modifier: Modifier = Modifier
) {
    val sidebarVisible by navigationState.sidebarVisible.collectAsState()
    val sidebarTargetModule by navigationState.sidebarTargetModule.collectAsState()
    val availableModules by globalState.moduleManager.modulesAvailableForUI.collectAsState()
    val configuration = LocalConfiguration.current
    val sidebarWidth = (configuration.screenWidthDp * 0.5f).dp // 50% of screen width
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Function to scroll to a specific module
    val scrollToModule = { moduleIndex: Int ->
        coroutineScope.launch {
            lazyListState.animateScrollToItem(moduleIndex)
        }
    }

    // Handle target module expansion and scrolling
    val modulesWithWidgets = remember(availableModules) {
        availableModules.filter { it.hasSidebarWidget }.sortedByDescending { it.sidebarWidgetOrder }
    }

    // Track expansion state for each module generically
    val moduleExpansionStates = remember(modulesWithWidgets) {
        modulesWithWidgets.associate { it.moduleId to mutableStateOf(false) }
    }

    // Handle target module - expand and scroll
    LaunchedEffect(sidebarTargetModule) {
        sidebarTargetModule?.let { targetModuleId ->
            Logger.log("UI", LogLevel.INFO, "SidebarComposable: Processing target module '$targetModuleId'")

            // Find the target module index
            val targetIndex = modulesWithWidgets.indexOfFirst { it.moduleId == targetModuleId }

            if (targetIndex >= 0) {
                // First collapse all modules, then expand only the target
                moduleExpansionStates.forEach { (_, state) ->
                    state.value = false
                }
                // Expand the target module
                moduleExpansionStates[targetModuleId]?.value = true

                // Scroll to the target section (+1 for geolocation widget)
                scrollToModule(targetIndex + 1)
                Logger.log("UI", LogLevel.INFO, "SidebarComposable: Expanded and scrolled to target module '$targetModuleId' at index ${targetIndex + 1}")

                // Clear the target after processing
                navigationState.clearSidebarTarget()
            } else {
                Logger.log("UI", LogLevel.WARN, "SidebarComposable: Target module '$targetModuleId' not found in sidebar widgets")
                navigationState.clearSidebarTarget()
            }
        }
    }

    // Collapse all modules when sidebar opens without a target
    LaunchedEffect(sidebarVisible, sidebarTargetModule) {
        if (sidebarVisible && sidebarTargetModule == null) {
            Logger.log("UI", LogLevel.INFO, "SidebarComposable: Sidebar opened without target, collapsing all modules")
            moduleExpansionStates.forEach { (_, state) ->
                state.value = false
            }
        }
    }

    AnimatedVisibility(
        visible = sidebarVisible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        // Sidebar content only - no overlay dimming
        Card(
            modifier = Modifier
                .width(sidebarWidth)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.9f)),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 8.dp,
                bottomEnd = 8.dp,
                bottomStart = 0.dp
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // modulesWithWidgets is already computed above

                // Module controls content area - using LazyColumn for scroll-to-section functionality
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Top
                ) {
                    // Geolocation widget at the top (index 0)
                    item {
                        Box(modifier = Modifier.padding(bottom = 16.dp)) {
                            GeolocationWidget(
                                globalState = globalState,
                                locationProvider = locationProvider,
                                locationState = locationState
                            )
                        }
                    }

                    // Render module widgets in priority order
                    itemsIndexed(modulesWithWidgets, key = { _, module -> module.moduleId }) { index, module ->
                        Box(modifier = Modifier.padding(bottom = 16.dp)) {
                            // Provide forced expansion state via CompositionLocal
                            CompositionLocalProvider(
                                LocalForcedExpansion provides (moduleExpansionStates[module.moduleId]?.value ?: false)
                            ) {
                                module.SidebarWidget(onExpanded = null)
                            }
                        }
                    }

                    // Show placeholder if no modules with widgets are available
                    if (modulesWithWidgets.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Module controls\nwill appear here",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GeolocationWidget(
    globalState: GlobalState,
    locationProvider: LocationProvider?,
    locationState: UserLocationState?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // State for geolocation controls from global state
    val circleRadius by globalState.circleRadiusKm.collectAsState()
    val vectorLength by globalState.vectorLengthKm.collectAsState()

    // Location services state - reactive to parameter changes
    val hasLocationServices by remember(locationState) {
        derivedStateOf { locationState != null }
    }

    CollapsibleSidebarWidget(
        title = "Geolocation",
        modifier = modifier,
        initiallyExpanded = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Show either placeholder or sliders based on location services availability
            if (!hasLocationServices) {
                // No location services - show placeholder only
                Text(
                    text = "no location",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                // Location services available - show sliders only
                // Circle radius slider (1km steps with blue markers)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Circle Radius: ",
                            color = SubmenuTheme.Colors.textPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${circleRadius.toInt()}",
                            color = SubmenuTheme.Colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "km",
                            color = SubmenuTheme.Colors.textPrimary,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = circleRadius,
                        onValueChange = {
                            val intValue = it.roundToInt()
                            // Logger.log("GEOLOCATION_SLIDER", LogLevel.DEBUG, "Circle radius slider moved to: ${intValue}km")
                            // Launch coroutine to save settings
                            scope.launch {
                                globalState.updateCircleRadius(intValue.toFloat())
                            }
                        },
                        valueRange = 0f..20f, // 0km to 20km (0 = disabled)
                        steps = 20, // 20 steps for 21 values (0 to 20 inclusive) - shows step markers
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = SubmenuTheme.Colors.primary,
                            activeTrackColor = SubmenuTheme.Colors.primary,
                            inactiveTrackColor = SubmenuTheme.Colors.iconDisabled,
                            activeTickColor = SubmenuTheme.Colors.primary,
                            inactiveTickColor = SubmenuTheme.Colors.primary.copy(alpha = 0.5f)
                        )
                    )
                }

                // Vector (bearing line) length slider (1km steps with blue markers)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Vector: ",
                            color = SubmenuTheme.Colors.textPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${vectorLength.toInt()}",
                            color = SubmenuTheme.Colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "km",
                            color = SubmenuTheme.Colors.textPrimary,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = vectorLength,
                        onValueChange = {
                            val intValue = it.roundToInt()
                            // Logger.log("GEOLOCATION_SLIDER", LogLevel.DEBUG, "Vector length slider moved to: ${intValue}km")
                            // Launch coroutine to save settings
                            scope.launch {
                                globalState.updateVectorLength(intValue.toFloat())
                            }
                        },
                        valueRange = 0f..50f, // 0km to 50km (0 = disabled)
                        steps = 50, // 50 steps for 51 values (0 to 50 inclusive) - shows step markers
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = SubmenuTheme.Colors.primary,
                            activeTrackColor = SubmenuTheme.Colors.primary,
                            inactiveTrackColor = SubmenuTheme.Colors.iconDisabled,
                            activeTickColor = SubmenuTheme.Colors.primary,
                            inactiveTickColor = SubmenuTheme.Colors.primary.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    }
}
