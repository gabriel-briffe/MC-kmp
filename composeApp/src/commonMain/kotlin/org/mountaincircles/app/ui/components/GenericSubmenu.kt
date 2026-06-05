package org.mountaincircles.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Generic submenu theme for consistent styling across all module submenus
 */
object SubmenuTheme {
    object Colors {
        val background = Color.Black.copy(alpha = 0.85f)
        val surface = Color.Gray.copy(alpha = 0.3f)
        val iconEnabled = Color.White
        val iconDisabled = Color.Gray
        val primary = Color.Cyan
        val textPrimary = Color.White
        val textSecondary = Color.Gray
    }
    
    object Dimensions {
        val height = 52.dp
        val cardElevation = 0.dp
        val cornerRadius = 4.dp
        val iconSize = 40.dp
        val iconInnerSize = 20.dp
        val compactIconSize = 32.dp  // For submenus with many controls
        val compactIconInnerSize = 16.dp
    }
    
    object Spacing {
        val horizontal = 8.dp  // Reduced to give more space for controls
        val vertical = 8.dp
        val controls = 16.dp
        val compact = 8.dp
        val tight = 4.dp
    }
}

/**
 * Generic submenu container that provides consistent styling
 * for all module submenus
 */
@Composable
fun GenericSubmenu(
    modifier: Modifier = Modifier,
    spacing: Arrangement.Horizontal = Arrangement.spacedBy(SubmenuTheme.Spacing.controls),
    content: @Composable RowScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(SubmenuTheme.Dimensions.height),
        colors = CardDefaults.cardColors(containerColor = SubmenuTheme.Colors.background),
        shape = RoundedCornerShape(0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = SubmenuTheme.Dimensions.cardElevation)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 30% black overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
            )

            // Content on top of overlay
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = spacing,
                content = content
            )
        }
    }
}
