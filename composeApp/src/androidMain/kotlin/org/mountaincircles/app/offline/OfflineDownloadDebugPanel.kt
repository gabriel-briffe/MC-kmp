package org.mountaincircles.app.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Scrollable on-device HTTP trace — separate from the download progress bar.
 */
@Composable
fun OfflineDownloadDebugPanel(modifier: Modifier = Modifier) {
    val lines by OfflineDownloadHttpTracker.lines.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 72.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .background(Color(0xCC111111), RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            item {
                Text(
                    text = "HTTP trace (offline download)",
                    color = Color(0xFF90CAF9),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            items(lines) { line ->
                Text(
                    text = line,
                    color = when {
                        line.startsWith("→") -> Color(0xFFB0BEC5)
                        line.startsWith("←") -> Color(0xFFA5D6A7)
                        line.startsWith("✗") -> Color(0xFFFF8A80)
                        else -> Color(0xFF78909C)
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}
