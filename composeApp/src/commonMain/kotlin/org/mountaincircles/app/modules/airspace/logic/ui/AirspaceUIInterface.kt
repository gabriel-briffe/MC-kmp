package org.mountaincircles.app.modules.airspace.logic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.airspace.AirspaceModule
import org.mountaincircles.app.modules.airspace.logic.data.AirspaceLayerDisplayData
import org.mountaincircles.app.modules.ModuleMenuAction
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.ui.modules.ComposableProvider

/**
 * Get the icon for the airspace module
 */
@Composable
fun getAirspaceIcon(): Painter {
    return AppIcons.Airspace()
}

/**
 * Get the button icon for the airspace module
 */
@Composable
fun AirspaceModule.getAirspaceButtonIcon(): Painter {
    // ✅ Use unified state directly
    val airspaceState = this.airspaceState.collectAsState().value
    val isVisible = airspaceState.isVisible
    Logger.log("AIRSPACE", LogLevel.DEBUG, "getButtonIcon: isVisible=$isVisible")

    return when {
        isVisible -> {
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Returning Airspace icon")
            AppIcons.Airspace()   // Layer is visible - will be white
        }
        else -> {
            Logger.log("AIRSPACE", LogLevel.DEBUG, "Returning AirspaceOff icon")
            AppIcons.AirspaceOff()  // Layer is hidden - will be grey
        }
    }
}

/**
 * Get the button action for the airspace module
 */
fun AirspaceModule.getAirspaceButtonAction(): () -> Unit {
    return {
        // Provide the toggle action that the generic button can call
        CoroutineScope(Dispatchers.Main).launch {
            Logger.log("AIRSPACE", LogLevel.INFO, "Airspace toggle button clicked via generic system")
            Logger.log("AIRSPACE", LogLevel.INFO, "Current airspace visibility state: moduleState.isVisible=${this@getAirspaceButtonAction.airspaceState.value.isVisible}")

            val layerManager = this@getAirspaceButtonAction.getLayerManager()

            if (layerManager != null) {
                // Use the proper toggle method following Circles/Wave pattern
                this@getAirspaceButtonAction.toggleVisibility()
                Logger.log("AIRSPACE", LogLevel.INFO, "Airspace layer toggled via generic button")
            } else {
                // Show the layer - create layer manager if it doesn't exist
                Logger.log("AIRSPACE", LogLevel.INFO, "Will show airspace layer (creating new layer manager)")
                Logger.log("AIRSPACE", LogLevel.DEBUG, "Creating new layer manager")
                // Create layer manager outside the lambda to avoid context issues
                val newLayerManager = org.mountaincircles.app.modules.airspace.layer.ui.AirspaceLayerManager(this@getAirspaceButtonAction)
                this@getAirspaceButtonAction.setLayerManager(newLayerManager)

                // Use a coroutine scope to initialize the layer
                CoroutineScope(Dispatchers.Main).launch {
                    Logger.log("AIRSPACE", LogLevel.DEBUG, "About to initialize layer manager")
                    newLayerManager.initializeLayers()
                // After initialization, toggle to show the layer
                this@getAirspaceButtonAction.toggleVisibility()
                Logger.log("AIRSPACE", LogLevel.INFO, "Airspace layer shown via generic toggle (created new manager)")
                }
            }
        }
    }
}

/**
 * Check if layers are added for the airspace module
 */
fun AirspaceModule.areAirspaceLayersAdded(): Boolean {
    return this.airspaceState.value.isVisible
}

/**
 * Get the module actions for the airspace module
 */
fun getAirspaceModuleActions(moduleId: String, isInitialized: Boolean): List<ModuleMenuAction> {
    return emptyList() // Airspace doesn't have import actions
}
