package org.mountaincircles.app.modules.wave.ui

// import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.mountaincircles.app.ui.components.UnifiedProgress
import org.mountaincircles.app.ui.components.UnifiedProgressIndicator
import org.mountaincircles.app.ui.components.ClearButton
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.logger.LogLevel
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text

/**
 * New Wave Import Composable using ViewModel pattern
 * Replaces WaveImportSectionImpl with clean separation of concerns
 * Maintains the exact same visual layout as the original BaseImportSection
 */
@Composable
fun WaveImportComposable(
    viewModel: WaveImportViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val progress by viewModel.progressFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val onClearAllClick: () -> Unit = {
        coroutineScope.launch {
            viewModel.handleAction(WaveImportUiAction.ClearAllFiles)
        }
    }

    // CLEANUP: Reset download state when import sheet is dismissed (matching original)
    DisposableEffect(Unit) {
        onDispose {
            Logger.log("WAVE_UI", LogLevel.INFO, "🗑️ WaveImportComposable disposed - resetting download state")
            coroutineScope.launch {
                try {
                    viewModel.resetDownloadState()
                } catch (e: Exception) {
                    Logger.log("WAVE_UI", LogLevel.ERROR, "Failed to reset download state on dispose: ${e.message}")
                }
            }
        }
    }

    // Base layout matching BaseImportSection.BaseLayout
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(WaveConstants.MAIN_LAYOUT_PADDING),
        verticalArrangement = Arrangement.spacedBy(WaveConstants.MAIN_LAYOUT_SPACING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Wind files checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = uiState.includeWindFiles,
                onCheckedChange = { checked ->
                    coroutineScope.launch {
                        viewModel.handleAction(WaveImportUiAction.ToggleIncludeWindFiles(checked))
                    }
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color.Blue,
                    uncheckedColor = Color.Gray,
                    checkmarkColor = Color.White
                )
            )
            Text(
                text = "Add wind files",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Wind region checkboxes (only show when wind files are enabled)
        if (uiState.includeWindFiles) {
            // Region checkboxes in a single column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WindRegionCheckbox(
                    region = WindRegion.NORTH,
                    displayName = "North (375 MB)",
                    isSelected = uiState.selectedWindRegions.contains(WindRegion.NORTH),
                    onToggle = { region, selected ->
                        coroutineScope.launch {
                            viewModel.handleAction(WaveImportUiAction.ToggleWindRegion(region, selected))
                        }
                    }
                )

                WindRegionCheckbox(
                    region = WindRegion.MIDDLE_WEST,
                    displayName = "Middle West (West Rhone valley - 225 MB)",
                    isSelected = uiState.selectedWindRegions.contains(WindRegion.MIDDLE_WEST),
                    onToggle = { region, selected ->
                        coroutineScope.launch {
                            viewModel.handleAction(WaveImportUiAction.ToggleWindRegion(region, selected))
                        }
                    }
                )

                WindRegionCheckbox(
                    region = WindRegion.MIDDLE_EAST,
                    displayName = "Middle East (Alps - 180 MB)",
                    isSelected = uiState.selectedWindRegions.contains(WindRegion.MIDDLE_EAST),
                    onToggle = { region, selected ->
                        coroutineScope.launch {
                            viewModel.handleAction(WaveImportUiAction.ToggleWindRegion(region, selected))
                        }
                    }
                )

                WindRegionCheckbox(
                    region = WindRegion.SOUTH,
                    displayName = "South (195 MB)",
                    isSelected = uiState.selectedWindRegions.contains(WindRegion.SOUTH),
                    onToggle = { region, selected ->
                        coroutineScope.launch {
                            viewModel.handleAction(WaveImportUiAction.ToggleWindRegion(region, selected))
                        }
                    }
                )
            }
        }

        // Progress indicator (matching BaseImportSection.ProgressSection)
        if (progress.isDownloading) {
            UnifiedProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Error display (matching BaseImportSection.ErrorSection)
        uiState.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = WaveConstants.ERROR_CONTAINER_ALPHA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = WaveConstants.MAIN_LAYOUT_PADDING)
            ) {
                Text(
                    text = error.userMessage,
                    color = Color.Red,
                    fontSize = WaveConstants.ERROR_FONT_SIZE,
                    modifier = Modifier.padding(WaveConstants.CARD_PADDING)
                )
            }
        }

        // Download items for different forecast types
        WaveForecastGrid(
            uiState = uiState,
            progress = progress,
            onDownloadClick = { forecastType ->
                coroutineScope.launch {
                    viewModel.handleAction(WaveImportUiAction.DownloadForecast(forecastType.name))
                }
            },
            onCancelClick = {
                coroutineScope.launch {
                    viewModel.handleAction(WaveImportUiAction.CancelDownload)
                }
            }
        )

        // Files in memory status text
        val totalWaveFiles = viewModel.getTotalWaveFilesInMemory()
        val totalWindFiles = viewModel.getTotalWindFilesInMemory()
        val totalWaveFileSize = viewModel.getTotalWaveFileSize()
        val totalWindFileSize = viewModel.getTotalWindFileSize()
        WaveStatusSection(
            entriesCount = uiState.entriesCount,
            waveFileCount = totalWaveFiles,
            windFileCount = totalWindFiles,
            waveFileSize = totalWaveFileSize,
            windFileSize = totalWindFileSize
        )

        // Clear all button - only show when there are files
        if (uiState.entriesCount > 0) {
            ClearButton(
                text = WaveConstants.Text.CLEAR_ALL_FILES,
                onClick = onClearAllClick,
                enabled = true, // Allow clearing during individual downloads
                icon = org.mountaincircles.app.ui.AppIcons.Close()
            )
        }
    }
}

/**
 * Individual wind region checkbox component
 */
@Composable
private fun WindRegionCheckbox(
    region: WindRegion,
    displayName: String,
    isSelected: Boolean,
    onToggle: (WindRegion, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { checked ->
                onToggle(region, checked)
            },
            colors = CheckboxDefaults.colors(
                checkedColor = Color.Blue,
                uncheckedColor = Color.Gray,
                checkmarkColor = Color.White
            )
        )
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}


