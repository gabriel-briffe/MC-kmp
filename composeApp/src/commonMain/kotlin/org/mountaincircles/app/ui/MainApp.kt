package org.mountaincircles.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.permissions.PermissionType
import org.mountaincircles.app.permissions.createPermissionManager
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.state.ProvideGlobalState
import org.mountaincircles.app.ui.overlay.MapWithOverlays
import org.mountaincircles.app.ui.locationMarkerSettingsManager
import kotlin.time.Duration.Companion.milliseconds

data class MapState(
    val locationProvider: org.maplibre.compose.location.LocationProvider?,
    val locationState: org.maplibre.compose.location.UserLocationState?
)

@Composable
private fun rememberMapState(hasLocationPermission: Boolean): MapState {
    val permissionManager = remember { createPermissionManager() }
    val locationMarkerSettings by remember { locationMarkerSettingsManager.settings }
        .collectAsState(initial = org.mountaincircles.app.ui.LocationMarkerSettings())

    // ✅ Monitor GPS state changes reactively
    val locationServicesFlow = remember { permissionManager.locationServicesStateFlow() }
    val locationServicesEnabled by locationServicesFlow.collectAsState(initial = permissionManager.isLocationServicesEnabled())

    // ✅ Create location provider conditionally based on permissions
    val canUseLocation = hasLocationPermission && locationServicesEnabled
    val locationProvider = if (canUseLocation) {
        Logger.log("LOCATION", LogLevel.INFO, "Creating app-level location provider (permission: $hasLocationPermission, GPS: $locationServicesEnabled, interval: ${locationMarkerSettings.updateIntervalMs}ms)")
        org.maplibre.compose.location.rememberDefaultLocationProvider(
            updateInterval = locationMarkerSettings.updateIntervalMs.milliseconds,
            desiredAccuracy = org.maplibre.compose.location.DesiredAccuracy.High,
            minDistanceMeters = 0.0
        )
    } else {
        Logger.log("LOCATION", LogLevel.INFO, "App-level location provider disabled (permission: $hasLocationPermission, GPS: $locationServicesEnabled)")
        null
    }

    // Create location state once at app level (demo app pattern)
    val locationState = locationProvider?.let { org.maplibre.compose.location.rememberUserLocationState(it) }

    return remember(hasLocationPermission, locationServicesEnabled, locationMarkerSettings) {
        MapState(locationProvider = locationProvider, locationState = locationState)
    }
}

@Composable
expect fun ScreenDimensionsInitializer(globalState: GlobalState)

@Composable
fun MainApp() {
    val globalState = GlobalState()

    ProvideGlobalState(globalState) {
        // Initialize screen dimensions for wind vector downsampling
        ScreenDimensionsInitializer(globalState)

        // Handle location permission requests reactively
        val permissionManager = remember { createPermissionManager() }
        var hasLocationPermission by remember { mutableStateOf(
            permissionManager.isPermissionGranted(PermissionType.LOCATION_FINE) ||
            permissionManager.isPermissionGranted(PermissionType.LOCATION_COARSE)
        )}

        // Request location permission automatically if not granted
        LaunchedEffect(hasLocationPermission) {
            if (!hasLocationPermission) {
                Logger.log("LOCATION", LogLevel.INFO, "Location permission not granted, requesting permission")
                try {
                    val result = permissionManager.requestPermission(PermissionType.LOCATION_FINE)
                    Logger.log("LOCATION", LogLevel.INFO, "Location permission request result: ${result::class.simpleName}")
                    // Re-check permission state after request completes
                    val newPermissionState = permissionManager.isPermissionGranted(PermissionType.LOCATION_FINE) ||
                                           permissionManager.isPermissionGranted(PermissionType.LOCATION_COARSE)
                    hasLocationPermission = newPermissionState
                } catch (e: Exception) {
                    Logger.log("LOCATION", LogLevel.ERROR, "Failed to request location permission: ${e.message}", e)
                }
            }
        }

        val mapState = rememberMapState(hasLocationPermission)

        // Use dark theme for the entire app
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF1976D2),
                background = Color.Black,
                surface = Color(0xFF121212),
                onBackground = Color.White,
                onSurface = Color.White
            )
        ) {
            // Initialize the application (generic setup)
            LaunchedEffect(Unit) {
                Logger.log("STARTUP", LogLevel.INFO, "Initializing MountainCircles app...")

                try {
                    // Initialize and register modules - each module handles its own functionality
                    globalState.initializeModules()
                    Logger.log("STARTUP", LogLevel.INFO, "App initialization completed successfully")

                } catch (e: Exception) {
                    Logger.log("STARTUP", LogLevel.ERROR, "Failed to initialize app: ${e.message}")
                }
            }

            MapWithOverlays(globalState, mapState.locationProvider, mapState.locationState)
        }
    }
}

