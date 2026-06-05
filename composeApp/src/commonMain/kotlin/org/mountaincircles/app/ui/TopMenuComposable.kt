package org.mountaincircles.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.state.NavigationState

@Composable
fun TopMenuComposable(
    navigationState: NavigationState,
    globalState: org.mountaincircles.app.state.GlobalState,
    modifier: Modifier = Modifier
) {
    val sidebarVisible by navigationState.sidebarVisible.collectAsState()
    val northLocked by globalState.northLocked.collectAsState()
    val availableModules by globalState.moduleManager.modulesAvailableForUI.collectAsState()
    val scope = rememberCoroutineScope()

    // Check if CirclesModule is in available modules (for conditional logic only)
    val circlesModule = availableModules.find { it is org.mountaincircles.app.modules.circles.CirclesModule }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Count total buttons for weight calculation (reserve space for modules with top menu buttons)
            val allModules = availableModules.filter { it is ModuleBase }
            val modulesWithButtons = allModules.filter { it.hasTopMenuButton }
            val totalButtons = 2 + modulesWithButtons.size + 1 // Reserve space for modules with top menu buttons

            // Debug logging - show layout and capabilities
            Logger.log("UI", LogLevel.DEBUG, "TOPMENU: Reserved space for ${modulesWithButtons.size} modules with top menu buttons, layout has $totalButtons total button slots")

            // Log button capability for each module during initialization
            allModules.forEach { module ->
                val moduleBase = module
                val hasButton = module.hasTopMenuButton
                Logger.log("UI", LogLevel.DEBUG, "TOPMENU: Module ${moduleBase.moduleId} - has top menu button: $hasButton")
            }

            // Sidebar toggle (layers icon) - first button
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        Logger.log("UI", LogLevel.INFO, "TopMenu: Sidebar toggle clicked")
                        navigationState.toggleSidebar()
                    }
                ) {
                    Icon(
                        painter = AppIcons.Layers(),
                        contentDescription = "Toggle Sidebar",
                        tint = if (sidebarVisible) Color.Cyan else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // North-lock button - second button
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            Logger.log("UI", LogLevel.INFO, "TopMenu: North-lock toggle clicked, current state: $northLocked")
                            globalState.toggleNorthLock()
                        }
                    }
                ) {
                    Icon(
                        painter = AppIcons.Navigation(),
                        contentDescription = if (northLocked) "Release North Lock" else "Lock North",
                        tint = if (northLocked) Color.Red else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Module-specific top menu buttons - each gets equal space (reserve space for modules with buttons)
            modulesWithButtons.forEach { module ->
                val currentSubmenuId by navigationState.submenuVisible.collectAsState()
                val isSubmenuOpen = currentSubmenuId == module.moduleId

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (isSubmenuOpen) {
                                Modifier.background(
                                    color = Color.Black.copy(alpha = 0.20f)
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (module.hasTopMenuButton) {
                        ReactiveModuleButton(
                            moduleBase = module,
                            onClick = {
                                Logger.log("UI", LogLevel.INFO, "Module ${module.moduleId} clicked via reactive button")
                                navigationState.toggleSubmenu(module.moduleId)
                            },
                            globalState = globalState,
                            navigationState = navigationState,
                            isHighlighted = isSubmenuOpen
                        )
                    }
                }
            }

            // Settings menu - last button
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp) // IconButton default size
                        .combinedClickable(
                            onClick = {
                                Logger.log("UI", LogLevel.INFO, "TopMenu: Settings menu clicked")
                                navigationState.toggleMainMenu()
                            },
                            onLongClick = {
                                Logger.log("UI", LogLevel.INFO, "TopMenu: Settings menu long-clicked - opening settings")
                                navigationState.openSettings()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = AppIcons.Settings(),
                        contentDescription = "Open Main Menu / Long press for Settings",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
