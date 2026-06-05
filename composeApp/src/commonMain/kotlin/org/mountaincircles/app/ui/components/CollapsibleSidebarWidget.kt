package org.mountaincircles.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mountaincircles.app.ui.AppIcons

/**
 * CompositionLocal that provides forced expansion state for sidebar widgets
 * When set to true, widgets should expand regardless of their internal state
 */
val LocalForcedExpansion = compositionLocalOf { false }

/**
 * Collapsible sidebar widget with instant expand/collapse
 */
@Composable
fun CollapsibleSidebarWidget(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    onExpanded: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    // Check if expansion is forced via CompositionLocal
    val forcedExpansion = LocalForcedExpansion.current
    
    // Internal state - can be overridden by forcedExpansion
    var internalExpanded by remember { mutableStateOf(initiallyExpanded) }
    
    // When forcedExpansion becomes true, expand the widget
    LaunchedEffect(forcedExpansion) {
        if (forcedExpansion && !internalExpanded) {
            internalExpanded = true
            onExpanded?.invoke()
        }
    }
    
    CollapsibleSidebarWidget(
        title = title,
        modifier = modifier,
        isExpanded = internalExpanded,
        onExpandedChange = { internalExpanded = it },
        onExpanded = onExpanded,
        content = content
    )
}

@Composable
fun CollapsibleSidebarWidget(
    title: String,
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onExpanded: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {

    Column(modifier = modifier.fillMaxWidth()) {
        // Header with clickable title and chevron
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val wasExpanded = isExpanded
                    val newExpanded = !isExpanded
                    onExpandedChange(newExpanded)
                    // Call onExpanded when transitioning from collapsed to expanded
                    if (!wasExpanded && newExpanded && onExpanded != null) {
                        onExpanded()
                    }
                }
                .padding(horizontal = 4.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Title on the left
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            // Chevron on the right
            Icon(
                painter = if (isExpanded) AppIcons.ExpandMore() else AppIcons.ExpandLess(),
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(if (isExpanded) 0f else -90f) // 0° = down, -90° = left
            )
        }

        // Collapsible content (no animation)
        if (isExpanded) {
            content()
        }
    }
}
