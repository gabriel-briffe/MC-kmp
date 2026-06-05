package org.mountaincircles.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.ui.components.GenericBottomSheet
import org.mountaincircles.app.ui.components.BottomSheetConfigs
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

// Platform-specific map capture function
expect suspend fun captureCurrentMapView(context: Any? = null)

// Platform-specific Windy meteogram capture function
expect suspend fun captureWindyMeteogramPoint(context: Any?)

// Platform-specific location name fetching function
expect suspend fun fetchLocationName(latitude: Double, longitude: Double): String

/**
 * Simple Widget sheet - displays widget configuration options
 */
@Composable
fun WidgetComposable(onDismiss: () -> Unit = {}) {
    GenericBottomSheet(
        visible = true, // Always visible when called
        onDismiss = {
            Logger.log("UI", LogLevel.INFO, "Widget sheet dismissed")
            onDismiss() // Notify parent to reset state
        },
        config = BottomSheetConfigs.WidgetSheet, // Use dedicated widget config
        content = {
            WidgetContent()
        }
    )
}

/**
 * Widget content - widget configuration with map capture
 */
@Composable
private fun WidgetContent() {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val meteogramButtonText = remember { mutableStateOf("Use center of map as meteogram widget location") }
    val waveButtonText = remember { mutableStateOf("Use this map view for the wave widget") }
    val includeSkySightInMeteogram = remember { mutableStateOf(false) }
    val thermalWindowKm = remember { mutableStateOf("100") }
    val waveWindowKm = remember { mutableStateOf("200") }
    val widgetLocationInfo = remember { mutableStateOf<Pair<String?, Pair<Double, Double>?>>(Pair(null, null)) }

    // Check existing metadata for SkySight credentials, window values, and location info on composable launch
    LaunchedEffect(Unit) {
        try {
            val hasSkySightInMetadata = checkSkySightCredentialsInWidgetMetadata(context)
            includeSkySightInMeteogram.value = hasSkySightInMetadata

            val (thermalWindow, waveWindow) = getSkySightWindowValues(context)
            thermalWindowKm.value = thermalWindow.toString()
            waveWindowKm.value = waveWindow.toString()

            val locationInfo = getWidgetLocationInfo(context)
            widgetLocationInfo.value = locationInfo

            Logger.log("UI", LogLevel.DEBUG, "📊 Widget composable initialized with SkySight ${if (hasSkySightInMetadata) "enabled" else "disabled"} based on metadata")
            Logger.log("UI", LogLevel.DEBUG, "📏 Window values - Thermal: ${thermalWindow}km, Wave: ${waveWindow}km")
            Logger.log("UI", LogLevel.DEBUG, "📍 Location info - Name: ${locationInfo.first}, Coords: ${locationInfo.second?.let { "${it.first}, ${it.second}" } ?: "none"}")
        } catch (e: Exception) {
            Logger.log("UI", LogLevel.ERROR, "❌ Failed to check widget metadata: ${e.message}", e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "Widget Configuration",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        // Android settings instructions for widgets
        Text(
            text = "For the widgets to work well, go to the android settings, apps, mountainCircles:\n" +
                  "- allow background data\n" +
                  "- unlimited data\n" +
                  "then battery:\n" +
                  "- allow background\n" +
                  "- no restrictions\n" + 
                  "And remove battery saver if manual update is not working",
            fontSize = 14.sp,
            color = Color.Yellow,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Wave widget explanation
        Text(
            text = "the wave widget updates every 24hrs",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        
        // Wave forecast capture button
        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        // Show downloading status
                        waveButtonText.value = "downloading opentopo background"
                        Logger.log("WIDGET", LogLevel.INFO, "Starting map view capture for wave widget")

                        captureCurrentMapView(context)

                        // Show success status
                        waveButtonText.value = "saved window"
                        Logger.log("WIDGET", LogLevel.INFO, "Map view captured successfully for wave widget")

                        // Reset after 3 seconds
                        kotlinx.coroutines.delay(3000)
                        waveButtonText.value = "Use this map view for the wave widget"

                    } catch (e: Exception) {
                        // Show failure status
                        waveButtonText.value = "failed"
                        Logger.log("WIDGET", LogLevel.ERROR, "Failed to capture map view: ${e.message}", e)

                        // Reset after 3 seconds
                        kotlinx.coroutines.delay(3000)
                        waveButtonText.value = "Use this map view for the wave widget"
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Cyan.copy(alpha = 0.8f),
                contentColor = Color.Black
            )
        ) {
            Text(waveButtonText.value)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Meteogram widget explanation
        Text(
            text = "the meteogram widget gets the best forecast depending on location and time ahead from open-meteo and updates twice a day",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        // Display current widget location info if available
        val (locationName, coords) = widgetLocationInfo.value
        if (locationName != null || coords != null) {
            val locationText = buildString {
                append("Meteogram set up for ")
                if (locationName != null && locationName.isNotEmpty()) {
                    append(locationName)
                } else {
                    append("location")
                }
                if (coords != null) {
                    append(" - ${String.format("%.4f", coords.first)}°, ${String.format("%.4f", coords.second)}°")
                }
            }

            Text(
                text = locationText,
                fontSize = 14.sp,
                color = Color.Yellow,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium
            )
        }

    // SkySight integration option for meteogram if credentials are present in metadata
        val globalState = org.mountaincircles.app.state.getGlobalState()
        val skySightModule = globalState.moduleManager.getModule("skysight") as? org.mountaincircles.app.modules.skysight.SkysightModule
        val skySightState = skySightModule?.skysightState?.value
        val hasSkySightCredentials = skySightState?.let { it.email.isNotEmpty() && it.password.isNotEmpty() } ?: false

        if (hasSkySightCredentials) {
            // Checkbox for including SkySight data
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Checkbox(
                    checked = includeSkySightInMeteogram.value,
                    onCheckedChange = { checked ->
                        includeSkySightInMeteogram.value = checked
                        // Handle SkySight credentials storage/removal
                        coroutineScope.launch {
                            handleSkySightCredentialsForWidget(context, checked, skySightState)
                        }
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.Cyan,
                        uncheckedColor = Color.Gray
                    )
                )
                Text(
                    text = "Add SkySight to meteogram",
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // SkySight data range configuration
            Text(
                text = "Data fetched around selected location:",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Thermal window controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Minus button
                Button(
                    onClick = {
                        val currentValue = thermalWindowKm.value.toIntOrNull() ?: 100
                        val newValue = (currentValue - 10).coerceAtLeast(10) // Minimum 10km
                        thermalWindowKm.value = newValue.toString()
                        coroutineScope.launch {
                            val waveKm = waveWindowKm.value.toIntOrNull() ?: 200
                            saveSkySightWindowValues(context, newValue, waveKm)
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.8f),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                // Value display
                Text(
                    text = "${thermalWindowKm.value}km for PFD, thermal height 14h, best vario 14h",
                    fontSize = 12.sp,
                    color = Color.Cyan,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )

                // Plus button
                Button(
                    onClick = {
                        val currentValue = thermalWindowKm.value.toIntOrNull() ?: 100
                        val newValue = (currentValue + 10).coerceAtMost(500) // Maximum 500km
                        thermalWindowKm.value = newValue.toString()
                        coroutineScope.launch {
                            val waveKm = waveWindowKm.value.toIntOrNull() ?: 200
                            saveSkySightWindowValues(context, newValue, waveKm)
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Green.copy(alpha = 0.8f),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Wave window controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Minus button
                Button(
                    onClick = {
                        val currentValue = waveWindowKm.value.toIntOrNull() ?: 200
                        val newValue = (currentValue - 10).coerceAtLeast(10) // Minimum 10km
                        waveWindowKm.value = newValue.toString()
                        coroutineScope.launch {
                            val thermalKm = thermalWindowKm.value.toIntOrNull() ?: 100
                            saveSkySightWindowValues(context, thermalKm, newValue)
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.8f),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                // Value display
                Text(
                    text = "${waveWindowKm.value}km for wave 4000m best vario 6h 12h 18h",
                    fontSize = 12.sp,
                    color = Color.Cyan,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )

                // Plus button
                Button(
                    onClick = {
                        val currentValue = waveWindowKm.value.toIntOrNull() ?: 200
                        val newValue = (currentValue + 10).coerceAtMost(500) // Maximum 500km
                        waveWindowKm.value = newValue.toString()
                        coroutineScope.launch {
                            val thermalKm = thermalWindowKm.value.toIntOrNull() ?: 100
                            saveSkySightWindowValues(context, thermalKm, newValue)
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Green.copy(alpha = 0.8f),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // Text prompting to login to SkySight
            Text(
                text = "Login into SkySight for meteogram option",
                fontSize = 14.sp,
                color = Color.Yellow,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 18.sp
            )
        }

        // Windy meteogram capture button
        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        // Get current camera position
                        val globalState = org.mountaincircles.app.state.getGlobalState()
                        val cameraState = globalState.currentCameraState.value
                        if (cameraState != null) {
                            val position = cameraState.position

                            // Fetch location name and show it on button
                            val locationName = fetchLocationName(position.target.latitude, position.target.longitude)
                            meteogramButtonText.value = "saved $locationName"
                        }

                        captureWindyMeteogramPoint(context)
                        Logger.log("WIDGET", LogLevel.INFO, "Windy meteogram point captured")

                        // Refresh location info after capturing new point
                        try {
                            val updatedLocationInfo = getWidgetLocationInfo(context)
                            widgetLocationInfo.value = updatedLocationInfo
                            Logger.log("UI", LogLevel.DEBUG, "📍 Refreshed location info after capture - Name: ${updatedLocationInfo.first}, Coords: ${updatedLocationInfo.second?.let { "${it.first}, ${it.second}" } ?: "none"}")
                        } catch (e: Exception) {
                            Logger.log("UI", LogLevel.ERROR, "❌ Failed to refresh location info after capture: ${e.message}", e)
                        }
                    } catch (e: Exception) {
                        Logger.log("WIDGET", LogLevel.ERROR, "Failed to capture Windy meteogram point: ${e.message}", e)
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Green.copy(alpha = 0.8f),
                contentColor = Color.Black
            )
        ) {
            Text(meteogramButtonText.value)
        }

        

        // Auto-reset meteogram button text after 3 seconds
        LaunchedEffect(meteogramButtonText.value) {
            if (meteogramButtonText.value != "Use center of map as meteogram widget location") {
                kotlinx.coroutines.delay(3000)
                meteogramButtonText.value = "Use center of map as meteogram widget location"
            }
        }

    }
}

// Platform-specific function to handle SkySight credentials for widget
expect suspend fun handleSkySightCredentialsForWidget(context: Any?, includeSkySight: Boolean, skySightState: org.mountaincircles.app.modules.skysight.logic.data.SkysightState?)

// Platform-specific function to check if SkySight credentials exist in widget metadata
expect suspend fun checkSkySightCredentialsInWidgetMetadata(context: Any?): Boolean

// Platform-specific function to get SkySight window values from widget metadata
expect suspend fun getSkySightWindowValues(context: Any?): Pair<Int, Int>

// Platform-specific function to save SkySight window values to widget metadata
expect suspend fun saveSkySightWindowValues(context: Any?, thermalWindow: Int, waveWindow: Int)

// Platform-specific function to get location info from widget metadata
expect suspend fun getWidgetLocationInfo(context: Any?): Pair<String?, Pair<Double, Double>?>