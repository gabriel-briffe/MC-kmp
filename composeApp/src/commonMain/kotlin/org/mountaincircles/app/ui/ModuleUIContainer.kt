package org.mountaincircles.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.ui.GenericSheetManager
import org.mountaincircles.app.ui.TopMenuComposable
import org.mountaincircles.app.ui.SubmenuComposable
import org.mountaincircles.app.ui.SidebarComposable

/**
 * UI container that manages all user interface components
 * Phase 2: Extracted from MainMapComposable to separate UI concerns
 * This composable provides UI components that should be positioned within a parent Box layout
 */
@Composable
fun ModuleUIContainer(
    uiComponentsState: UIComponentsState,
    locationProvider: org.maplibre.compose.location.LocationProvider? = null,
    locationState: org.maplibre.compose.location.UserLocationState? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
    // Top Menu - Fixed at top
    TopMenuComposable(
        navigationState = uiComponentsState.navigationState,
        globalState = uiComponentsState.globalState,
        modifier = Modifier.align(Alignment.TopStart)
    )

    // Submenu - Below top menu (when open), no gap
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(top = 56.dp) // Top menu height (56), no gap
    ) {
        SubmenuComposable(
            globalState = uiComponentsState.globalState
        )
    }

    // Sidebar - Sliding from left, below top menu and submenu, no gaps
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(top = if (uiComponentsState.navigationState.submenuVisible.collectAsState().value != null) 108.dp else 56.dp) // Top menu (56) + submenu (52) = 108dp, or top menu (56) only
            .fillMaxSize()
    ) {
        val sidebarVisible by uiComponentsState.navigationState.sidebarVisible.collectAsState()

        // Invisible overlay to handle outside clicks when sidebar is open
        if (sidebarVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null // No visual feedback
                    ) {
                        Logger.log("UI", LogLevel.INFO, "ModuleUIContainer: Outside click detected, closing sidebar")
                        uiComponentsState.navigationState.closeSidebar()
                    }
            )
        }

        // Sidebar component
        SidebarComposable(
            navigationState = uiComponentsState.navigationState,
            globalState = uiComponentsState.globalState,
            locationProvider = locationProvider,
            locationState = locationState,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
    }

    // Generic Sheet Manager - Unified system for all bottom sheets
    GenericSheetManager(
        navigationState = uiComponentsState.navigationState,
        globalState = uiComponentsState.globalState
    )
}
