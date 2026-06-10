package org.mountaincircles.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import org.maplibre.spatialk.geojson.Position
import org.mountaincircles.app.ui.overlay.MapOverlayRegistry
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds

import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.map.GestureOptions
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.sources.rememberRasterSource
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.util.ClickResult
// STEP 3.3: LocationPuck imports
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import org.mountaincircles.app.ui.CoreMarkerLayer
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import org.maplibre.compose.location.UserLocationState
import org.maplibre.compose.camera.CameraState
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.ui.map.LayerManagerComposables
import org.mountaincircles.app.ui.MainMenuComposable
import org.mountaincircles.app.ui.SettingsComposable
import org.mountaincircles.app.ui.LocationMarkerSettings
import org.mountaincircles.app.ui.TopMenuComposable
import org.mountaincircles.app.ui.SubmenuComposable
import org.mountaincircles.app.ui.overlay.MapWithOverlays
import org.mountaincircles.app.ui.MapContainer
import org.mountaincircles.app.ui.MapOverlaysContainer
import org.mountaincircles.app.offline.OfflineRegionUi

// STEP 3.2: LocationProvider infrastructure prepared


// Removed accompanist permission interface - using direct Android permission checking instead

@Composable
fun MainMapComposable(
    globalState: GlobalState,
    locationProvider: org.maplibre.compose.location.LocationProvider?,
    locationState: org.maplibre.compose.location.UserLocationState?
) {

    // Location provider is created at app level and passed down
    

    
    // Log startup
    LaunchedEffect(Unit) {
        Logger.log("STARTUP", LogLevel.INFO, "MainMapComposable initialized with direct MBTiles access")
        Logger.log("MAP", LogLevel.INFO, "MapLibre + direct MBTiles integration ready")
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars) // Handle both status and navigation bars
    ) {
        // MapLibre Map with OSM tiles - MapLibre handles tile fetching natively
        Logger.log("MAP", LogLevel.INFO, "Using OpenStreetMap tiles - MapLibre handles fetching natively")

        // Phase 1: Extracted map rendering into MapContainer
        MapContainer(
            mapState = MapStateImpl(globalState),
            locationState = LocationStateImpl(locationProvider, locationState),
            moduleLayersState = ModuleLayersStateImpl(globalState),
            globalState = globalState // TODO: Phase 4 - remove via callbacks
        )

        // Phase 3: Extracted overlay rendering into MapOverlaysContainer
        MapOverlaysContainer(
            uiComponentsState = UIComponentsStateImpl(globalState),
            globalState = globalState
        )

        OfflineRegionUi(globalState)

        // Phase 2: Extracted UI components into ModuleUIContainer
        ModuleUIContainer(
            uiComponentsState = UIComponentsStateImpl(globalState),
            locationProvider = locationProvider,
            locationState = locationState
        )
    }
}









