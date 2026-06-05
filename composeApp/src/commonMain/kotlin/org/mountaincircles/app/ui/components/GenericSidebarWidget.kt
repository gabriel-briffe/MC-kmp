package org.mountaincircles.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * Generic sidebar widget container - provides consistent collapsible behavior for all modules
 */
@Composable
fun GenericSidebarWidget(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    onExpanded: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    CollapsibleSidebarWidget(
        title = title,
        modifier = modifier,
        initiallyExpanded = initiallyExpanded,
        onExpanded = onExpanded,
        content = content
    )
}

@Composable
fun GenericSidebarWidget(
    title: String,
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onExpanded: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    CollapsibleSidebarWidget(
        title = title,
        modifier = modifier,
        isExpanded = isExpanded,
        onExpandedChange = onExpandedChange,
        onExpanded = onExpanded,
        content = content
    )
}
