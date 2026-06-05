package org.mountaincircles.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.state.GlobalState
import org.mountaincircles.app.state.NavigationState
import org.mountaincircles.app.ui.settings.ModuleSettingsRegistry
import org.mountaincircles.app.ui.settings.GenericSettingsSection
import org.mountaincircles.app.ui.theme.AppTheme
import org.mountaincircles.app.ui.components.GenericBottomSheet
import org.mountaincircles.app.ui.components.BottomSheetConfigs
import org.mountaincircles.app.ui.components.CollapsibleSettingsSection
import org.mountaincircles.app.ui.components.ResetButtonStyle
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.modules.ModuleBase
import org.mountaincircles.app.settings.ClassMetadata
import org.mountaincircles.app.settings.FieldMetadata
import org.mountaincircles.app.settings.FieldType
import org.mountaincircles.app.settings.SettingsMetadataExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Singleton location marker settings manager
val locationMarkerSettingsManager = org.mountaincircles.app.persistence.DataStoreSettingsManager(
    moduleId = "app_location_marker",
    serializer = LocationMarkerSettings.serializer(),
    defaultSettings = LocationMarkerSettings()
)

// App settings metadata provider
private object AppSettingsProvider {
    fun getAppSettingsMetadata(): Pair<ClassMetadata, Map<String, Any?>> {
        val metadata = ClassMetadata(
            name = "App Settings",
            description = "Global application preferences",
            version = 1,
            icon = "⚙️",
            category = "General",
            fields = listOf(
                FieldMetadata(
                    name = "dotRadius",
                    type = Float::class,
                    fieldType = FieldType.SLIDER,
                    label = "Location Dot Size",
                    description = "",
                    unit = "dp",
                    min = 4.0,
                    max = 24.0,
                    step = 1.0,
                    decimals = 0,
                    required = true,
                    enabled = true,
                    group = "Location Marker",
                    order = 1,
                    icon = "",
                    isHidden = false,
                    requiresRestart = false,
                    isAdvanced = false
                ),
                FieldMetadata(
                    name = "bearingSize",
                    type = Float::class,
                    fieldType = FieldType.SLIDER,
                    label = "Bearing Triangle Size",
                    description = "",
                    unit = "dp",
                    min = 8.0,
                    max = 32.0,
                    step = 1.0,
                    decimals = 0,
                    required = true,
                    enabled = true,
                    group = "Location Marker",
                    order = 2,
                    icon = "",
                    isHidden = false,
                    requiresRestart = false,
                    isAdvanced = false
                ),
                FieldMetadata(
                    name = "updateIntervalMs",
                    type = Long::class,
                    fieldType = FieldType.SLIDER,
                    label = "Update Interval",
                    description = "",
                    unit = "ms",
                    min = 200.0,
                    max = 4000.0,
                    step = 200.0,
                    decimals = 0,
                    required = true,
                    enabled = true,
                    group = "Location Marker",
                    order = 3,
                    icon = "",
                    isHidden = false,
                    requiresRestart = false,
                    isAdvanced = false
                ),
                FieldMetadata(
                    name = "circleWidthDp",
                    type = Float::class,
                    fieldType = FieldType.SLIDER,
                    label = "Distance Ring Width",
                    description = "",
                    unit = "dp",
                    min = 1.0,
                    max = 10.0,
                    step = 0.5,
                    decimals = 1,
                    required = true,
                    enabled = true,
                    group = "Location Marker",
                    order = 4,
                    icon = "",
                    isHidden = false,
                    requiresRestart = false,
                    isAdvanced = false
                ),
                FieldMetadata(
                    name = "vectorWidthDp",
                    type = Float::class,
                    fieldType = FieldType.SLIDER,
                    label = "Vector Width",
                    description = "",
                    unit = "dp",
                    min = 1.0,
                    max = 10.0,
                    step = 0.5,
                    decimals = 1,
                    required = true,
                    enabled = true,
                    group = "Location Marker",
                    order = 5,
                    icon = "",
                    isHidden = false,
                    requiresRestart = false,
                    isAdvanced = false
                )
            )
        )

        // Return default values - actual values will be provided reactively
        val values = mapOf(
            "dotRadius" to 8f,  // Default value
            "bearingSize" to 16f, // Default value
            "circleWidthDp" to 4f, // Default circle width
            "vectorWidthDp" to 3f  // Default vector width
        )

        return metadata to values
    }
}

// Module setting change handler
private fun handleModuleSettingChange(scope: CoroutineScope, module: ModuleBase, fieldName: String, value: Any) {
    scope.launch {
        handleModuleSettingChangeSuspend(module, fieldName, value)
    }
}

private suspend fun handleModuleSettingChangeSuspend(module: ModuleBase, fieldName: String, value: Any) {
    ModuleSettingsRegistry.handleModuleSettingChange(module, fieldName, value)
}

// App setting change handler
private fun handleAppSettingChange(scope: CoroutineScope, globalState: GlobalState, fieldName: String, value: Any) {
    scope.launch {
        handleAppSettingChangeSuspend(globalState, fieldName, value)
    }
}

private suspend fun handleAppSettingChangeSuspend(globalState: GlobalState, fieldName: String, value: Any) {
    try {
        when (fieldName) {
            // Location marker settings
            "dotRadius", "bearingSize", "updateIntervalMs" -> {
                val currentSettings = locationMarkerSettingsManager.load()
                val updatedSettings = when (fieldName) {
                    "dotRadius" -> currentSettings.copy(dotRadius = (value as? Number)?.toFloat() ?: currentSettings.dotRadius)
                    "bearingSize" -> currentSettings.copy(bearingSize = (value as? Number)?.toFloat() ?: currentSettings.bearingSize)
                    "updateIntervalMs" -> currentSettings.copy(updateIntervalMs = (value as? Number)?.toLong() ?: currentSettings.updateIntervalMs)
                    else -> currentSettings
                }
                locationMarkerSettingsManager.save(updatedSettings)
            }

            // Geolocation settings
            "circleWidthDp" -> {
                globalState.updateCircleWidth((value as? Number)?.toFloat() ?: 4f)
            }
            "vectorWidthDp" -> {
                globalState.updateVectorWidth((value as? Number)?.toFloat() ?: 3f)
            }
        }
    } catch (e: Exception) {
        Logger.log("APP_SETTINGS", LogLevel.ERROR, "Failed to save app setting $fieldName: ${e.message}", e)
    }
}

private fun resetAllSettingsToDefaults(scope: CoroutineScope, globalState: GlobalState) {
    scope.launch {
        try {
            // Reset location marker settings
            val defaultSettings = LocationMarkerSettings() // All default values
            locationMarkerSettingsManager.save(defaultSettings)

            // Reset geolocation settings to defaults
            globalState.updateCircleRadius(0f)  // Default 0km
            globalState.updateVectorLength(0f)  // Default 0km
            globalState.updateCircleWidth(4f)   // Default 4dp
            globalState.updateVectorWidth(3f)   // Default 3dp

            Logger.log("APP_SETTINGS", LogLevel.INFO, "All settings reset to defaults")
        } catch (e: Exception) {
            Logger.log("APP_SETTINGS", LogLevel.ERROR, "Failed to reset settings to defaults: ${e.message}", e)
        }
    }
}

private fun resetModuleSettingsToDefaults(scope: CoroutineScope, module: ModuleBase) {
    scope.launch {
        try {
            ModuleSettingsRegistry.resetModuleSettingsToDefaults(module)
            Logger.log("MODULE_SETTINGS", LogLevel.INFO, "${module.moduleId} settings reset to defaults")
        } catch (e: Exception) {
            Logger.log("MODULE_SETTINGS", LogLevel.ERROR, "Failed to reset ${module.moduleId} settings: ${e.message}", e)
        }
    }
}

@Serializable
data class LocationMarkerSettings(
    val dotRadius: Float = 8f,         // Default 8.dp (0.5x to 3x scale)
    val bearingSize: Float = 16f,      // Default 16.dp
    val updateIntervalMs: Long = 1000L // Default 1000ms (1 second)
)

// App-level settings manager for location marker

@Composable
fun SettingsComposable(
    navigationState: NavigationState,
    globalState: GlobalState,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val settingsVisible by navigationState.settingsVisible.collectAsState()
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Function to scroll to a specific section
    val scrollToSection = { sectionIndex: Int ->
        coroutineScope.launch {
            lazyListState.animateScrollToItem(sectionIndex)
        }
    }

    GenericBottomSheet(
        visible = settingsVisible,
        onDismiss = onDismiss ?: {
            Logger.log("UI", LogLevel.INFO, "Settings: Dismiss requested")
            navigationState.closeSettings()
        },
        config = BottomSheetConfigs.Settings,
        modifier = modifier,
        contentHasOwnScrolling = true // Settings uses LazyColumn for its own scrolling
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(AppTheme.Spacing.bottomSheetPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item {
                Text(
                    text = "Settings",
                    style = AppTheme.Typography.bottomSheetTitle,
                    modifier = Modifier.padding(bottom = AppTheme.Spacing.bottomSheetTitleBottom)
                )
            }

            // Settings guidance text
            item {
                Text(
                    text = "Settings help you adjust what you see depending on the resolution of your device's screen\n\n!! Make sure you see the features for which you are changing the settings !!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Yellow,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Module-specific settings sections - only show modules with settings UI
            val availableModules = globalState.moduleManager.modulesAvailableForUI.value
            Logger.log("SETTINGS_UI", LogLevel.DEBUG, "Available modules: ${availableModules.map { "${it.moduleId}(init=${it.moduleState.value.isInitialized})" }}")

            // Filter to only modules that have settings panel capability
            val modulesWithSettings = availableModules.filter { module ->
                val hasPanel = module.hasSettingsPanel
                Logger.log("SETTINGS_UI", LogLevel.DEBUG, "Module ${module.moduleId}: hasSettingsPanel=$hasPanel")
                hasPanel
            }
            Logger.log("SETTINGS_UI", LogLevel.INFO, "Modules with settings: ${modulesWithSettings.map { it.moduleId }}")

            if (modulesWithSettings.isNotEmpty()) {
                itemsIndexed(modulesWithSettings) { index: Int, module: org.mountaincircles.app.modules.ModuleBase ->
                    // Wrap each module's settings in a collapsible container
                    CollapsibleModuleSettings(
                        module = module,
                        scope = coroutineScope,
                        onExpanded = { scrollToSection(index + 2) } // +2 for header and guidance text
                    )
                }
            } else {
                // Show placeholder if no modules have settings
                item {
                    Text(
                        text = "No module settings available",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
            
            // App-wide settings section
            item {
                AppSettingsSection(globalState, coroutineScope) {
                    // App settings is the last item: header(0) + guidance(1) + modules + app settings
                    scrollToSection(modulesWithSettings.size + 2)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CollapsibleModuleSettings(module: ModuleBase, scope: CoroutineScope, onExpanded: (() -> Unit)? = null) {
    Logger.log("SETTINGS_UI", LogLevel.DEBUG, "Rendering settings for ${module.moduleId}")

    // Get the module's settings metadata and values using provider pattern
    val (metadata, values) = ModuleSettingsRegistry.getModuleSettingsMetadata(module)

    Logger.log("SETTINGS_UI", LogLevel.DEBUG, "${module.moduleId} metadata: ${metadata?.fields?.size ?: 0} fields, values: ${values.size}")

    // Use the module's declared icon, fallback to generic settings icon
    val iconPainter = module.getIcon() ?: AppIcons.Settings()

    CollapsibleSettingsSection(
        icon = iconPainter,
        title = module.displayName,
        metadata = metadata,
        values = values,
        onValueChange = { fieldName, value ->
            handleModuleSettingChange(scope, module, fieldName, value)
        },
        onResetClick = { resetModuleSettingsToDefaults(scope, module) },
        resetButtonText = "Reset ${module.moduleId.replaceFirstChar { it.uppercase() }} Settings",
        resetButtonStyle = ResetButtonStyle.Module,
        onExpanded = onExpanded
    )

    // Add spacing between module sections
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun AppSettingsSection(globalState: GlobalState, scope: CoroutineScope, onExpanded: (() -> Unit)? = null) {
    // Get app settings metadata and reactive values from DataStore
    val metadata = remember { AppSettingsProvider.getAppSettingsMetadata().first }
    val currentSettings by locationMarkerSettingsManager.settings.collectAsState(initial = LocationMarkerSettings())

    // Get geolocation settings from GlobalState
    val circleWidthDp by globalState.circleWidthDp.collectAsState()
    val vectorWidthDp by globalState.vectorWidthDp.collectAsState()

    val values = mapOf(
        "dotRadius" to currentSettings.dotRadius,
        "bearingSize" to currentSettings.bearingSize,
        "updateIntervalMs" to currentSettings.updateIntervalMs,
        "circleWidthDp" to circleWidthDp,
        "vectorWidthDp" to vectorWidthDp
    )

    CollapsibleSettingsSection(
        icon = AppIcons.Settings(),
        title = "App Settings",
        metadata = metadata,
        values = values,
        onValueChange = { fieldName, value ->
            handleAppSettingChange(scope, globalState, fieldName, value)
        },
        onResetClick = { resetAllSettingsToDefaults(scope, globalState) },
        resetButtonStyle = ResetButtonStyle.App,
        onExpanded = onExpanded
    )
}








