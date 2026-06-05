package org.mountaincircles.app.modules.airports.overlay.ui

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
import org.mountaincircles.app.modules.airports.AirportsModule
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.modules.airports.logic.data.AirportFeatureData
import org.mountaincircles.app.ui.overlay.SwipeablePopup
import org.mountaincircles.app.ui.overlay.SwipeablePopupConfig
import org.mountaincircles.app.ui.overlay.SwipeablePopupContent
import org.mountaincircles.app.modules.airports.logic.data.AirportsPopupDisplayData
import org.mountaincircles.app.modules.airports.logic.data.FrequencyData
import org.mountaincircles.app.modules.airports.logic.data.RunwayData
import org.mountaincircles.app.ui.components.StandardButton
import org.mountaincircles.app.ui.overlay.OverlayProvider
import org.mountaincircles.app.ui.overlay.OverlayPosition
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.modules.airports.overlay.loadAirportPicture
import org.mountaincircles.app.modules.airports.overlay.getAirportPictureAbsolutePath
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.mountaincircles.app.state.PopupId

/**
 * Airports module popup overlay provider
 * Shows airport information when airport popup is triggered by clicking
 * Popup can be dismissed by swiping left on the popup content
 */
class AirportsPopupOverlay : OverlayProvider {
    override val moduleId = "airports"
    override val priority = 15  // Lower priority than airspace (20) so airspace appears above
    override val position = OverlayPosition.BOTTOM_CENTER

    @Composable
    override fun OverlayContent(module: ModuleBase, globalState: GlobalState) {
        Logger.log("AIRPORTS_POPUP", LogLevel.DEBUG, "🎯 AirportsPopupOverlay.OverlayContent called")

        val airportsModule = remember(module) { module as AirportsModule }

        // ✅ Observe centralized popup state (exclusive like submenus)
        val activePopup by globalState.navigationState.popupVisible.collectAsState()

        // Check if this airport popup should be shown
        val showPopup = activePopup?.moduleId == moduleId && activePopup?.dataId != null

        Logger.log("AIRPORTS_POPUP", LogLevel.DEBUG, "   activePopup: ${activePopup?.moduleId}:${activePopup?.dataId}")
        Logger.log("AIRPORTS_POPUP", LogLevel.DEBUG, "   showPopup: $showPopup")

        if (showPopup) {
            val airportId = activePopup?.dataId
            Logger.log("AIRPORTS_POPUP", LogLevel.INFO, "🎯 Showing airports popup overlay for airport: $airportId")

            // Get airport data from module state (this will be reactive)
            val airportsState by airportsModule.airportsState.collectAsState()
            val popupFeatures = airportsState.popupFeatures

            if (popupFeatures.isNotEmpty()) {
                AirportsPopupContent(
                    airportsModule = airportsModule,
                    features = popupFeatures,
                onDismiss = {
                    Logger.log("AIRPORTS_POPUP", LogLevel.INFO, "🔴 OVERLAY onDismiss called")
                    globalState.navigationState.closePopup()
                    airportsModule.hideAirportPopup() // Also update module state
                    Logger.log("AIRPORTS_POPUP", LogLevel.INFO, "✅ OVERLAY onDismiss completed - airport popup closed")
                }
                )
            } else {
                Logger.log("AIRPORTS_POPUP", LogLevel.DEBUG, "🎯 Airport popup not shown - no features available")
            }
        } else {
            Logger.log("AIRPORTS_POPUP", LogLevel.DEBUG, "🎯 Airport popup not shown - not active popup")
        }
    }

    /**
     * Airports popup content component
     * Displays airport information in a simple card with swipe-to-dismiss
     */
    @Composable
    private fun AirportsPopupContent(
        airportsModule: AirportsModule,
        features: List<AirportFeatureData>,
        onDismiss: () -> Unit
    ) {
        Logger.log("AIRPORTS_POPUP", LogLevel.DEBUG, "   Building airports popup with ${features.size} features")

        // Use generic swipeable popup component
        val config = SwipeablePopupConfig(
            heightRatio = 0.5f,
            swipeThresholdDp = 120,
            containerColor = androidx.compose.ui.graphics.Color.Black,
            containerAlpha = 0.9f,
            logTag = "AIRPORTS_POPUP"
        )

        SwipeablePopup(config = config, onDismiss = onDismiss) {
            // Content composable - isolated from swipe state changes
            PopupCardContent(airportsModule, features)
        }
    }


    @Composable
    private fun PopupCardContent(
        airportsModule: AirportsModule,
        features: List<AirportFeatureData>
    ) {
        SwipeablePopupContent {

            // Main content area with airport information
            if (features.isNotEmpty()) {
                val airport = features.first()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Airport header: ICAO Code and Name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ICAO Code (left aligned) - only show if available
                        if (airport.icaoCode != null && airport.icaoCode != "N/A") {
                            Text(
                                text = airport.icaoCode,
                                style = MaterialTheme.typography.headlineSmall,
                                color = androidx.compose.ui.graphics.Color.White
                            )

                            // Small padding between ICAO code and name
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Name (right aligned in its box) - always present
                        Text(
                            text = airport.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = androidx.compose.ui.graphics.Color.White,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Elevation
                    airport.elevation?.let { elevation ->
                        InfoRow("Elevation", elevation)
                    }

                    // Type
                    InfoRow("Type", airport.type)

                    // Traffic Type
                    if (airport.trafficType.isNotEmpty()) {
                        InfoRow("Traffic", airport.trafficType.joinToString(", "))
                    }

                    // Frequencies
                    if (airport.frequencies.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Frequencies",
                            style = MaterialTheme.typography.titleMedium,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        airport.frequencies.forEach { frequency ->
                            FrequencyRow(frequency)
                        }
                    }

                    // Runways
                    if (airport.runways.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Runways",
                            style = MaterialTheme.typography.titleMedium,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        airport.runways.forEach { runway ->
                            RunwayRow(runway)
                        }
                    }

                    // Description (if available)
                    airport.description?.let { desc ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Pictures (if available)
                    if (airport.pics.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))

                        airport.pics.forEach { picPath ->
                            PictureRow(picPath)
                        }
                    }

                    // Airport enable/disable button
                    Spacer(modifier = Modifier.height(16.dp))
                    val airportsState by airportsModule.airportsState.collectAsState()
                    val isDisabled = airportsState.disabledAirportIds.contains(airport.id)
                    val scope = rememberCoroutineScope()
                    StandardButton(
                        text = if (isDisabled) "Disabled" else "Enabled",
                        enabled = true,
                        icon = if (isDisabled) AppIcons.Close() else AppIcons.Check(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isDisabled) 0.5f else 1.0f),
                        onClick = {
                            Logger.log("AIRPORTS_POPUP", LogLevel.INFO, "${if (isDisabled) "Enabling" else "Disabling"} airport ${airport.icaoCode ?: airport.name}")
                            scope.launch {
                                val result = airportsModule.airportsBusinessService.toggleAirportDisabledState(airport.id)
                                if (result.isSuccess) {
                                    Logger.log("AIRPORTS_POPUP", LogLevel.INFO, "Successfully toggled airport state")
                                } else {
                                    Logger.log("AIRPORTS_POPUP", LogLevel.ERROR, "Failed to toggle airport state: ${result.exceptionOrNull()?.message}")
                                }
                            }
                        }
                    )

                    // Bottom padding for phones with rounded corners (2 empty lines)
                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else {
                // Fallback for empty features
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Airport Information",
                        style = MaterialTheme.typography.headlineSmall,
                        color = androidx.compose.ui.graphics.Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No airport data available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.LightGray
                    )
                }
            }
        }
    }
}

/**
 * Row for displaying airport information (label left-aligned, value right-aligned)
 */
@Composable
private fun InfoRow(label: String, value: String) {
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
            color = androidx.compose.ui.graphics.Color.Cyan,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Row for displaying frequency information (name value)
 */
@Composable
private fun FrequencyRow(frequency: FrequencyData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Primary frequency indicator
        if (frequency.primary) {
            Text(
                text = "★",
                color = androidx.compose.ui.graphics.Color.Yellow,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(22.dp))
        }

        Text(
            text = frequency.name,
            style = MaterialTheme.typography.bodyMedium,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = frequency.value,
            style = MaterialTheme.typography.bodyMedium,
            color = androidx.compose.ui.graphics.Color.Cyan,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Row for displaying runway information (designator composition length x width)
 */
@Composable
private fun RunwayRow(runway: RunwayData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Designator (left aligned)
        Text(
            text = runway.designator,
            style = MaterialTheme.typography.bodyMedium,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.width(40.dp)
        )

        // Composition (center aligned)
        Text(
            text = runway.mainComposite,
            style = MaterialTheme.typography.bodyMedium,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        // Length x Width (right aligned)
        Text(
            text = "${runway.length} × ${runway.width}",
            style = MaterialTheme.typography.bodyMedium,
            color = androidx.compose.ui.graphics.Color.Cyan,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Row for displaying airport pictures
 */
@Composable
private fun PictureRow(picPath: String) {
    var imageBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Load image asynchronously
    LaunchedEffect(picPath) {
        try {
            isLoading = true
            loadError = null

            // Convert relative path to absolute path
            val absolutePath = getAirportPictureAbsolutePath(picPath)
            imageBitmap = loadAirportPicture(absolutePath)
        } catch (e: Exception) {
            loadError = e.message ?: "Unknown error"
            Logger.log("AIRPORTS_POPUP", LogLevel.ERROR, "Failed to load picture $picPath: ${e.message}", e)
        } finally {
            isLoading = false
        }
    }

    // Image display area - full width with dynamic height based on aspect ratio
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                // Loading indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
            loadError != null -> {
                // Error display with placeholder dimensions
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp), // Default height for error state
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "📷",
                            fontSize = 24.sp,
                            color = androidx.compose.ui.graphics.Color.Gray
                        )
                        Text(
                            text = "Failed to load",
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            imageBitmap != null -> {
                // Calculate aspect ratio and display image at full width
                val bitmap = imageBitmap!!
                val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

                Image(
                    bitmap = bitmap,
                    contentDescription = "Airport picture",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio), // Maintain aspect ratio while filling width
                    contentScale = ContentScale.FillWidth // Fill width, height adjusts to maintain aspect ratio
                )
            }
            else -> {
                // Fallback for unexpected state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp), // Default height for fallback
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No image",
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
            }
        }
    }
}

