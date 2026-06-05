package org.mountaincircles.app.modules.wave.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/**
 * Extracted status section component
 * Displays the number of files currently in memory with wave/wind breakdown
 */
@Composable
fun WaveStatusSection(
    entriesCount: Int,
    waveFileCount: Int,
    windFileCount: Int,
    waveFileSize: Long,
    windFileSize: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = WaveConstants.STATUS_PADDING_VERTICAL),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Total line
        val totalSize = waveFileSize + windFileSize
        Text(
            text = "Total: $entriesCount files - ${formatFileSize(totalSize)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        // Wave line
        if (waveFileCount > 0) {
            Text(
                text = "Wave: $waveFileCount files - ${formatFileSize(waveFileSize)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray.copy(alpha = 0.8f)
            )
        }

        // Wind line
        if (windFileCount > 0) {
            Text(
                text = "Wind: $windFileCount files - ${formatFileSize(windFileSize)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Format file size in human readable format
 */
fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
