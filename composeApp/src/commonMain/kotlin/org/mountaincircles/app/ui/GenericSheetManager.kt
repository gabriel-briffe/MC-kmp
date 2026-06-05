package org.mountaincircles.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.NavigationState
import org.mountaincircles.app.state.SheetType
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.ui.components.GenericBottomSheet
import org.mountaincircles.app.ui.components.BottomSheetConfigs
import org.mountaincircles.app.ui.modules.ModuleSheetRegistry
import org.mountaincircles.app.ui.modules.ScrollableComposableProvider
import org.mountaincircles.app.ui.theme.AppTheme

/**
 * Generic Sheet Manager - Unified system for all bottom sheets
 * Replaces individual sheet composables with a single managed system
 */
@Composable
fun GenericSheetManager(
    navigationState: NavigationState,
    globalState: GlobalState,
    modifier: Modifier = Modifier
) {
    val currentSheet by navigationState.currentSheet.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll trigger state - created dynamically for scrollable modules
    val scrollTrigger = remember { mutableStateOf(false) }

    Logger.log("UI", LogLevel.DEBUG, "GenericSheetManager: currentSheet = $currentSheet")

    when (currentSheet) {
        is SheetType.MainMenu -> {
            MainMenuSheet(
                navigationState = navigationState,
                globalState = globalState,
                modifier = modifier
            )
        }
        is SheetType.Settings -> {
            SettingsSheet(
                navigationState = navigationState,
                globalState = globalState,
                modifier = modifier
            )
        }
        is SheetType.About -> {
            AboutSheet(
                navigationState = navigationState,
                modifier = modifier
            )
        }
        is SheetType.Widget -> {
            WidgetSheet(
                navigationState = navigationState,
                modifier = modifier
            )
        }
        is SheetType.ImportSheet -> {
            val moduleId = (currentSheet as SheetType.ImportSheet).moduleId
            val module = globalState.moduleManager.getModule(moduleId)
            val provider = module?.let { ModuleSheetRegistry.getProvider(it) }

            // Create scroll trigger callback if the provider supports scrolling
            val onScrollToTop: (() -> Unit)? = if (provider is ScrollableComposableProvider && provider.supportsScrollToTop) {
                {
                    scrollTrigger.value = true
                    // Reset after a short delay to allow re-triggering
                    coroutineScope.launch {
                        delay(100)
                        scrollTrigger.value = false
                    }
                }
            } else null

            ImportSheet(
                moduleId = moduleId,
                navigationState = navigationState,
                globalState = globalState,
                modifier = modifier,
                scrollToTopTrigger = scrollTrigger.value,
                onScrollToTop = onScrollToTop
            )
        }
        null -> {
            // No sheet to show
        }
    }
}

/**
 * Main Menu Sheet - Extracted from MainMenuComposable
 */
@Composable
private fun MainMenuSheet(
    navigationState: NavigationState,
    globalState: GlobalState,
    modifier: Modifier = Modifier
) {
    MainMenuComposable(
        globalState = globalState,
        navigationState = navigationState,
        modifier = modifier
    )
}

/**
 * Settings Sheet - Extracted from SettingsComposable
 */
@Composable
private fun SettingsSheet(
    navigationState: NavigationState,
    globalState: GlobalState,
    modifier: Modifier = Modifier
) {
    SettingsComposable(
        navigationState = navigationState,
        globalState = globalState,
        onDismiss = {
            Logger.log("UI", LogLevel.INFO, "SettingsSheet: Dismiss requested")
            navigationState.closeSettings()
        },
        modifier = modifier
    )
}

/**
 * About Sheet - Extracted from AboutComposable
 */
@Composable
private fun AboutSheet(
    navigationState: NavigationState,
    modifier: Modifier = Modifier
) {
    AboutComposable(
        onDismiss = {
            Logger.log("UI", LogLevel.INFO, "AboutSheet: Dismiss requested, reopening main menu")
            navigationState.closeAbout()
            navigationState.toggleMainMenu()
        }
    )
}

/**
 * Widget Sheet - Extracted from WidgetComposable
 */
@Composable
private fun WidgetSheet(
    navigationState: NavigationState,
    modifier: Modifier = Modifier
) {
    WidgetComposable(
        onDismiss = {
            Logger.log("UI", LogLevel.INFO, "WidgetSheet: Dismiss requested, reopening main menu")
            navigationState.closeWidget()
            navigationState.toggleMainMenu()
        }
    )
}


/**
 * Import Sheet - Shows import UI for a specific module
 */
@Composable
private fun ImportSheet(
    moduleId: String,
    navigationState: NavigationState,
    globalState: GlobalState,
    modifier: Modifier = Modifier,
    scrollToTopTrigger: Boolean = false,
    onScrollToTop: (() -> Unit)? = null
) {
    val module = globalState.moduleManager.getModule(moduleId)
    if (module != null) {
        // Check if module has a specific import sheet implementation
        val hasCustomImportSheet = ModuleSheetRegistry.hasSheetUI(module)
        val provider = if (hasCustomImportSheet) ModuleSheetRegistry.getProvider(module) else null
        val fullWidthContent: List<@Composable () -> Unit> = provider?.provideFullWidthContent(module) ?: emptyList()

        // Wrap import sheet content in a bottom sheet with dismiss handling
        GenericBottomSheet(
            visible = true,
            onDismiss = {
                Logger.log("UI", LogLevel.INFO, "ImportSheet: Dismiss requested for $moduleId")
                navigationState.closeImportSheet()
            },
            config = BottomSheetConfigs.ImportSheet,
            modifier = modifier,
            scrollToTopTrigger = scrollToTopTrigger,
            fullWidthContent = fullWidthContent,
            contentHasOwnScrolling = provider is ScrollableComposableProvider && provider.supportsLazyScrolling
        ) {
            if (hasCustomImportSheet) {
                // Use module-specific import sheet (WaveImportComposable, etc.)
                Logger.log("UI", LogLevel.DEBUG, "Using custom import sheet for $moduleId")

                // Title (centered)
                Text(
                    text = ModuleSheetRegistry.getSheetTitle(module),
                    style = AppTheme.Typography.bottomSheetTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppTheme.Spacing.bottomSheetTitleBottom),
                    textAlign = TextAlign.Center
                )

                // Module-specific content
                ModuleSheetRegistry.RenderModuleSheet(module, onScrollToTop)
            } else {
                // Fall back to generic import sheet
                Logger.log("UI", LogLevel.DEBUG, "Using generic import sheet for $moduleId")
                GenericImportSheet(module = module)
            }
        }
    } else {
        Logger.log("UI", LogLevel.ERROR, "ImportSheet: Module $moduleId not found")
        // Dismiss if module not found
        navigationState.closeImportSheet()
    }
}
