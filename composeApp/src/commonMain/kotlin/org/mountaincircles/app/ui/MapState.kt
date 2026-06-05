package org.mountaincircles.app.ui

import kotlinx.coroutines.flow.StateFlow
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.location.UserLocationState
import org.mountaincircles.app.ui.map.MapClickEvent

/**
 * Interface for map-specific state that MapContainer needs
 * This isolates map rendering concerns from global application state
 */
interface MapRenderingState {
    val mapReady: StateFlow<Boolean>
    val northLocked: StateFlow<Boolean>
    val clickedPosition: StateFlow<org.mountaincircles.app.ui.map.MapClickEvent?>
}

/**
 * Interface for location-specific state that MapContainer needs
 * This isolates location concerns from global application state
 */
interface LocationState {
    val locationProvider: LocationProvider?
    val locationState: UserLocationState?
}
