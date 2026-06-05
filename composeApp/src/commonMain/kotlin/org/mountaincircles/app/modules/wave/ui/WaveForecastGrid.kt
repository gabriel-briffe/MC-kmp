package org.mountaincircles.app.modules.wave.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.mountaincircles.app.modules.wave.import.logic.WaveImportType
import org.mountaincircles.app.modules.wave.ui.WindRegion
import org.mountaincircles.app.ui.components.UnifiedProgress
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.ui.theme.AppTheme

/**
 * Extracted forecast grid component for better separation of concerns
 * Handles the display of forecast download items
 */
@Composable
fun WaveForecastGrid(
    uiState: WaveImportUiState,
    progress: UnifiedProgress,
    onDownloadClick: (WaveImportType) -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(WaveConstants.GRID_ITEM_SPACING),
        modifier = modifier.fillMaxWidth()
    ) {
        WaveConstants.FORECAST_TYPES.forEach { forecastType ->
            WaveForecastItem(
                forecastType = forecastType,
                uiState = uiState,
                progress = progress,
                onDownloadClick = onDownloadClick,
                onCancelClick = onCancelClick
            )
        }
    }
}

/**
 * Individual forecast download item - extracted from main composable
 */
@Composable
private fun WaveForecastItem(
    forecastType: WaveImportType,
    uiState: WaveImportUiState,
    progress: UnifiedProgress,
    onDownloadClick: (WaveImportType) -> Unit,
    onCancelClick: () -> Unit
) {
    val isDownloading = progress.isDownloading && progress.fileName?.contains(forecastType.name) == true
    val isAvailable = uiState.availableForecasts.contains(forecastType)

    // Determine button behavior based on download state
    val isDownloadActive = progress.isDownloading  // Any download active
    val isThisItemActive = isDownloading           // This specific item is active
    val buttonEnabled = true  // Always enabled - click to cancel any download or start new one

    val buttonOnClick = if (isDownloadActive) {
        onCancelClick  // Cancel any active download when clicked
    } else {
        { onDownloadClick(forecastType) }  // Start download if none active
    }

    // Colors using constants
    val containerColor = when {
        !buttonEnabled -> WaveConstants.DISABLED_CONTAINER_COLOR
        isThisItemActive && isAvailable -> WaveConstants.AVAILABLE_ACTIVE_CONTAINER_COLOR
        isAvailable -> WaveConstants.AVAILABLE_IDLE_CONTAINER_COLOR
        else -> AppTheme.Buttons.standardContainerColor
    }

    val contentColor = when {
        !buttonEnabled -> WaveConstants.DISABLED_CONTENT_COLOR
        else -> AppTheme.Buttons.standardContentColor
    }

    val borderColor = when {
        isThisItemActive && isAvailable -> WaveConstants.ACTIVE_BORDER_COLOR
        else -> WaveConstants.TRANSPARENT_BORDER
    }

    val borderWidth = if (isThisItemActive && isAvailable) WaveConstants.ACTIVE_BORDER_WIDTH else WaveConstants.INACTIVE_BORDER_WIDTH

    // Card layout matching GenericImportItem exactly
    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(borderWidth, borderColor),
        shape = RoundedCornerShape(AppTheme.Buttons.standardCornerRadius),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = buttonEnabled,
                onClick = buttonOnClick,
                onLongClick = {}  // No long click action
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppTheme.Buttons.standardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Title and subtitle
            Column(modifier = Modifier.weight(1f)) {
                val title = when (forecastType) {
                    WaveImportType.TODAY -> WaveConstants.Titles.TODAY
                    WaveImportType.TOMORROW -> WaveConstants.Titles.TOMORROW
                    WaveImportType.YESTERDAY_FOR_TODAY -> WaveConstants.Titles.YESTERDAY_FOR_TODAY
                }

                Text(
                    text = title,
                    fontSize = WaveConstants.TITLE_FONT_SIZE,
                    fontWeight = FontWeight.Medium,
                    color = if (!buttonEnabled) contentColor else Color.White
                )

                val subtitle = when {
                    isDownloadActive -> WaveConstants.Text.DOWNLOADING_CANCEL  // Any button can cancel when downloading
                    isAvailable -> {
                        val waveCount = uiState.forecastWaveFileCounts[forecastType] ?: 0
                        val expectedWaveCount = WaveConstants.HOUR_LIST.size * WaveConstants.ISOBARE_LIST.size

                        val regionWindCounts = uiState.forecastWindFileCountsByRegion[forecastType] ?: emptyMap()
                        val expectedWindCountPerRegion = expectedWaveCount * 2  // U + V files per region

                        val windLines = regionWindCounts.entries
                            .filter { it.value > 0 } // Only show regions with files
                            .joinToString("\n") { (region, count) ->
                                val regionName = when (region) {
                                    WindRegion.MIDDLE_EAST -> "MiddleEast"
                                    WindRegion.MIDDLE_WEST -> "MiddleWest"
                                    WindRegion.NORTH -> "North"
                                    WindRegion.SOUTH -> "South"
                                }
                                "$regionName wind: $count/$expectedWindCountPerRegion"
                            }

                        if (windLines.isNotEmpty()) {
                            "wave: $waveCount/$expectedWaveCount\n$windLines"
                        } else {
                            "wave: $waveCount/$expectedWaveCount"
                        }
                    }
                    else -> WaveConstants.Text.TAP_TO_DOWNLOAD
                }

                Text(
                    text = subtitle,
                    fontSize = WaveConstants.SUBTITLE_FONT_SIZE,
                    color = if (!buttonEnabled) WaveConstants.DISABLED_CONTENT_COLOR.copy(alpha = WaveConstants.DISABLED_SUBTITLE_ALPHA) else Color.Gray.copy(alpha = WaveConstants.DISABLED_SUBTITLE_ALPHA),
                    modifier = Modifier.padding(top = WaveConstants.SUBTITLE_TOP_PADDING)
                )
            }

            // Right side: Icon or progress indicator
            when {
                isDownloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(WaveConstants.PROGRESS_INDICATOR_SIZE),
                        color = if (!buttonEnabled) contentColor else Color.Blue,
                        strokeWidth = WaveConstants.PROGRESS_STROKE_WIDTH
                    )
                }
                isAvailable -> {
                    Icon(
                        painter = AppIcons.Wave(),
                        contentDescription = WaveConstants.ContentDescriptions.AVAILABLE_FORECAST,
                        modifier = Modifier.size(WaveConstants.ICON_SIZE),
                        tint = if (!buttonEnabled) contentColor else Color.White
                    )
                }
                else -> {
                    // Show download arrow or similar when not available
                    Text(
                        text = WaveConstants.Text.DOWNLOAD_ARROW,
                        fontSize = WaveConstants.DOWNLOAD_ARROW_FONT_SIZE,
                        color = if (!buttonEnabled) contentColor else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
