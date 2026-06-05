package org.mountaincircles.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Generic Checkbox List Widget for sidebar filters
 * Reusable component for airspace, airports, and other type-based filtering
 */
@Composable
fun CheckboxListWidget(
    hasData: Boolean,
    availableItems: Set<String>,
    visibleItems: Set<String>,
    getDisplayName: (String) -> String,
    getColor: (String) -> Color,
    onCheckedChange: (String, Boolean) -> Unit,
    emptyDataMessage: String,
    noItemsMessage: String,
    sortItems: (Set<String>) -> List<String> = { it.toList() },
    modifier: Modifier = Modifier
) {
    CheckboxListContent(
        hasData = hasData,
        availableItems = availableItems,
        visibleItems = visibleItems,
        getDisplayName = getDisplayName,
        getColor = getColor,
        onCheckedChange = onCheckedChange,
        emptyDataMessage = emptyDataMessage,
        noItemsMessage = noItemsMessage,
        sortItems = sortItems
    )
}

@Composable
private fun CheckboxListContent(
    hasData: Boolean,
    availableItems: Set<String>,
    visibleItems: Set<String>,
    getDisplayName: (String) -> String,
    getColor: (String) -> Color,
    onCheckedChange: (String, Boolean) -> Unit,
    emptyDataMessage: String,
    noItemsMessage: String,
    sortItems: (Set<String>) -> List<String>
) {
    // Content based on state
    if (!hasData) {
        Text(
            text = emptyDataMessage,
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    } else {
        if (availableItems.isEmpty()) {
            Text(
                text = noItemsMessage,
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            // Sort and display items
            val sortedItems = sortItems(availableItems)

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                sortedItems.forEach { item ->
                    CheckboxItem(
                        item = item,
                        displayName = getDisplayName(item),
                        color = getColor(item),
                        isVisible = visibleItems.contains(item),
                        onCheckedChange = onCheckedChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckboxItem(
    item: String,
    displayName: String,
    color: Color,
    isVisible: Boolean,
    onCheckedChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
            .clickable { onCheckedChange(item, !isVisible) } // Make entire row clickable
    ) {
        Checkbox(
            checked = isVisible,
            onCheckedChange = { checked -> onCheckedChange(item, checked) },
            colors = CheckboxDefaults.colors(
                checkedColor = color,
                uncheckedColor = Color.Gray,
                checkmarkColor = Color.White
            )
        )

        Text(
            text = displayName,
            color = if (isVisible) Color.White else Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)  // Balanced horizontal padding
        )
    }
}
