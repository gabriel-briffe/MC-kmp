package org.mountaincircles.app.widget.glance

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

private const val TAG = "GlanceWidget_WaveTodayLayout"

/**
 * Colors for the wave widget (matching meteogram glance widget exactly).
 */
private object WaveWidgetColors {
    val background = Color(0xFF010203)
    val headerBackground = Color(0xFF222222)
    val textPrimary = Color.White
    val textSecondary = Color(0xFFAAAAAA)
}

/**
 * Main layout for the Wave Today Glance widget.
 * 
 * Structure:
 * - ImageContent: Wave forecast image (fills available space)
 * - TitleRow: "TODAY dd/MM 12h 4200m"
 * - RefreshRow: "HH:mm ↻"
 */
@Composable
fun WaveTodayGlanceLayout(
    state: WaveTodayState,
    onRefreshClick: () -> Unit
) {
    Logger.log(TAG, LogLevel.DEBUG, "WaveTodayGlanceLayout: state=${state::class.simpleName}")
    
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WaveWidgetColors.background))
    ) {
        when (state) {
            is WaveTodayState.Loading -> {
                LoadingContent()
                TitleRow(title = "Loading...")
                RefreshRow(
                    lastRefreshTime = null,
                    isLoading = true,
                    onRefreshClick = onRefreshClick
                )
            }
            is WaveTodayState.Refreshing -> {
                // Show terrain image with spinner overlay, no title row
                RefreshingContent(terrainBitmap = state.terrainBitmap)
                // Empty refresh row with just spinner
                RefreshRow(
                    lastRefreshTime = null,
                    isLoading = true,
                    onRefreshClick = onRefreshClick
                )
            }
            is WaveTodayState.Available -> {
                ImageContent(bitmap = state.imageBitmap)
                TitleRow(title = state.titleText)
                RefreshRow(
                    lastRefreshTime = state.lastRefreshTime,
                    isLoading = false,
                    onRefreshClick = onRefreshClick
                )
            }
            is WaveTodayState.Unavailable -> {
                NoDataContent(message = state.message)
                TitleRow(title = "Wave Today")
                RefreshRow(
                    lastRefreshTime = null,
                    isLoading = false,
                    onRefreshClick = onRefreshClick
                )
            }
        }
    }
}

/**
 * Wave forecast image - fills available space with center crop (like legacy widget).
 */
@Composable
private fun ColumnScope.ImageContent(bitmap: Bitmap) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .defaultWeight(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Wave forecast",
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * Loading state content.
 */
@Composable
private fun ColumnScope.LoadingContent() {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .defaultWeight()
            .background(ColorProvider(WaveWidgetColors.headerBackground)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Loading wave data...",
            style = TextStyle(
                color = ColorProvider(WaveWidgetColors.textSecondary),
                fontSize = 12.sp
            )
        )
    }
}

/**
 * Refreshing state content - terrain image with spinner overlay.
 */
@Composable
private fun ColumnScope.RefreshingContent(terrainBitmap: Bitmap?) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .defaultWeight(),
        contentAlignment = Alignment.Center
    ) {
        // Show terrain image if available
        if (terrainBitmap != null) {
            Image(
                provider = ImageProvider(terrainBitmap),
                contentDescription = "Terrain background",
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback to dark background if no terrain
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(WaveWidgetColors.headerBackground))
            ) {}
        }
        
        // Spinner overlay centered on the image
        CircularProgressIndicator(
            modifier = GlanceModifier.size(32.dp),
            color = ColorProvider(WaveWidgetColors.textPrimary)
        )
    }
}

/**
 * No data state content.
 */
@Composable
private fun ColumnScope.NoDataContent(message: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .defaultWeight()
            .background(ColorProvider(WaveWidgetColors.headerBackground)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = TextStyle(
                color = ColorProvider(WaveWidgetColors.textSecondary),
                fontSize = 12.sp
            )
        )
    }
}

/**
 * Title row - displays wave forecast info.
 */
@Composable
private fun TitleRow(title: String) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(ColorProvider(WaveWidgetColors.headerBackground)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = ColorProvider(WaveWidgetColors.textPrimary),
                fontSize = 10.sp
            )
        )
    }
}

/**
 * Refresh row - shows last refresh time and refresh button. Empty when loading.
 */
@Composable
private fun RefreshRow(lastRefreshTime: String?, isLoading: Boolean, onRefreshClick: () -> Unit) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(20.dp)
            .background(ColorProvider(WaveWidgetColors.headerBackground))
            .clickable { if (!isLoading) onRefreshClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Show nothing when loading - just an empty row
        if (!isLoading) {
            if (lastRefreshTime != null) {
                Text(
                    text = lastRefreshTime,
                    style = TextStyle(
                        color = ColorProvider(WaveWidgetColors.textSecondary),
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
            }
            Box(
                modifier = GlanceModifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⟳",
                    style = TextStyle(
                        color = ColorProvider(WaveWidgetColors.textPrimary),
                        fontSize = 14.sp
                    )
                )
            }
        }
    }
}
