package org.mountaincircles.app.ui

import kotlinx.coroutines.flow.StateFlow
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.location.UserLocationState
import org.mountaincircles.app.state.GlobalState

/**
 * Implementation of MapRenderingState that wraps GlobalState
 * Provides backward compatibility during the decoupling transition
 */
class MapStateImpl(private val globalState: GlobalState) : MapRenderingState {
    override val mapReady: StateFlow<Boolean> = globalState.mapReady
    override val northLocked: StateFlow<Boolean> = globalState.northLocked
    override val clickedPosition: StateFlow<org.mountaincircles.app.ui.map.MapClickEvent?> = globalState.clickedPosition
}

/**
 * Implementation of LocationState that wraps location parameters
 * Provides a clean interface for location-related state
 */
data class LocationStateImpl(
    override val locationProvider: org.maplibre.compose.location.LocationProvider?,
    override val locationState: org.maplibre.compose.location.UserLocationState?
) : LocationState
