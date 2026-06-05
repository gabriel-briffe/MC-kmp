package org.mountaincircles.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/**
 * Configuration for the generic bottom sheet behavior
 */
data class BottomSheetConfig(
    val initialHeightFraction: Float = 0.5f, // Start at 50% of screen height
    val minHeightFraction: Float = 0.1f, // Minimum 10% of screen height
    val maxHeightFraction: Float = 0.9f, // Maximum 90% of screen height
    val containerColor: Color = Color.Black.copy(alpha = 0.95f),
    val contentColor: Color = Color.White,
    val dragHandleHeight: Float = 48f, // Height in dp of drag area
    val overlayAlpha: Float = 0.4f // Background overlay alpha
)

/**
 * A generic bottom sheet that stays exactly where the user releases it.
 * Unlike standard bottom sheets, this doesn't snap to predefined positions.
 * 
 * This is the new standard bottom sheet component for the entire app.
 */
@Composable
fun GenericBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    config: BottomSheetConfig = BottomSheetConfig(),
    modifier: Modifier = Modifier,
    scrollToTopTrigger: Boolean = false, // Trigger to scroll to top
    fullWidthContent: List<@Composable () -> Unit> = emptyList(), // List of full-width content composables
    contentHasOwnScrolling: Boolean = false, // If true, don't add verticalScroll to content area
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState() // Scroll state for content area

    // Track the current height as a fraction of screen height
    val heightFraction = remember { Animatable(if (visible) config.initialHeightFraction else 0f) }
    
    // Handle visibility changes
    LaunchedEffect(visible) {
        if (visible) {
            heightFraction.animateTo(
                targetValue = config.initialHeightFraction,
                animationSpec = tween(durationMillis = 300)
            )
        } else {
            heightFraction.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200)
            )
        }
    }

    // Handle scroll-to-top trigger (synchronous, no animation)
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger) {
            scrollState.scrollTo(0) // Synchronous scroll to top
        }
    }
    
    if (visible || heightFraction.value > 0f) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .zIndex(1000f) // Ensure it's above other content
        ) {
            val maxHeight = maxHeight
            val currentHeight = maxHeight * heightFraction.value
            
            // Semi-transparent background overlay
            if (heightFraction.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = config.overlayAlpha * heightFraction.value))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    // Dismiss when tapping on background
                                    scope.launch {
                                        heightFraction.animateTo(0f)
                                        onDismiss()
                                    }
                                }
                            )
                        }
                )
            }
            
            // The actual bottom sheet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentHeight)
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(config.containerColor)
            ) {
                // Fixed drag handle area (not scrollable)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(config.dragHandleHeight.dp) // Configurable drag area
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    // Snap to min/max if needed, otherwise stay where released
                                    scope.launch {
                                        val currentFraction = heightFraction.value
                                        when {
                                            currentFraction < config.minHeightFraction -> {
                                                // If below minimum, either dismiss or snap to minimum
                                                if (currentFraction < config.minHeightFraction * 0.5f) {
                                                    heightFraction.animateTo(0f)
                                                    onDismiss()
                                                } else {
                                                    heightFraction.animateTo(config.minHeightFraction)
                                                }
                                            }
                                            currentFraction > config.maxHeightFraction -> {
                                                heightFraction.animateTo(config.maxHeightFraction)
                                            }
                                            // Otherwise, stay exactly where it was released
                                        }
                                    }
                                }
                            ) { _, dragAmount ->
                                // Convert drag amount to height fraction change
                                val dragInFraction = with(density) {
                                    -dragAmount.y / maxHeight.toPx() // Negative because drag up = increase height
                                }
                                
                                scope.launch {
                                    val newFraction = (heightFraction.value + dragInFraction)
                                        .coerceIn(0f, 1f) // Allow going to 0 for dismiss gesture
                                    heightFraction.snapTo(newFraction)
                                }
                            }
                        },
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    // Drag handle indicator
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(
                                Color.Gray.copy(alpha = 0.6f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
                
                // Single scrollable content area containing both padded and full-width sections
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Take remaining space
                        .then(
                            if (!contentHasOwnScrolling) Modifier.verticalScroll(scrollState)
                            else Modifier // Don't add verticalScroll if content has its own scrolling
                        )
                    // Removed pointerInput to allow child components (like sliders) to handle their own interactions
                ) {
                    CompositionLocalProvider(LocalContentColor provides config.contentColor) {
                        // Vertical column for proper stacking of content and full-width sections
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Normal content (padding applied by content composable itself)
                            content()

                            // Full-width section for images (no padding)
                            fullWidthContent.forEach { contentComposable ->
                                contentComposable()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility - old FreeFormBottomSheet function
 */
@Composable
fun FreeFormBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialHeightFraction: Float = 0.5f,
    minHeightFraction: Float = 0.1f,
    maxHeightFraction: Float = 0.9f,
    containerColor: Color = Color.Black.copy(alpha = 0.95f),
    contentColor: Color = Color.White,
    scrollToTopTrigger: Boolean = false, // Trigger to scroll to top
    content: @Composable () -> Unit
) {
    GenericBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        config = BottomSheetConfig(
            initialHeightFraction = initialHeightFraction,
            minHeightFraction = minHeightFraction,
            maxHeightFraction = maxHeightFraction,
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = modifier,
        scrollToTopTrigger = scrollToTopTrigger,
        content = content
    )
}

/**
 * Pre-configured bottom sheet for main menu
 */
object BottomSheetConfigs {
    val MainMenu = BottomSheetConfig(
        initialHeightFraction = 0.75f,
        minHeightFraction = 0.3f,
        maxHeightFraction = 0.9f
    )
    
    val Settings = BottomSheetConfig(
        initialHeightFraction = 0.75f,
        minHeightFraction = 0.2f,
        maxHeightFraction = 0.9f
    )
    
    val ModuleSheet = BottomSheetConfig(
        initialHeightFraction = 0.75f,
        minHeightFraction = 0.2f,
        maxHeightFraction = 0.9f
    )

    val ImportSheet = BottomSheetConfig(
        initialHeightFraction = 0.75f,
        minHeightFraction = 0.2f,
        maxHeightFraction = 0.9f
    )

    val WidgetSheet = BottomSheetConfig(
        initialHeightFraction = 0.50f,
        minHeightFraction = 0.2f,
        maxHeightFraction = 0.9f
    )
}

/**
 * State holder for bottom sheets
 */
@Stable
class FreeFormBottomSheetState(
    initialVisible: Boolean = false,
    val initialHeightFraction: Float = 0.5f
) {
    private val _visible = mutableStateOf(initialVisible)
    val visible: Boolean by _visible
    
    fun show() {
        _visible.value = true
    }
    
    fun hide() {
        _visible.value = false
    }
    
    fun toggle() {
        _visible.value = !_visible.value
    }
}

/**
 * Remember a FreeFormBottomSheetState
 */
@Composable
fun rememberFreeFormBottomSheetState(
    initialVisible: Boolean = false,
    initialHeightFraction: Float = 0.5f
): FreeFormBottomSheetState {
    return remember {
        FreeFormBottomSheetState(
            initialVisible = initialVisible,
            initialHeightFraction = initialHeightFraction
        )
    }
}
