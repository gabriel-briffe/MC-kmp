package org.mountaincircles.app.modules.livetracking.logic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.painter.Painter
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.livetracking.LiveTrackingModule
import org.mountaincircles.app.modules.livetracking.logic.data.LiveTrackingVisibilityMode
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.modules.ModuleMenuAction
import org.mountaincircles.app.state.getGlobalState
import kotlinx.coroutines.launch
import org.mountaincircles.app.utils.ScopeManager


/**
 * Get the button action for the LiveTracking module
 */
fun LiveTrackingModule.getLiveTrackingButtonAction(): () -> Unit {
    return {
        Logger.log("LIVETRACKING", LogLevel.INFO, "LiveTracking toggle button clicked via generic system")
        val currentMode = this@getLiveTrackingButtonAction.liveTrackingState.value.visibilityMode
        Logger.log("LIVETRACKING", LogLevel.INFO, "Current visibility mode: $currentMode")

        // Cycle through the 3 modes: ALL_VISIBLE -> FRIENDS_ONLY -> ALL_HIDDEN -> ALL_VISIBLE
        val newMode = when (currentMode) {
            LiveTrackingVisibilityMode.ALL_VISIBLE -> LiveTrackingVisibilityMode.FRIENDS_ONLY
            LiveTrackingVisibilityMode.FRIENDS_ONLY -> LiveTrackingVisibilityMode.ALL_HIDDEN
            LiveTrackingVisibilityMode.ALL_HIDDEN -> LiveTrackingVisibilityMode.ALL_VISIBLE
        }

        Logger.log("LIVETRACKING", LogLevel.DEBUG, "Cycling visibility mode from $currentMode to $newMode")

        this@getLiveTrackingButtonAction.updateState { currentState ->
            currentState.copy(visibilityMode = newMode)
        }

        // Persist the new visibility mode asynchronously
        ScopeManager.ioScope.launch {
            this@getLiveTrackingButtonAction.settingPersistence.saveString("liveTrackingVisibilityMode", newMode.name)
        }

        Logger.log("LIVETRACKING", LogLevel.INFO, "Final visibility mode: ${this@getLiveTrackingButtonAction.liveTrackingState.value.visibilityMode}")
    }
}

/**
 * Get the button icon for the LiveTracking module
 */
@Composable
fun LiveTrackingModule.getLiveTrackingButtonIcon(): Painter {
    val liveTrackingState = this.liveTrackingState.collectAsState().value
    val visibilityMode = liveTrackingState.visibilityMode

    Logger.log("LIVETRACKING", LogLevel.DEBUG, "getLiveTrackingButtonIcon: visibilityMode=$visibilityMode")

    return when (visibilityMode) {
        LiveTrackingVisibilityMode.ALL_VISIBLE -> {
            Logger.log("LIVETRACKING", LogLevel.DEBUG, "Returning Glider icon (all visible)")
            AppIcons.GliderIcon()      // All aircraft visible - glider icon
        }
        LiveTrackingVisibilityMode.FRIENDS_ONLY -> {
            Logger.log("LIVETRACKING", LogLevel.DEBUG, "Returning GliderWithHeart icon (friends only)")
            AppIcons.GliderWithHeartIcon()  // Friends only visible - glider with heart icon
        }
        LiveTrackingVisibilityMode.ALL_HIDDEN -> {
            Logger.log("LIVETRACKING", LogLevel.DEBUG, "Returning GliderOff icon (all hidden)")
            AppIcons.GliderOffIcon()    // All aircraft hidden - glider off icon
        }
    }
}


/**
 * Get the LiveTracking module actions for the main menu
 */
fun getLiveTrackingModuleActions(moduleId: String, isInitialized: Boolean): List<ModuleMenuAction> {
    return listOf(
        ModuleMenuAction(
            id = "livetracking_friends",
            title = "Manage OGN Friends",
            description = "Add, Remove, Rename",
            getIcon = { AppIcons.GliderIcon() },
            action = {
                Logger.log("LIVETRACKING", LogLevel.INFO, "LiveTracking friends action triggered - opening import sheet")
                // Open the import sheet like other modules do
                val globalState = getGlobalState()
                globalState.navigationState.openImportSheet(moduleId)
            },
            isEnabled = isInitialized
        )
    )
}