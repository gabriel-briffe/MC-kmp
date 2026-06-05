package org.mountaincircles.app.modules.circles.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.ui.components.UnifiedProgress
import org.mountaincircles.app.ui.components.UnifiedProgressIndicator
import org.mountaincircles.app.ui.components.StandardButton
import org.mountaincircles.app.ui.components.ClearButton
import org.mountaincircles.app.modules.circles.import.data.CirclePackDefinitions

/**
 * New Circles Import Composable using ViewModel pattern
 * Replaces CirclesImportSectionImpl with clean separation of concerns
 */
@Composable
fun CirclesImportComposable(
    viewModel: CirclesImportViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val progress by viewModel.progressFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // CLEANUP: Reset download state when import sheet is dismissed
    DisposableEffect(Unit) {
        onDispose {
            Logger.log("CIRCLES_UI", LogLevel.INFO, "🗑️ CirclesImportComposable disposed - resetting download state")
            coroutineScope.launch {
                try {
                    viewModel.resetDownloadState()
                } catch (e: Exception) {
                    Logger.log("CIRCLES_UI", LogLevel.ERROR, "Failed to reset download state on dispose: ${e.message}")
                }
            }
        }
    }

    // Base layout
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(CirclesConstants.MAIN_LAYOUT_PADDING),
        verticalArrangement = Arrangement.spacedBy(CirclesConstants.MAIN_LAYOUT_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress indicator
        if (progress.isDownloading) {
            UnifiedProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Error display
        uiState.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CirclesConstants.ERROR_TEXT_COLOR.copy(alpha = CirclesConstants.ERROR_CONTAINER_ALPHA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = CirclesConstants.MAIN_LAYOUT_PADDING)
            ) {
                Text(
                    text = error.userMessage,
                    color = CirclesConstants.ERROR_TEXT_COLOR,
                    fontSize = CirclesConstants.ERROR_FONT_SIZE,
                    modifier = Modifier.padding(CirclesConstants.CARD_PADDING)
                )
            }
        }

        // Download items for different packs
        CirclesPackGrid(
            uiState = uiState,
            onDownloadClick = { packId, configId ->
                coroutineScope.launch {
                    viewModel.handleAction(CirclesImportUiAction.DownloadPack(packId, configId))
                }
            },
            onSelectClick = { packId, configId ->
                coroutineScope.launch {
                    viewModel.handleAction(CirclesImportUiAction.SelectPack(packId, configId))
                }
            },
            onDeleteClick = { packId, configId ->
                coroutineScope.launch {
                    viewModel.handleAction(CirclesImportUiAction.DeletePack(packId, configId))
                }
            },
            onCancelClick = { fullPackId ->
                coroutineScope.launch {
                    viewModel.handleAction(CirclesImportUiAction.CancelDownload(fullPackId))
                }
            }
        )

        // Status text
        CirclesStatusSection(entriesCount = uiState.entriesCount)

        // Import zip button - allow during individual downloads
        CirclesImportZipButton(
            onImportClick = {
                coroutineScope.launch {
                    viewModel.handleAction(CirclesImportUiAction.ImportFromZip)
                }
            }
        )

        // Clear all packs button - allow during individual downloads
        if (uiState.availablePacks.isNotEmpty()) {
            CirclesClearAllButton(
                onClearClick = {
                    coroutineScope.launch {
                        viewModel.handleAction(CirclesImportUiAction.ClearAllPacks)
                    }
                }
            )
        }
    }
}

/**
 * Grid of available circle packs
 */
@Composable
private fun CirclesPackGrid(
    uiState: CirclesImportUiState,
    onDownloadClick: (String, String) -> Unit,
    onSelectClick: (String, String) -> Unit,
    onDeleteClick: (String, String) -> Unit,
    onCancelClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CirclesConstants.PACK_ITEM_SPACING),
        modifier = modifier.fillMaxWidth()
    ) {
        CirclePackDefinitions.availablePacks.forEach { packDef ->
            CirclesPackItem(
                packDefinition = packDef,
                uiState = uiState,
                onDownloadClick = onDownloadClick,
                onSelectClick = onSelectClick,
                onDeleteClick = onDeleteClick,
                onCancelClick = onCancelClick
            )
        }
    }
}

/**
 * Individual circle pack item
 */
@Composable
private fun CirclesPackItem(
    packDefinition: org.mountaincircles.app.modules.circles.import.data.CirclePackDefinition,
    uiState: CirclesImportUiState,
    onDownloadClick: (String, String) -> Unit,
    onSelectClick: (String, String) -> Unit,
    onDeleteClick: (String, String) -> Unit,
    onCancelClick: (String) -> Unit
) {
    val packState = rememberPackState(packDefinition, uiState)
    val buttonState = rememberButtonState(packState, onDownloadClick, onSelectClick, onCancelClick)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = packState.buttonEnabled,
                onClick = buttonState.onClick,
            onLongClick = if (packState.isInstalled) {
                {
                    Logger.log("CIRCLES_UI", LogLevel.INFO, "Long press detected for pack: ${packDefinition.packId}/${packDefinition.configId}")
                    onDeleteClick(packDefinition.packId, packDefinition.configId)
                }
            } else null
            ),
        colors = CardDefaults.cardColors(
            containerColor = getPackContainerColor(packState)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CirclesConstants.CARD_PADDING),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = packDefinition.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (!packState.buttonEnabled) CirclesConstants.DISABLED_CONTENT_COLOR else Color.White
                )
                Text(
                    text = buttonState.buttonText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!packState.buttonEnabled) CirclesConstants.DISABLED_CONTENT_COLOR.copy(alpha = CirclesConstants.DISABLED_SUBTITLE_ALPHA) else Color.Gray.copy(alpha = CirclesConstants.DISABLED_SUBTITLE_ALPHA),
                    modifier = Modifier.padding(top = CirclesConstants.SUBTITLE_TOP_PADDING)
                )
            }

            getPackIcon(packState)
        }
    }
}

/**
 * Remember pack state to avoid recomputation
 */
@Composable
private fun rememberPackState(
    packDefinition: org.mountaincircles.app.modules.circles.import.data.CirclePackDefinition,
    uiState: CirclesImportUiState
): PackState {
    val packId = packDefinition.packId
    val configId = packDefinition.configId
    val downloadConfigId = "${configId}-4210"
    val fullPackId = "${packId}_${configId}"

    return PackState(
        packId = packId,
        configId = configId,
        downloadConfigId = downloadConfigId,
        fullPackId = fullPackId,
        isInstalled = uiState.availablePacks.contains("${packId}_${downloadConfigId}"),
        isActive = uiState.activePackId == "${packId}_${downloadConfigId}",
        isDownloading = uiState.activeDownloadPackId == fullPackId,
        isDownloadActive = uiState.isDownloading,
        buttonEnabled = !uiState.isDownloading || uiState.activeDownloadPackId == fullPackId
    )
}

/**
 * Data class for pack state
 */
private data class PackState(
    val packId: String,
    val configId: String,
    val downloadConfigId: String,
    val fullPackId: String,
    val isInstalled: Boolean,
    val isActive: Boolean,
    val isDownloading: Boolean,
    val isDownloadActive: Boolean,
    val buttonEnabled: Boolean
)

/**
 * Remember button state and click handler
 */
@Composable
private fun rememberButtonState(
    packState: PackState,
    onDownloadClick: (String, String) -> Unit,
    onSelectClick: (String, String) -> Unit,
    onCancelClick: (String) -> Unit
): ButtonState {
    val (buttonText, onClick) = when {
        packState.isDownloading -> CirclesConstants.Text.DOWNLOADING_CANCEL to { onCancelClick(packState.fullPackId) }
        packState.isActive && packState.isInstalled -> CirclesConstants.Text.ACTIVE_PACK to {
            // Re-select active pack (same as select)
            onSelectClick(packState.packId, packState.downloadConfigId)
        }
        packState.isInstalled -> CirclesConstants.Text.TAP_TO_SELECT to { onSelectClick(packState.packId, packState.downloadConfigId) }
        else -> CirclesConstants.Text.TAP_TO_DOWNLOAD to { onDownloadClick(packState.packId, packState.configId) }
    }

    return ButtonState(buttonText, onClick)
}

/**
 * Data class for button state
 */
private data class ButtonState(
    val buttonText: String,
    val onClick: () -> Unit
)

/**
 * Get container color for pack card based on state
 */
@Composable
private fun getPackContainerColor(packState: PackState): Color {
    return when {
        !packState.buttonEnabled -> CirclesConstants.DISABLED_CONTAINER_COLOR
        packState.isActive && packState.isInstalled -> CirclesConstants.AVAILABLE_ACTIVE_CONTAINER_COLOR
        packState.isInstalled -> CirclesConstants.AVAILABLE_IDLE_CONTAINER_COLOR
        else -> MaterialTheme.colorScheme.surface
    }
}

/**
 * Get appropriate icon for pack based on state
 */
@Composable
private fun getPackIcon(packState: PackState) {
    when {
        packState.isDownloading -> {
            CircularProgressIndicator(
                modifier = Modifier.size(CirclesConstants.PROGRESS_INDICATOR_SIZE),
                color = CirclesConstants.PROGRESS_INDICATOR_COLOR,
                strokeWidth = CirclesConstants.PROGRESS_STROKE_WIDTH
            )
        }
        packState.isInstalled && !packState.isActive -> {
            // Show Target icon for installed but not active packs (matching original)
            Icon(
                painter = org.mountaincircles.app.ui.AppIcons.Target(),
                contentDescription = CirclesConstants.ContentDescriptions.AVAILABLE_PACK,
                tint = CirclesConstants.AVAILABLE_PACK_ICON_COLOR,
                modifier = Modifier.size(CirclesConstants.ICON_SIZE)
            )
        }
        packState.isInstalled && packState.isActive -> {
            // Show Check icon for active packs (matching BaseImportSection default behavior)
            Icon(
                painter = org.mountaincircles.app.ui.AppIcons.Check(),
                contentDescription = "Active Pack",
                tint = CirclesConstants.ACTIVE_PACK_ICON_COLOR,
                modifier = Modifier.size(CirclesConstants.ICON_SIZE)
            )
        }
        packState.isInstalled -> {
            // Show Check icon for installed but not active packs (fallback)
            Icon(
                painter = org.mountaincircles.app.ui.AppIcons.Check(),
                contentDescription = "Installed Pack",
                tint = CirclesConstants.ACTIVE_PACK_ICON_COLOR,
                modifier = Modifier.size(CirclesConstants.ICON_SIZE)
            )
        }
        else -> {
            Text(
                text = CirclesConstants.Text.DOWNLOAD_ARROW,
                fontSize = CirclesConstants.DOWNLOAD_ARROW_FONT_SIZE,
                color = if (!packState.buttonEnabled) CirclesConstants.DISABLED_CONTENT_COLOR else Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}

/**
 * Status section showing pack count
 */
@Composable
private fun CirclesStatusSection(
    entriesCount: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = CirclesConstants.Text.PACKS_AVAILABLE.format(entriesCount),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Gray,
        modifier = modifier.padding(vertical = CirclesConstants.STATUS_PADDING_VERTICAL)
    )
}

/**
 * Import ZIP button
 */
@Composable
private fun CirclesImportZipButton(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    StandardButton(
        text = CirclesConstants.Text.IMPORT_ZIP,
        enabled = true, // Allow during individual downloads (matching original behavior)
        icon = org.mountaincircles.app.ui.AppIcons.Target(),
        onClick = onImportClick,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Clear all packs button
 */
@Composable
private fun CirclesClearAllButton(
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClearButton(
        text = CirclesConstants.Text.CLEAR_ALL_PACKS,
        enabled = true,
        onClick = onClearClick,
        modifier = modifier
    )
}

/**
 * Explanatory text for circles functionality
 */
@Composable
private fun CirclesExplanatoryText(
    modifier: Modifier = Modifier
) {
    Text(
        text = CirclesConstants.Text.EXPLANATORY,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = CirclesConstants.EXPLANATORY_FONT_SIZE,
            lineHeight = CirclesConstants.EXPLANATORY_LINE_HEIGHT
        ),
        color = Color.Gray,
        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
        modifier = modifier
    )
}
