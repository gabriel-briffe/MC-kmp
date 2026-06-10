package org.mountaincircles.app.offline

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Scrollable on-device HTTP trace — separate from the download progress bar.
 */
@Composable
fun OfflineDownloadDebugPanel(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val lines by OfflineDownloadHttpTracker.lines.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var copiedHint by remember { mutableStateOf(false) }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    LaunchedEffect(copiedHint) {
        if (copiedHint) {
            kotlinx.coroutines.delay(2000)
            copiedHint = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 72.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC111111), RoundedCornerShape(8.dp))
                .padding(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (copiedHint) "Copied to clipboard" else "HTTP trace (offline download)",
                    color = if (copiedHint) Color(0xFFA5D6A7) else Color(0xFF90CAF9),
                    fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            copyTraceToClipboard(context, lines)
                            copiedHint = true
                        },
                        enabled = lines.isNotEmpty(),
                    ) {
                        Text("Copy", fontSize = 12.sp, color = Color.White)
                    }
                    TextButton(onClick = onClose) {
                        Text("Close", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
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
}

internal fun copyTraceToClipboard(context: Context, lines: List<String>) {
    val text = lines.joinToString(separator = "\n")
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Offline download HTTP trace", text))
}
