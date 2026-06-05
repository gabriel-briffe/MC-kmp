package org.mountaincircles.app.ui.overlay

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger

/**
 * Configuration for SwipeablePopup behavior
 */
data class SwipeablePopupConfig(
    val heightRatio: Float = 0.5f,
    val swipeThresholdDp: Int = 120,
    val containerColor: androidx.compose.ui.graphics.Color? = null,
    val containerAlpha: Float = 1.0f,
    val logTag: String = "SWIPEABLE_POPUP"
)

/**
 * Generic swipeable popup component that can be reused across different modules
 * Provides swipe-to-dismiss functionality with visual feedback
 */
@Composable
fun SwipeablePopup(
    config: SwipeablePopupConfig = SwipeablePopupConfig(),
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    // Swipe state isolated here - changes won't affect the content composable
    val swipeOffset = remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = config.swipeThresholdDp.dp.value

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(config.heightRatio)
            .offset(x = swipeOffset.floatValue.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        Logger.log(config.logTag, LogLevel.DEBUG, "Swipe started on popup")
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        // Only allow left swipe (negative values)
                        val newOffset = (swipeOffset.floatValue + dragAmount).coerceAtMost(0f)
                        swipeOffset.floatValue = newOffset
                    },
                    onDragEnd = {
                        Logger.log(config.logTag, LogLevel.DEBUG, "Swipe ended: offset=${swipeOffset.floatValue}, threshold=${-swipeThresholdPx}")

                        if (swipeOffset.floatValue < -swipeThresholdPx) {
                            // Swipe left successful - dismiss popup
                            Logger.log(config.logTag, LogLevel.INFO, "🔴 SWIPE: Swipe left detected - dismissing popup")
                            onDismiss()
                            Logger.log(config.logTag, LogLevel.INFO, "✅ SWIPE: Dismiss completed")
                        } else {
                            // Not enough swipe - animate back to original position
                            Logger.log(config.logTag, LogLevel.DEBUG, "Swipe cancelled - returning to original position")
                            swipeOffset.floatValue = 0f
                        }
                    },
                    onDragCancel = {
                        swipeOffset.floatValue = 0f
                        Logger.log(config.logTag, LogLevel.DEBUG, "Swipe cancelled")
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = config.containerColor?.copy(alpha = config.containerAlpha)
                ?: CardDefaults.cardColors().containerColor.copy(alpha = config.containerAlpha)
        )
    ) {
        content()
    }
}

/**
 * Common content wrapper for swipeable popups
 * Provides consistent padding, swipe hint, and layout structure
 */
@Composable
fun SwipeablePopupContent(
    modifier: Modifier = Modifier,
    showSwipeHint: Boolean = true,
    swipeHintText: String = "Swipe left to close",
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = 4.dp,
                bottom = 16.dp
            )
    ) {
        // Swipe hint in top right corner
        if (showSwipeHint) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = swipeHintText,
                    color = Color.Gray.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        // Main content
        content()
    }
}
