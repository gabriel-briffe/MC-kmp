package org.mountaincircles.app.ui

import androidx.compose.foundation.clickable
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
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.ModuleUIState
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.state.NavigationState
import org.mountaincircles.app.ui.components.GenericBottomSheet
import org.mountaincircles.app.ui.components.BottomSheetConfigs
import org.mountaincircles.app.ui.theme.AppTheme
import org.mountaincircles.app.ui.AboutComposable
import org.mountaincircles.app.offline.isOfflineRegionDownloadSupported

@Composable
fun MainMenuComposable(
    globalState: GlobalState,
    navigationState: NavigationState,
    modifier: Modifier = Modifier
) {
    val mainMenuVisible by navigationState.mainMenuVisible.collectAsState()

    Logger.log("UI", LogLevel.DEBUG, "MainMenuComposable: Rendering, mainMenuVisible=$mainMenuVisible")


    GenericBottomSheet(
        visible = mainMenuVisible,
        onDismiss = {
            Logger.log("UI", LogLevel.INFO, "MainMenu: Dismiss requested")
            navigationState.closeMainMenu()
        },
        config = BottomSheetConfigs.MainMenu,
        modifier = modifier
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppTheme.Spacing.bottomSheetPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "Main Menu",
                    style = AppTheme.Typography.bottomSheetTitle,
                    modifier = Modifier.padding(bottom = AppTheme.Spacing.bottomSheetTitleBottom)
                )
                
                // Module Actions Section (at the beginning)
                val allModules by globalState.moduleManager.modulesAvailableForUI.collectAsState()
                val modulesWithActions = allModules
                    .filter { module -> module.hasMainMenuButton }
                    .sortedBy { it.mainMenuOrder }

                Logger.log("UI", LogLevel.DEBUG, "MainMenu: Found ${modulesWithActions.size} modules with actions: ${modulesWithActions.map { it.moduleId }}")

                // Create reactive actions for each module
                modulesWithActions.forEach { module ->
                    ModuleActionComposable(module, navigationState)
                }

                // Separator between module actions and core menu items
                Spacer(modifier = Modifier.height(16.dp))

                if (isOfflineRegionDownloadSupported) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                Logger.log("UI", LogLevel.INFO, "MainMenu: Store offline clicked")
                                globalState.startOfflineRegionSelection()
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                painter = AppIcons.Download(),
                                contentDescription = "Store offline",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Store offline",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Download map area for offline use (zoom 0–7)",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                
                // Settings button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable {
                            Logger.log("UI", LogLevel.INFO, "MainMenu: Settings clicked")
                            navigationState.openSettings()
                        },
                    colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            painter = AppIcons.Settings(),
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Column {
                            Text(
                                text = "Settings",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "App configuration and preferences",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // Widget button - placeholder for now
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable {
                            Logger.log("UI", LogLevel.INFO, "MainMenu: Widget clicked")
                            navigationState.openWidget()
                        },
                    colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            painter = AppIcons.Settings(),
                            contentDescription = "Widget",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )

                        Column {
                            Text(
                                text = "Widget",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Configure homescreen widget",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // About button - shows simple about sheet
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable {
                            Logger.log("UI", LogLevel.INFO, "MainMenu: About clicked")
                            navigationState.openAbout()
                        },
                    colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            painter = AppIcons.Info(),
                            contentDescription = "About",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Column {
                            Text(
                                text = "About",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Version info and acknowledgments",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Footer info
                Text(
                    text = "MountainCircles v1.1.47",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
    }

}

@Composable
private fun ModuleActionComposable(module: org.mountaincircles.app.modules.ModuleBase, navigationState: NavigationState) {
    // All modules that inherit from ModuleBase have uiState
    val uiState: ModuleUIState by module.uiState.collectAsState()

    Logger.log("UI", LogLevel.DEBUG, "MainMenu: ${module.moduleId} uiState - isReady=${uiState.isReady}, isVisible=${uiState.isVisible}")

    val actions = module.getModuleActions()

    Logger.log("UI", LogLevel.DEBUG, "MainMenu: ${module.moduleId} created ${actions.size} actions")

    actions.forEach { action ->
        Logger.log("UI", LogLevel.DEBUG, "MainMenu: ${module.moduleId} action '${action.title}' - enabled=${action.isEnabled}")
        ModuleActionCard(action)
    }
}

@Composable
private fun ModuleActionCard(action: org.mountaincircles.app.modules.ModuleMenuAction) {
    Logger.log("UI", LogLevel.DEBUG, "MainMenu: Rendering action card '${action.title}' - enabled=${action.isEnabled}")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(enabled = action.isEnabled) {
                Logger.log("UI", LogLevel.INFO, "MainMenu: Module action clicked: ${action.id}")
                action.action()
            },
        colors = CardDefaults.cardColors(
            containerColor = if (action.isEnabled)
                Color.Gray.copy(alpha = 0.3f)
            else
                Color.Gray.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            action.getIcon().let { icon ->
                Icon(
                    painter = icon,
                    contentDescription = action.title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = action.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (action.isEnabled) Color.White else Color.Gray
                )
                Text(
                    text = action.description,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

