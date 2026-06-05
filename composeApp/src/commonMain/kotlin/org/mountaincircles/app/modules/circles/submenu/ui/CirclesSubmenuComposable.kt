package org.mountaincircles.app.modules.circles.submenu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.mountaincircles.app.logger.LogLevel
import org.mountaincircles.app.logger.Logger
import org.mountaincircles.app.modules.circles.CirclesModule
import org.mountaincircles.app.ui.AppIcons
import org.mountaincircles.app.ui.components.GenericSubmenu
import org.mountaincircles.app.ui.components.SubmenuTheme

/**
 * Circles submenu that appears below the top menu
 * 
 * Provides controls for:
 * - Circles visibility toggle (lines and their labels)
 * - Sectors opacity slider (0-50%)
 */
@Composable
fun CirclesSubmenuComposable(
    module: CirclesModule,
    modifier: Modifier = Modifier
) {
    // ✅ First collect StateFlow to make it reactive in Compose
    val moduleState by module.circlesState.collectAsState()

    // ✅ Optimized: Only listen to specific properties needed
    val circlesVisibility by remember { derivedStateOf { moduleState.circlesVisibility } }
    val sectorsOpacity by remember { derivedStateOf { moduleState.sectorsOpacity } }

    val coroutineScope = rememberCoroutineScope()
    
    GenericSubmenu(modifier = modifier) {
        // 1. Visibility toggle button
        IconButton(
            onClick = {
                Logger.log("CIRCLES_UI", LogLevel.INFO, "Circles visibility toggle clicked")
                coroutineScope.launch {
                    module.toggleVisibility()
                }
            },
            modifier = Modifier.size(SubmenuTheme.Dimensions.iconSize)
        ) {
            Icon(
                painter = if (circlesVisibility) AppIcons.Visibility() else AppIcons.VisibilityOff(),
                contentDescription = if (circlesVisibility) "Hide Circles" else "Show Circles",
                tint = if (circlesVisibility) SubmenuTheme.Colors.iconEnabled else SubmenuTheme.Colors.iconDisabled,
                modifier = Modifier.size(SubmenuTheme.Dimensions.iconInnerSize)
            )
        }
        
        // 2. Sectors opacity label
        Text(
            text = "Sectors",
            color = SubmenuTheme.Colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        
        // 3. Sectors opacity slider (0-50%)
        Slider(
            value = sectorsOpacity,
            onValueChange = { newOpacity ->
                coroutineScope.launch {
                    module.updateSectorsOpacity(newOpacity)
                }
            },
            valueRange = 0.0f..0.5f, // 0% to 50%
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = SubmenuTheme.Colors.primary,
                activeTrackColor = SubmenuTheme.Colors.primary,
                inactiveTrackColor = SubmenuTheme.Colors.iconDisabled
            )
        )
        
        // 4. Sectors opacity percentage display
        Text(
            text = "${(sectorsOpacity * 100).toInt()}%",
            color = SubmenuTheme.Colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(32.dp)
        )
    }
}
