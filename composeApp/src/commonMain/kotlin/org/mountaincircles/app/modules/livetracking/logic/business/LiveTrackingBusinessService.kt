package org.mountaincircles.app.modules.livetracking.logic.business

import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.livetracking.LiveTrackingModule

/**
 * Live Tracking Business Service
 * Handles core business logic for live tracking operations
 */
class LiveTrackingBusinessService(private val module: LiveTrackingModule) {

    /**
     * Hide aircraft popup
     */
    fun hideAircraftPopup() {
        Logger.log("LIVETRACKING_POPUP", LogLevel.INFO, "🔴 STARTING: Hiding aircraft popup")
        module.updateState { currentState ->
            currentState.copy(
                showPopup = false,
                popupDeviceId = null
            )
        }
        Logger.log("LIVETRACKING_POPUP", LogLevel.INFO, "✅ COMPLETED: Aircraft popup hidden")
    }

}