package org.mountaincircles.app.modules.maps.ui

import androidx.compose.foundation.BorderStroke
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
import org.mountaincircles.app.ui.AppIcons
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.ui.theme.AppTheme
import org.mountaincircles.app.ui.components.UnifiedProgress
import org.mountaincircles.app.ui.components.UnifiedProgressIndicator
import org.mountaincircles.app.ui.import.DownloadItem
import org.mountaincircles.app.ui.import.DownloadState

/**
 * Maps import section composable - uses ViewModel pattern
 * Separates UI logic from business logic following the same architecture as Wave and Circles modules
 */
@Composable
fun MapsImportComposable(
    viewModel: MapsImportViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val progress by viewModel.progressFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Base layout matching BaseImportSection.BaseLayout
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress section matching BaseImportSection.ProgressSection
        if (progress.isDownloading) {
            org.mountaincircles.app.ui.components.UnifiedProgressIndicator(
                progress = progress,
                showFileProgress = true
            )
        }

        // Error display matching BaseImportSection.ErrorSection
        if (uiState.error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                androidx.compose.material3.Text(
                    text = "Error: ${uiState.error?.userMessage}",
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Import section content (module-specific)
        ImportSectionContent(progress, coroutineScope, uiState, viewModel)
    }

    // Add DisposableEffect to reset download state when leaving
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetDownloadState()
        }
    }
}

@Composable
private fun ImportSectionContent(
    progress: UnifiedProgress,
    coroutineScope: CoroutineScope,
    uiState: MapsImportUiState,
    viewModel: MapsImportViewModel
) {
    // Create download items from ViewModel state
    val downloadItems = uiState.availableMaps.map { map ->
        // Capture stable values at creation time (safe pattern)
        val mapId = map.id
        val mapName = map.name
        val isInstalledAtCreation = uiState.installedMaps.contains(mapId)

        DownloadItem<String>(
            id = mapId,
            title = mapName,
            getSubtitle = { state ->
                val isInstalled = uiState.installedMaps.contains(mapId)
                when {
                    state.activeItem == mapId -> "Downloading... Tap to cancel"
                    uiState.downloadingMaps.contains(mapId) -> "Downloading..."
                    isInstalled -> "Long press to delete"
                    else -> "Tap to download"
                }
            },
            isInstalled = { uiState.installedMaps.contains(mapId) },
            isDownloading = { uiState.downloadingMaps.contains(mapId) },
            onClick = {
                // Use captured stable value instead of captured reference
                if (!isInstalledAtCreation) {
                    viewModel.handleAction(MapsImportUiAction.DownloadMap(mapId))
                }
            },
            onLongClick = {
                // Use captured stable value instead of captured reference
                if (isInstalledAtCreation) {
                    viewModel.handleAction(MapsImportUiAction.DeleteMap(mapId))
                }
            }
        )
    }

    // Render all download items - let the whole sheet scroll, don't use nested scrolling
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        downloadItems.forEach { item ->
            GenericImportItem(
                title = item.title,
                subtitle = item.getSubtitle(DownloadState(
                    activeItem = uiState.downloadingMaps.firstOrNull(),
                    isDownloadActive = uiState.downloadingMaps.isNotEmpty()
                )),
                isInstalled = item.isInstalled(),
                isDownloading = item.isDownloading(),
                progress = progress,
                isDownloadActive = uiState.downloadingMaps.isNotEmpty(),
                isThisItemActive = uiState.downloadingMaps.contains(item.id),
                onCancel = {
                    viewModel.handleAction(MapsImportUiAction.CancelDownload(item.id))
                },
                onClick = item.onClick,
                onLongClick = item.onLongClick ?: {}
            )
        }
    }
}

@Composable
private fun GenericImportItem(
    title: String,
    subtitle: String? = null,
    isInstalled: Boolean = false,
    isActive: Boolean = false,
    isDownloading: Boolean = false,
    progress: UnifiedProgress? = null,
    isDownloadActive: Boolean = false,
    isThisItemActive: Boolean = false,
    onCancel: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Determine button behavior based on download state
    val buttonEnabled = !isDownloadActive || isThisItemActive  // Enable only if no download OR this is active
    val buttonOnClick = if (isThisItemActive && onCancel != null) {
        { onCancel() }  // Cancel if this is the active download
    } else if (!isDownloadActive) {
        onClick  // Normal click if no download active
    } else {
        {}  // Disabled if other download is active
    }

    val containerColor = when {
        !buttonEnabled -> Color.Gray.copy(alpha = 0.2f)  // Greyed out when disabled
        isActive && isInstalled -> Color.Green.copy(alpha = 0.3f)
        isInstalled -> Color.Green.copy(alpha = 0.15f)
        else -> AppTheme.Buttons.standardContainerColor
    }

    val contentColor = when {
        !buttonEnabled -> Color.Gray.copy(alpha = 0.5f)  // Greyed out text/icons when disabled
        else -> AppTheme.Buttons.standardContentColor
    }

    val borderColor = when {
        isActive && isInstalled -> Color.Cyan
        else -> Color.Transparent
    }

    val borderWidth = if (isActive && isInstalled) 2.dp else 0.dp

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(borderWidth, borderColor),
        shape = RoundedCornerShape(AppTheme.Buttons.standardCornerRadius),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = buttonEnabled,
                onClick = buttonOnClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppTheme.Buttons.standardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (!buttonEnabled) contentColor else Color.White
                )
                subtitle?.let {
                    Text(
                        text = it,
                        fontSize = 10.sp,
                        color = if (!buttonEnabled) contentColor.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            when {
                isDownloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = if (!buttonEnabled) contentColor else Color.Blue,
                        strokeWidth = 2.dp
                    )
                }
                isInstalled && icon != null -> {
                    icon()
                }
                isInstalled -> {
                    Icon(
                        painter = AppIcons.Check(),
                        contentDescription = "Installed",
                        tint = if (!buttonEnabled) contentColor else Color.Green,
                        modifier = Modifier.size(24.dp)
                    )
                }
                else -> {
                    Icon(
                        painter = AppIcons.Download(),
                        contentDescription = "Download",
                        tint = if (!buttonEnabled) contentColor else Color.Blue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
