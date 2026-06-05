package org.mountaincircles.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.ui.theme.AppTheme

/**
 * Simple unified progress indicator component for all modules
 * Works with UnifiedProgress data class - always shows segmented progress
 *
 * @param progress The UnifiedProgress data to display
 * @param modifier Modifier for the component
 * @param showFileProgress Whether to show file progress (X of Y) in status text and right side
 */
@Composable
fun UnifiedProgressIndicator(
    progress: UnifiedProgress,
    modifier: Modifier = Modifier,
    showFileProgress: Boolean = true
) {
    // Log progress changes directly
    Logger.log("PROGRESS_BAR", LogLevel.INFO,
        "Progress bar display: '${progress.getDisplayInfo()}' (fileName='${progress.fileName}', current=${progress.current}, total=${progress.total}) - ${progress.computedPercentComplete}% - '${progress.status}'")

    Card(
        colors = CardDefaults.cardColors(containerColor = AppTheme.ProgressBar.containerColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppTheme.ProgressBar.cardPadding)
        ) {
            // Status and progress info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status text (file progress shown separately in top right)
                val statusText = progress.status

                Text(
                    text = statusText,
                    style = AppTheme.Typography.progressStatus,
                    modifier = Modifier.weight(1f)
                )

                // Show file progress for multi-file downloads, percentage for current file
                if (showFileProgress && progress.hasFileProgress && progress.total > 1) {
                    // Multi-file download: show "X of Y" in top right
                    Text(
                        text = "${progress.current} of ${progress.total}",
                        style = AppTheme.Typography.progressPercentage,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = AppTheme.ProgressBar.percentageMinWidth)
                    )
                } else if (progress.hasByteProgress && progress.totalBytes > 0) {
                    // Single file or byte-level progress: show current file percentage
                    val currentFilePercent = ((progress.bytesDownloaded * 100) / progress.totalBytes).toInt().coerceIn(0, 100)
                    Text(
                        text = "${currentFilePercent}%",
                        style = AppTheme.Typography.progressPercentage,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = AppTheme.ProgressBar.percentageMinWidth)
                    )
                } else if (progress.computedPercentComplete >= 0) {
                    // Fallback to computed percentage
                    Text(
                        text = "${progress.computedPercentComplete}%",
                        style = AppTheme.Typography.progressPercentage,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = AppTheme.ProgressBar.percentageMinWidth)
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.ProgressBar.itemSpacing))

            // Progress bar - always segmented for unified experience
            SegmentedProgressBar(
                progress = progress,
                barHeight = AppTheme.ProgressBar.barHeight,
                barColor = AppTheme.ProgressBar.barColor,
                trackColor = AppTheme.ProgressBar.trackColor
            )

            Spacer(modifier = Modifier.height(AppTheme.ProgressBar.itemSpacing))

            // File info and counters row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File information
                Text(
                    text = progress.getDisplayInfo(),
                    style = AppTheme.Typography.progressDetail,
                    modifier = Modifier.weight(1f)
                )

                // Success/Failed counters (if any)
                if (progress.successCount > 0 || progress.failedCount > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (progress.successCount > 0) {
                            Text(
                                text = "✓${progress.successCount}",
                                style = AppTheme.Typography.progressCounter.copy(
                                    color = AppTheme.ProgressBar.successColor
                                )
                            )
                        }

                        if (progress.failedCount > 0) {
                            Text(
                                text = "✗${progress.failedCount}",
                                style = AppTheme.Typography.progressCounter.copy(
                                    color = AppTheme.ProgressBar.errorColor
                                )
                            )
                        }
                    }
                }

                // File size information
                if (progress.hasByteProgress) {
                    Text(
                        text = progress.formatBytes(),
                        style = AppTheme.Typography.progressDetail,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

/**
 * Segmented progress bar that shows individual file progress
 * Each file gets an equal segment, with current file showing byte-level progress
 * Works for any number of files (1+ segments) with seamless visual flow
 * Always used for unified progress experience across all modules
 */
@Composable
private fun SegmentedProgressBar(
    progress: UnifiedProgress,
    barHeight: androidx.compose.ui.unit.Dp,
    barColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight),
        horizontalArrangement = Arrangement.Start // No spacing between segments
    ) {
        val totalFiles = progress.total
        val completedFiles = progress.current // Files before current are complete
        val segmentWidth = 1f / totalFiles // Equal width for each file

        for (fileIndex in 0 until totalFiles) {
            // For single file (totalFiles == 1), treat it as the current file
            val effectiveCurrent = if (totalFiles == 1) 0 else progress.current
            val effectiveCompleted = if (totalFiles == 1) 0 else progress.current

            val isCompleted = fileIndex < effectiveCompleted
            val isCurrent = fileIndex == effectiveCurrent
            val isPending = fileIndex > effectiveCurrent

            // Calculate progress for this segment
            val segmentProgress = when {
                isCompleted -> 1f // Fully completed
                isCurrent -> {
                    // Current file: show byte-level progress within this segment
                    if (progress.hasByteProgress && progress.totalBytes > 0) {
                        progress.bytesDownloaded.toFloat() / progress.totalBytes.toFloat()
                    } else if (progress.computedPercentComplete >= 0) {
                        progress.computedPercentComplete / 100f
                    } else {
                        0.5f // Default to 50% if no progress available
                    }
                }
                isPending -> 0f // Not started yet
                else -> 0f
            }

            // Segment color based on status
            val segmentColor = when {
                isCompleted -> barColor // Completed: full color
                isCurrent -> barColor.copy(alpha = 0.8f) // Current: slightly transparent
                else -> trackColor.copy(alpha = 0.3f) // Pending: very light
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Background (track)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(trackColor.copy(alpha = 0.2f))
                )

                // Progress fill
                if (segmentProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(segmentProgress)
                            .fillMaxHeight()
                            .background(segmentColor)
                    )
                }

            }
        }
    }
}
